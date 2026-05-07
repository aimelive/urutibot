package com.aimelive.urutibot.service;

import com.aimelive.urutibot.model.ChatSession;
import com.aimelive.urutibot.repository.ChatMessageRepository;
import com.aimelive.urutibot.repository.ChatSessionRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * Spring-managed gateway carrying the transactional JPA work for
 * {@link DurableChatMemory}. The memory itself is instantiated per
 * {@code memoryId} by {@code ChatMemoryProvider} and lives outside the
 * Spring container, so its transactional methods would not be proxied —
 * keeping the persistence on a {@code @Component} avoids that pitfall.
 *
 * <p>Performance notes — three in-memory caches collapse what was 6-10 DB
 * round-trips per turn down to 2-4:
 * <ul>
 *   <li>{@code sessionIdByMemoryId} — eliminates {@code findIdByMemoryId} on
 *       every internal append and on {@code tail()} reads.</li>
 *   <li>{@code nextSequenceBySessionId} — eliminates {@code SELECT MAX(sequence)}
 *       on every insert; sequence is increment-and-get in memory.</li>
 *   <li>{@code titledSessions} — once a session has a title, we skip the
 *       full {@code ChatSession} entity hydration in {@code appendUserTurn}
 *       and bump {@code last_activity_at} via a single bulk UPDATE.</li>
 * </ul>
 *
 * <p>All caches are evicted via {@link #evict(UUID, String)} on session
 * delete; the {@code AtomicInteger} sequence counter is safe under
 * single-writer-per-session (one in-flight LLM turn per memoryId), and
 * over-incrementing on a constraint-violation rollback is harmless — the
 * next insert simply skips a sequence number, which is permitted by the
 * monotonic {@code (session_id, sequence)} unique index.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DurableChatMemoryGateway {

    private static final int TITLE_MAX_LEN = 80;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    private final Map<String, UUID> sessionIdByMemoryId = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> nextSequenceBySessionId = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> titledSessions = new ConcurrentHashMap<>();

    /**
     * Append one chat row.
     *
     * <p>LangChain4j calls {@code add()} 2-4× per turn (User → AI → ToolResult →
     * AI). Only the {@link UserMessage} turn touches {@code title} /
     * {@code lastActivityAt}; the others go through the lightweight internal
     * path that uses cached session ids and sequence counters.
     */
    @Transactional
    public void append(String memoryId, ChatMessage message) {
        if (message instanceof UserMessage um) {
            appendUserTurn(memoryId, um);
        } else {
            appendInternalMessage(memoryId, message);
        }
    }

    private void appendUserTurn(String memoryId, UserMessage message) {
        UUID cachedId = sessionIdByMemoryId.get(memoryId);
        boolean knownTitled = cachedId != null && Boolean.TRUE.equals(titledSessions.get(cachedId));

        if (knownTitled) {
            // Hot path — skip entity hydration + dirty-check; bump activity in one UPDATE.
            chatSessionRepository.touchLastActivityAt(cachedId, LocalDateTime.now());
            ChatSession ref = chatSessionRepository.getReferenceById(cachedId);
            insertNextMessage(ref, message);
            return;
        }

        ChatSession session = chatSessionRepository.findByMemoryId(memoryId).orElse(null);
        if (session == null) {
            log.warn("append called for unknown memoryId={}; skipping (session must be created first)",
                    memoryId);
            return;
        }
        sessionIdByMemoryId.put(memoryId, session.getId());

        if (session.getTitle() == null) {
            String t = message.singleText();
            if (t != null && !t.isBlank()) {
                session.setTitle(t.length() > TITLE_MAX_LEN ? t.substring(0, TITLE_MAX_LEN) : t);
            }
        }
        // Managed entity — dirty check writes lastActivityAt at commit time.
        session.setLastActivityAt(LocalDateTime.now());
        if (session.getTitle() != null) {
            titledSessions.put(session.getId(), Boolean.TRUE);
        }
        insertNextMessage(session, message);
    }

    private void appendInternalMessage(String memoryId, ChatMessage message) {
        UUID sessionId = resolveSessionId(memoryId);
        if (sessionId == null) {
            log.warn("append called for unknown memoryId={}; skipping (session must be created first)",
                    memoryId);
            return;
        }
        // Reference proxy avoids loading the ChatSession row purely to satisfy
        // the FK on the new chat_messages row.
        ChatSession ref = chatSessionRepository.getReferenceById(sessionId);
        insertNextMessage(ref, message);
    }

    /**
     * Resolve session id for a memoryId, hitting the DB only on cache miss.
     * The mapping is immutable for the lifetime of a session, so cached
     * entries are valid until the session is deleted (see {@link #evict}).
     */
    private UUID resolveSessionId(String memoryId) {
        UUID cached = sessionIdByMemoryId.get(memoryId);
        if (cached != null) return cached;
        UUID resolved = chatSessionRepository.findIdByMemoryId(memoryId).orElse(null);
        if (resolved != null) {
            sessionIdByMemoryId.put(memoryId, resolved);
        }
        return resolved;
    }

    /**
     * Compute the next sequence and insert. The {@code uq_chat_messages_session_sequence}
     * unique index protects against duplicate sequences from concurrent appends —
     * collisions are exceedingly rare in practice (one user, one in-flight LLM
     * turn) and bubble up as a {@code DataIntegrityViolationException}. Per-row
     * retry would need {@code REQUIRES_NEW} since Hibernate marks the current
     * TX rollback-only on the violation; not worth the cost for the realistic
     * concurrency model.
     */
    private void insertNextMessage(ChatSession sessionRef, ChatMessage message) {
        int nextSeq = nextSequence(sessionRef.getId());
        chatMessageRepository.save(com.aimelive.urutibot.model.ChatMessage.builder()
                .session(sessionRef)
                .sequence(nextSeq)
                .role(mapRole(message))
                .content(extractText(message))
                .payloadJson(messagesToJson(List.of(message)))
                .build());
    }

    private int nextSequence(UUID sessionId) {
        AtomicInteger counter = nextSequenceBySessionId.computeIfAbsent(sessionId,
                id -> new AtomicInteger(chatMessageRepository.findMaxSequenceBySessionId(id)));
        return counter.incrementAndGet();
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> tail(String memoryId, int limit) {
        UUID sessionId = resolveSessionId(memoryId);
        if (sessionId == null) return Collections.emptyList();

        // ORDER BY is in the @Query itself; Pageable's Sort would be ignored,
        // so don't bother building one.
        List<com.aimelive.urutibot.model.ChatMessage> rowsDesc =
                chatMessageRepository.findTopBySessionIdOrderedDesc(
                        sessionId, PageRequest.of(0, limit));
        if (rowsDesc.isEmpty()) return Collections.emptyList();

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
     * Drop in-memory state for a deleted session — called by
     * {@code ChatSessionService} on single-session and bulk deletes so the
     * caches do not pin stale ids.
     */
    public void evict(UUID sessionId, String memoryId) {
        if (memoryId != null) sessionIdByMemoryId.remove(memoryId);
        if (sessionId != null) {
            nextSequenceBySessionId.remove(sessionId);
            titledSessions.remove(sessionId);
        }
    }

    /** Drop all in-memory state — used by bulk-delete paths where individual ids are not enumerated. */
    public void evictAll() {
        sessionIdByMemoryId.clear();
        nextSequenceBySessionId.clear();
        titledSessions.clear();
    }

    /**
     * Heals tool-call AiMessages whose {@code arguments} are empty / blank /
     * non-JSON. langchain4j-anthropic 1.0.0-beta1 passes the raw arguments
     * string straight to Jackson when building the follow-up request, so an
     * empty value crashes the entire turn with "No content to map due to
     * end-of-input". Applied on read (not write) so the on-disk JSON keeps
     * matching what the provider actually returned.
     */
    private ChatMessage normalize(ChatMessage msg) {
        if (!(msg instanceof AiMessage am)) return msg;
        List<ToolExecutionRequest> reqs = am.toolExecutionRequests();
        if (reqs == null || reqs.isEmpty()) return msg;

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
        if (!changed) return msg;
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

    private com.aimelive.urutibot.model.ChatMessage.MessageRole mapRole(ChatMessage msg) {
        if (msg instanceof SystemMessage) return com.aimelive.urutibot.model.ChatMessage.MessageRole.SYSTEM;
        if (msg instanceof UserMessage) return com.aimelive.urutibot.model.ChatMessage.MessageRole.USER;
        if (msg instanceof AiMessage) return com.aimelive.urutibot.model.ChatMessage.MessageRole.ASSISTANT;
        if (msg instanceof ToolExecutionResultMessage) return com.aimelive.urutibot.model.ChatMessage.MessageRole.TOOL;
        return com.aimelive.urutibot.model.ChatMessage.MessageRole.SYSTEM;
    }

    private String extractText(ChatMessage msg) {
        if (msg instanceof UserMessage um) return um.singleText();
        if (msg instanceof AiMessage am) return am.text();
        if (msg instanceof SystemMessage sm) return sm.text();
        if (msg instanceof ToolExecutionResultMessage tr) return tr.text();
        return null;
    }
}
