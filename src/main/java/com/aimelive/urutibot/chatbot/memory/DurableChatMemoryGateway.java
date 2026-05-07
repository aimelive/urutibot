package com.aimelive.urutibot.chatbot.memory;

import com.aimelive.urutibot.chatbot.repository.ChatMessageRepository;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * Persists the authenticated user's single conversation. The {@code memoryId}
 * passed in by LangChain4j is the user's id as a string; this gateway parses
 * it once and keys all storage by {@link UUID} thereafter.
 *
 * <p>
 * Row ordering is the chat_messages PK ({@code BIGSERIAL id}) - assigned
 * by Postgres in insertion order, monotonic per user, no client-side counter
 * to maintain. The rollback boundary for each turn is derived from the id of
 * the just-saved user message ({@code id - 1}); Hibernate populates that id
 * synchronously via the sequence generator, so we never need a separate
 * {@code MAX(id)} round trip.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DurableChatMemoryGateway {

    private final ChatMessageRepository chatMessageRepository;

    private record TurnState(UUID userId, long startMaxId) {
    }

    private final Map<String, TurnState> turnStateByMemoryId = new ConcurrentHashMap<>();

    @Transactional
    public void append(String memoryId, ChatMessage message) {
        UUID userId = parseUserId(memoryId);
        if (userId == null)
            return;

        com.aimelive.urutibot.chatbot.model.ChatMessage saved = insertMessage(userId, message);

        if (message instanceof UserMessage) {
            // Hibernate's sequence generator populates id synchronously on
            // save(), so the just-saved row is the first of this turn. The
            // rollback boundary is therefore (id - 1) - anything strictly
            // greater belongs to the in-flight turn and must be dropped on
            // failure.
            turnStateByMemoryId.put(memoryId, new TurnState(userId, saved.getId() - 1));
        }
    }

    private com.aimelive.urutibot.chatbot.model.ChatMessage insertMessage(UUID userId, ChatMessage message) {
        return chatMessageRepository.save(com.aimelive.urutibot.chatbot.model.ChatMessage.builder()
                .userId(userId)
                .role(mapRole(message))
                .content(extractText(message))
                .payloadJson(messagesToJson(List.of(message)))
                .build());
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> tail(String memoryId, int limit) {
        UUID userId = parseUserId(memoryId);
        if (userId == null)
            return Collections.emptyList();

        List<com.aimelive.urutibot.chatbot.model.ChatMessage> rowsDesc = chatMessageRepository
                .findTopByUserIdOrderedDesc(userId, PageRequest.of(0, limit));
        if (rowsDesc.isEmpty())
            return Collections.emptyList();

        Collections.reverse(rowsDesc);

        List<ChatMessage> out = new ArrayList<>(rowsDesc.size());
        for (var row : rowsDesc) {
            if (row.getPayloadJson() != null) {
                messagesFromJson(row.getPayloadJson())
                        .forEach(m -> out.add(normalize(m)));
            }
        }
        return out;
    }

    /**
     * Mark the in-flight turn as successful - drop the rollback bookkeeping.
     * No DB work; the messages are already persisted via the normal append
     * flow.
     */
    public void commitTurn(String memoryId) {
        turnStateByMemoryId.remove(memoryId);
    }

    /**
     * Drop every {@code chat_messages} row the failed turn inserted. With
     * the one-conversation-per-user model there is no session row to clean
     * up - the user's history simply contracts back to the prior turn.
     */
    @Transactional
    public void rollbackTurn(String memoryId) {
        TurnState state = turnStateByMemoryId.remove(memoryId);
        if (state == null)
            return;

        int deleted = chatMessageRepository
                .deleteByUserIdAndIdGreaterThan(state.userId(), state.startMaxId());
        log.info("Rolled back {} message(s) after failed turn memoryId={}", deleted, memoryId);
    }

    /**
     * No-op hook retained for callers that previously had to evict a
     * per-user counter cache. Kept so existing wiring continues to compile;
     * the stateless design no longer needs eviction.
     */
    public void evictUser(UUID userId) {
        // intentionally empty
    }

    private static UUID parseUserId(String memoryId) {
        if (memoryId == null)
            return null;
        try {
            return UUID.fromString(memoryId);
        } catch (IllegalArgumentException e) {
            // Anonymous memoryIds are also UUIDs in practice, but they go
            // through MessageWindowChatMemory and never reach this gateway.
            // A non-UUID memoryId here is a programming error.
            log.warn("Durable gateway received non-UUID memoryId={}; skipping", memoryId);
            return null;
        }
    }

    private ChatMessage normalize(ChatMessage msg) {
        if (!(msg instanceof AiMessage am))
            return msg;
        List<ToolExecutionRequest> reqs = am.toolExecutionRequests();
        if (reqs == null || reqs.isEmpty())
            return msg;

        boolean changed = false;
        List<ToolExecutionRequest> fixed = new ArrayList<>(reqs.size());
        for (ToolExecutionRequest req : reqs) {
            String args = req.arguments();
            if (args == null || args.isBlank() || !looksLikeJsonObject(args)) {
                fixed.add(ToolExecutionRequest.builder()
                        .id(req.id())
                        .name(req.name())
                        .arguments("{}")
                        .build());
                changed = true;
            } else {
                fixed.add(req);
            }
        }
        if (!changed)
            return msg;
        String text = am.text();
        if (text != null && !text.isEmpty()) {
            return AiMessage.from(text, fixed);
        }
        return AiMessage.from(fixed);
    }

    private boolean looksLikeJsonObject(String s) {
        String t = s.trim();
        return t.startsWith("{") && t.endsWith("}");
    }

    private com.aimelive.urutibot.chatbot.model.ChatMessage.MessageRole mapRole(ChatMessage msg) {
        if (msg instanceof SystemMessage)
            return com.aimelive.urutibot.chatbot.model.ChatMessage.MessageRole.SYSTEM;
        if (msg instanceof UserMessage)
            return com.aimelive.urutibot.chatbot.model.ChatMessage.MessageRole.USER;
        if (msg instanceof AiMessage)
            return com.aimelive.urutibot.chatbot.model.ChatMessage.MessageRole.ASSISTANT;
        if (msg instanceof ToolExecutionResultMessage)
            return com.aimelive.urutibot.chatbot.model.ChatMessage.MessageRole.TOOL;
        return com.aimelive.urutibot.chatbot.model.ChatMessage.MessageRole.SYSTEM;
    }

    private String extractText(ChatMessage msg) {
        if (msg instanceof UserMessage um)
            return um.singleText();
        if (msg instanceof AiMessage am)
            return am.text();
        if (msg instanceof SystemMessage sm)
            return sm.text();
        if (msg instanceof ToolExecutionResultMessage tr)
            return tr.text();
        return null;
    }
}
