package com.aimelive.urutibot.controller;

import com.aimelive.urutibot.dto.ChatMessageResponse;
import com.aimelive.urutibot.dto.ChatSessionResponse;
import com.aimelive.urutibot.dto.ChatbotRequest;
import com.aimelive.urutibot.exception.HttpException;
import com.aimelive.urutibot.model.ChatSession;
import com.aimelive.urutibot.repository.ChatMessageRepository;
import com.aimelive.urutibot.security.AppUserPrincipal;
import com.aimelive.urutibot.security.ChatAuthContext;
import com.aimelive.urutibot.service.ChatSessionService;
import com.aimelive.urutibot.service.ChatbotService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");

    private final ChatbotService chatbotService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAuthContext chatAuthContext;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatbotRequest request,
                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        UUID userId = principal != null ? principal.getId() : null;
        String memoryId = request.getMemoryId();

        chatSessionService.findOrCreateAndAuthorize(
                memoryId, userId, request.getAnonymousVisitorId());


        if (principal != null) {
            chatAuthContext.bind(memoryId, principal);
            log.debug("Chat stream begin: memoryId={} userId={} email={}",
                    memoryId, principal.getId(), principal.getEmail());
        } else {
            log.debug("Chat stream begin: memoryId={} (anonymous)", memoryId);
        }

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        Runnable releaseAuth = () -> chatAuthContext.release(memoryId);

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
                    if (closed.get()) return;
                    try {
                        String payload = objectMapper.writeValueAsString(token);
                        emitter.send(SseEmitter.event().name("token").data(payload));
                    } catch (IOException e) {
                        closed.set(true);
                        emitter.completeWithError(e);
                    }
                })
                .onCompleteResponse(resp -> {
                    if (closed.get()) return;
                    try {
                        emitter.send(SseEmitter.event().name("done").data("{}"));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onError(throwable -> {
                    log.warn("Streaming chat error", throwable);
                    if (closed.get()) return;
                    try {
                        String payload = safeError("An error occurred while generating a response.");
                        emitter.send(SseEmitter.event().name("error").data(payload));
                    } catch (IOException ignored) {
                    }
                    emitter.completeWithError(throwable);
                })
                .start();

        return emitter;
    }

    @Operation(summary = "List the authenticated user's past chat sessions")
    @PreAuthorize("hasAuthority('CHAT_SESSION_READ_OWN')")
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponse>> listMySessions(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(chatSessionService.listSessionsForUser(principal.getId()).stream()
                .map(ChatSessionResponse::fromEntity)
                .toList());
    }

    @Operation(summary = "List anonymous chat sessions for a given visitor ID (cookie/localStorage value)")
    @GetMapping("/sessions/anonymous")
    public ResponseEntity<List<ChatSessionResponse>> listAnonymousSessions(
            @RequestParam("visitorId") String anonymousVisitorId) {
        if (anonymousVisitorId == null || anonymousVisitorId.isBlank()) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "visitorId is required");
        }
        return ResponseEntity.ok(chatSessionService.listSessionsForAnonymous(anonymousVisitorId).stream()
                .map(ChatSessionResponse::fromEntity)
                .toList());
    }

    /**
     * Hard cap on a single page — defends against an unbounded fetch on long
     * conversations where {@code chat_messages} can hold thousands of TEXT /
     * JSONB rows. Clients that need everything paginate explicitly.
     */
    private static final int MAX_HISTORY_PAGE_SIZE = 200;

    @Operation(summary = "Fetch the message history for one of your sessions (paginated, ascending sequence)")
    @GetMapping("/sessions/{memoryId}/messages")
    public ResponseEntity<Page<ChatMessageResponse>> getSessionMessages(
            @PathVariable String memoryId,
            @RequestParam(value = "visitorId", required = false) String anonymousVisitorId,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PageableDefault(size = 100, sort = "sequence", direction = Sort.Direction.ASC) Pageable pageable) {
        UUID userId = principal != null ? principal.getId() : null;
        ChatSession session = chatSessionService.getOwnedSession(memoryId, userId, anonymousVisitorId);

        Pageable safe = capPageSize(pageable);
        Page<ChatMessageResponse> page = chatMessageRepository
                .findPageBySessionId(session.getId(), safe)
                .map(ChatMessageResponse::fromEntity);
        return ResponseEntity.ok(page);
    }

    private Pageable capPageSize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_HISTORY_PAGE_SIZE) return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_HISTORY_PAGE_SIZE, pageable.getSort());
    }

    @Operation(summary = "Delete one of your chat sessions (used by 'New Chat' to drop the abandoned thread)")
    @DeleteMapping("/sessions/{memoryId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String memoryId,
            @RequestParam(value = "visitorId", required = false) String anonymousVisitorId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        UUID userId = principal != null ? principal.getId() : null;
        chatSessionService.deleteOwnedSession(memoryId, userId, anonymousVisitorId);
        chatAuthContext.release(memoryId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete every chat session belonging to the caller (called on logout)")
    @DeleteMapping("/sessions/mine")
    public ResponseEntity<Map<String, Integer>> deleteAllMine(
            @RequestParam(value = "visitorId", required = false) String anonymousVisitorId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        UUID userId = principal != null ? principal.getId() : null;
        if (userId == null && (anonymousVisitorId == null || anonymousVisitorId.isBlank())) {
            throw new HttpException(HttpStatus.BAD_REQUEST,
                    "Either an authenticated user or a visitorId is required");
        }
        int deleted = chatSessionService.deleteAllForCaller(userId, anonymousVisitorId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    /**
     * Renders the current user's identity as a single line for the model's
     * system prompt. Lets the LLM make routing decisions (skip the "please
     * sign in" prompt for an authenticated user, address them by name, etc.)
     * without an extra tool round-trip.
     */
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
