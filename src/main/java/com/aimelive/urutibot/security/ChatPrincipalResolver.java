package com.aimelive.urutibot.security;

import com.aimelive.urutibot.config.CacheConfig;
import com.aimelive.urutibot.model.User;
import com.aimelive.urutibot.repository.ChatSessionRepository;
import com.aimelive.urutibot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the authenticated user behind a chat memoryId from inside
 * LangChain4j tool execution (where Spring SecurityContextHolder is empty).
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@link ChatAuthContext} — in-memory binding set on the HTTP request
 *       thread by the SSE controller; fastest, hot-path, zero DB hits.</li>
 *   <li>{@code chatSessionUserId} cache — memoryId → userId mapping, populated
 *       lazily on first resolve and evicted by {@code ChatSessionService} when
 *       the session is deleted. Replaces a {@code findUserIdByMemoryId} round
 *       trip on every tool call that doesn't have an in-context binding.</li>
 *   <li>{@code usersById} cache — userId → User entity (with roles +
 *       permissions) so subsequent tool turns skip the join.</li>
 * </ol>
 *
 * <p>Returns {@link Optional#empty()} when the chat is anonymous, so callers
 * can short-circuit with {@code requiresAuth:true}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatPrincipalResolver {

    private final ChatAuthContext authContext;
    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;

    /**
     * Self-injected proxy used to invoke @Cacheable methods on this bean from
     * within the same class — Spring's proxy-based cache aspect would not
     * trigger on plain {@code this.method()} calls.
     */
    @Autowired
    @Lazy
    private ChatPrincipalResolver self;

    public Optional<ChatAuthContext.AuthSnapshot> resolveSnapshot(String memoryId) {
        Optional<ChatAuthContext.AuthSnapshot> fromCtx = authContext.get(memoryId);
        if (fromCtx.isPresent()) return fromCtx;
        return resolveFromSession(memoryId);
    }

    /**
     * Returns a fully-initialised {@link User} entity (with roles + permissions
     * eagerly fetched via {@code @EntityGraph}) so callers in LangChain4j tool
     * execution can read fields like {@code fullName} and authority sets after
     * this method returns, regardless of session state.
     *
     * <p>When the in-memory binding is present we already know the user id
     * (from the controller's authorize step) and skip the
     * {@code findUserIdByMemoryId} pre-flight entirely. Both lookups go
     * through the {@code usersById} / {@code chatSessionUserId} caches.
     */
    public Optional<User> resolveUser(String memoryId) {
        Optional<UUID> fromCtx = authContext.get(memoryId)
                .map(ChatAuthContext.AuthSnapshot::userId);
        if (fromCtx.isPresent()) {
            return self.findUserById(fromCtx.get());
        }
        return self.findUserIdByMemoryId(memoryId)
                .flatMap(self::findUserById);
    }

    protected Optional<ChatAuthContext.AuthSnapshot> resolveFromSession(String memoryId) {
        return self.findUserIdByMemoryId(memoryId)
                .flatMap(self::findUserById)
                .map(u -> new ChatAuthContext.AuthSnapshot(
                        u.getId(),
                        u.getEmail(),
                        u.getFullName(),
                        u.getRoles().stream()
                                .flatMap(r -> java.util.stream.Stream.concat(
                                        java.util.stream.Stream.of("ROLE_" + r.getName().name()),
                                        r.getPermissions().stream().map(p -> p.getName())))
                                .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                        java.time.Instant.now()
                ));
    }

    /**
     * Cached: memoryId → owning userId. The mapping is immutable for the
     * lifetime of a session (sessions are never re-assigned), so a 5-minute
     * TTL is safe; eviction on session delete is wired in
     * {@code ChatSessionService}.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CHAT_SESSION_USER, key = "#memoryId", unless = "#result == null")
    public Optional<UUID> findUserIdByMemoryId(String memoryId) {
        return chatSessionRepository.findUserIdByMemoryId(memoryId);
    }

    /**
     * Cached: userId → User with roles + permissions eagerly fetched. Evicted
     * by {@code AuthService} on user mutation (currently registration only).
     *
     * <p>{@code unless} compares against {@code null} (not {@code Optional.isEmpty()})
     * because Spring's cache aspect auto-unwraps {@link Optional} return values
     * — within the SpEL expression, {@code #result} is the unwrapped {@link User}
     * (or {@code null} for an empty Optional), so {@code .isEmpty()} would
     * resolve against {@link User} and throw.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.USERS_BY_ID, key = "#userId", unless = "#result == null")
    public Optional<User> findUserById(UUID userId) {
        return userRepository.findWithAuthoritiesById(userId);
    }
}
