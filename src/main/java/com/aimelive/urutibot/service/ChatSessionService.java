package com.aimelive.urutibot.service;

import com.aimelive.urutibot.config.CacheConfig;
import com.aimelive.urutibot.exception.HttpException;
import com.aimelive.urutibot.model.ChatSession;
import com.aimelive.urutibot.model.User;
import com.aimelive.urutibot.repository.ChatSessionRepository;
import com.aimelive.urutibot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionService {

    /** Cap returned to UI session listings — consistent with frontend pagination. */
    private static final int MAX_SESSIONS_PER_REQUEST = 100;
    private static final Pageable DEFAULT_SESSION_PAGE = PageRequest.of(
            0, MAX_SESSIONS_PER_REQUEST, Sort.by(Sort.Direction.DESC, "lastActivityAt"));

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;
    private final DurableChatMemoryGateway memoryGateway;

    /**
     * Locate the session for the given memoryId, creating it if necessary.
     * Validates that the requester owns the session — throws 403 otherwise.
     */
    @Transactional
    public ChatSession findOrCreateAndAuthorize(String memoryId, UUID userId, String anonymousVisitorId) {
        ChatSession session = chatSessionRepository.findByMemoryId(memoryId).orElse(null);

        if (session == null) {
            User user = userId != null
                    ? userRepository.getReferenceById(userId)  // proxy reference; no SELECT
                    : null;
            ChatSession fresh = ChatSession.builder()
                    .memoryId(memoryId)
                    .user(user)
                    .anonymousVisitorId(anonymousVisitorId)
                    .lastActivityAt(LocalDateTime.now())
                    .build();
            try {
                return chatSessionRepository.saveAndFlush(fresh);
            } catch (DataIntegrityViolationException race) {
                // Two concurrent first-turns for the same memoryId raced to insert.
                // The unique index `idx_chat_sessions_memory_id` rejected the loser
                // — re-load the winner and continue with normal ownership checks.
                log.debug("Race on chat_sessions insert for memoryId={}; reloading winner", memoryId);
                session = chatSessionRepository.findByMemoryId(memoryId)
                        .orElseThrow(() -> new HttpException(HttpStatus.CONFLICT,
                                "Chat session creation race could not be resolved"));
            }
        }

        // Existing session — verify ownership and auto-link if user has logged in.
        // The JWT filter already loaded (and cached) the principal, so we trust the
        // userId and skip the redundant existsById round-trip.
        if (userId != null) {
            if (session.getUser() == null) {
                session.setUser(userRepository.getReferenceById(userId));
            } else if (!session.getUser().getId().equals(userId)) {
                throw new HttpException(HttpStatus.FORBIDDEN, "This chat session belongs to another user");
            }
        } else {
            if (session.getUser() != null) {
                throw new HttpException(HttpStatus.UNAUTHORIZED,
                        "This chat session requires authentication");
            }
            if (anonymousVisitorId == null
                    || !anonymousVisitorId.equals(session.getAnonymousVisitorId())) {
                throw new HttpException(HttpStatus.FORBIDDEN,
                        "Anonymous chat session does not match the supplied visitor ID");
            }
        }

        // Managed entity — dirty-check writes lastActivityAt at commit time.
        session.setLastActivityAt(LocalDateTime.now());
        return session;
    }

    @Transactional(readOnly = true)
    public List<ChatSession> listSessionsForUser(UUID userId) {
        return chatSessionRepository
                .findByUserIdOrderByLastActivityAtDesc(userId, DEFAULT_SESSION_PAGE)
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<ChatSession> listSessionsForAnonymous(String anonymousVisitorId) {
        return chatSessionRepository
                .findByAnonymousVisitorIdOrderByLastActivityAtDesc(anonymousVisitorId, DEFAULT_SESSION_PAGE)
                .getContent();
    }

    /**
     * Delete a single session — used by the frontend when the user clicks
     * "New Chat" so the abandoned conversation doesn't linger in the DB.
     * Verifies ownership before deleting; throws 403 otherwise.
     * {@code chat_messages} are cascaded via the FK ({@code ON DELETE CASCADE}).
     * Silent no-op if the session doesn't exist.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CHAT_SESSION_USER, key = "#memoryId")
    public void deleteOwnedSession(String memoryId, UUID userId, String anonymousVisitorId) {
        chatSessionRepository.findByMemoryId(memoryId).ifPresent(session -> {
            if (session.getUser() != null) {
                if (userId == null || !session.getUser().getId().equals(userId)) {
                    throw new HttpException(HttpStatus.FORBIDDEN,
                            "Not allowed to delete this chat session");
                }
            } else if (anonymousVisitorId == null
                    || !anonymousVisitorId.equals(session.getAnonymousVisitorId())) {
                throw new HttpException(HttpStatus.FORBIDDEN,
                        "Not allowed to delete this chat session");
            }
            chatSessionRepository.delete(session);
            memoryGateway.evict(session.getId(), session.getMemoryId());
            log.debug("Deleted chat session memoryId={} userId={} visitorId={}",
                    memoryId, userId, anonymousVisitorId);
        });
    }

    /**
     * Bulk-delete every session belonging to the caller. Called on logout.
     * Replaces a load-N-then-delete-N round-trip pattern with a single SQL
     * statement per audience (auth + anonymous) — chat_messages cascade via FK.
     */
    /**
     * {@code allEntries=true} on the chatSessionUserId cache — bulk delete
     * doesn't surface individual memoryIds, and the cost of re-warming on
     * the next first-turn lookup is paid at most once per active session.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CHAT_SESSION_USER, allEntries = true)
    public int deleteAllForCaller(UUID userId, String anonymousVisitorId) {
        int total = 0;
        if (userId != null) {
            total += chatSessionRepository.deleteAllByUserId(userId);
        }
        if (anonymousVisitorId != null && !anonymousVisitorId.isBlank()) {
            total += chatSessionRepository.deleteAllByAnonymousVisitorId(anonymousVisitorId);
        }
        if (total > 0) {
            // Bulk delete didn't surface individual ids; clear the gateway caches
            // wholesale. The cost (re-warming on subsequent first-turn lookups)
            // is paid at most once per cleared session and only on the next use.
            memoryGateway.evictAll();
        }
        log.info("Deleted {} chat session(s) for caller userId={} visitorId={}",
                total, userId, anonymousVisitorId);
        return total;
    }

    @Transactional(readOnly = true)
    public ChatSession getOwnedSession(String memoryId, UUID userId, String anonymousVisitorId) {
        ChatSession session = chatSessionRepository.findByMemoryId(memoryId)
                .orElseThrow(() -> new HttpException(HttpStatus.NOT_FOUND, "Chat session not found"));

        if (session.getUser() != null) {
            if (userId == null || !session.getUser().getId().equals(userId)) {
                throw new HttpException(HttpStatus.FORBIDDEN, "Not allowed to access this chat session");
            }
        } else {
            if (anonymousVisitorId == null
                    || !anonymousVisitorId.equals(session.getAnonymousVisitorId())) {
                throw new HttpException(HttpStatus.FORBIDDEN, "Not allowed to access this chat session");
            }
        }
        return session;
    }
}
