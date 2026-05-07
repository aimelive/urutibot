package com.aimelive.urutibot.chatbot;

import com.aimelive.urutibot.chatbot.dto.ChatMessageResponse;
import com.aimelive.urutibot.chatbot.dto.ChatbotRequest;
import com.aimelive.urutibot.chatbot.memory.AnonChatMemoryRegistry;
import com.aimelive.urutibot.chatbot.memory.DurableChatMemoryGateway;
import com.aimelive.urutibot.chatbot.repository.ChatMessageRepository;
import com.aimelive.urutibot.shared.exception.HttpException;
import com.aimelive.urutibot.auth.security.AppUserPrincipal;
import com.aimelive.urutibot.auth.security.ChatAuthContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.TokenStream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Tag(name = "Chatbot", description = "API endpoints for chatbot")
@Slf4j
@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private static final long STREAM_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final ZoneId KIGALI = ZoneId.of("Africa/Kigali");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    private static final int MAX_HISTORY_PAGE_SIZE = 50;

    private final ChatbotService chatbotService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAuthContext chatAuthContext;
    private final DurableChatMemoryGateway memoryGateway;
    private final AnonChatMemoryRegistry anonMemoryRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(value = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatbotRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        boolean authenticated = principal != null;

        // Authenticated users key by their userId so the conversation is
        // unified across tabs/devices. Anonymous users key by the
        // tab-scoped UUID the frontend supplies.
        String memoryId;
        if (authenticated) {
            memoryId = principal.getId().toString();
            chatAuthContext.bind(memoryId, principal);
            log.debug("Chat stream begin: userId={} email={}",
                    principal.getId(), principal.getEmail());
        } else {
            memoryId = request.getMemoryId();
            if (memoryId == null || memoryId.isBlank()) {
                throw new HttpException(HttpStatus.BAD_REQUEST,
                        "memoryId is required for anonymous chats");
            }
            log.debug("Chat stream begin: memoryId={} (anonymous, ephemeral)", memoryId);
        }

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        String boundMemoryId = memoryId;
        Runnable releaseAuth = () -> chatAuthContext.release(boundMemoryId);

        emitter.onTimeout(() -> {
            closed.set(true);
            releaseAuth.run();
            emitter.complete();
        });
        emitter.onCompletion(() -> {
            closed.set(true);
            releaseAuth.run();
        });
        emitter.onError(t -> {
            closed.set(true);
            releaseAuth.run();
        });

        TokenStream tokenStream = chatbotService.answerStream(
                memoryId,
                request.getMessage(),
                LocalDate.now(KIGALI).format(DATE_FMT),
                buildAuthContext(principal));

        tokenStream
                .onPartialResponse(token -> {
                    if (closed.get())
                        return;
                    try {
                        String payload = objectMapper.writeValueAsString(token);
                        emitter.send(SseEmitter.event().name("token").data(payload));
                    } catch (IOException e) {
                        closed.set(true);
                        emitter.completeWithError(e);
                    }
                })
                .onCompleteResponse(resp -> {
                    if (authenticated) memoryGateway.commitTurn(boundMemoryId);
                    if (closed.get())
                        return;
                    try {
                        emitter.send(SseEmitter.event().name("done").data("{}"));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onError(throwable -> {
                    log.warn("Streaming chat error", throwable);
                    // Drop the partial turn before sending the error event so
                    // the user's next attempt starts from a clean window —
                    // half-recorded user/tool messages would otherwise poison
                    // the model's view of history.
                    if (authenticated) {
                        try { memoryGateway.rollbackTurn(boundMemoryId); }
                        catch (Exception cleanupErr) {
                            log.warn("Rollback of failed turn raised; continuing", cleanupErr);
                        }
                    } else {
                        anonMemoryRegistry.clear(boundMemoryId);
                    }
                    if (closed.get())
                        return;
                    try {
                        String payload = safeError(LlmErrorMapper.toClientMessage(throwable));
                        emitter.send(SseEmitter.event().name("error").data(payload));
                    } catch (IOException ignored) {
                    }
                    emitter.completeWithError(throwable);
                })
                .start();

        return emitter;
    }

    @Operation(summary = "Fetch the authenticated user's conversation history (paginated, oldest first)")
    @PreAuthorize("hasAuthority('CHAT_HISTORY_READ_OWN')")
    @GetMapping("/conversation")
    public ResponseEntity<Page<ChatMessageResponse>> getConversation(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PageableDefault(size = MAX_HISTORY_PAGE_SIZE, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        Pageable safe = capPageSize(pageable);
        Page<ChatMessageResponse> page = chatMessageRepository
                .findByUserIdOrderByIdAsc(principal.getId(), safe)
                .map(ChatMessageResponse::fromEntity);
        return ResponseEntity.ok(page);
    }

    @Operation(summary = "Wipe the authenticated user's conversation (used by 'New chat' and logout)")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/conversation")
    @Transactional
    public ResponseEntity<Map<String, Integer>> deleteConversation(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        UUID userId = principal.getId();
        int deleted = chatMessageRepository.deleteAllByUserId(userId);
        memoryGateway.evictUser(userId);
        chatAuthContext.release(userId.toString());
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    private Pageable capPageSize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_HISTORY_PAGE_SIZE)
            return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_HISTORY_PAGE_SIZE, pageable.getSort());
    }

    private String buildAuthContext(AppUserPrincipal principal) {
        if (principal == null) {
            return "Anonymous visitor (not signed in). Appointment tools will fail with requiresAuth.";
        }
        String roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.joining(", "));
        return String.format(
                "Authenticated as %s (email: %s, roles: [%s]). Appointment tools will succeed.",
                principal.getUser().getFullName(), principal.getEmail(),
                roles.isEmpty() ? "USER" : roles);
    }

    private String safeError(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("message", message));
        } catch (JsonProcessingException e) {
            return "{\"message\":\"error\"}";
        }
    }
}
