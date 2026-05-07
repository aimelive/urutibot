package com.aimelive.urutibot.repository;

import com.aimelive.urutibot.model.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findByMemoryId(String memoryId);

    /**
     * Lightweight projection — returns ONLY the session id without hydrating
     * the entity or its lazy {@code messages} collection. Used by the auth
     * resolver hot path which only needs to compare the owning user id.
     */
    @Query("SELECT s.id FROM ChatSession s WHERE s.memoryId = :memoryId")
    Optional<UUID> findIdByMemoryId(@Param("memoryId") String memoryId);

    @Query("SELECT s.user.id FROM ChatSession s WHERE s.memoryId = :memoryId")
    Optional<UUID> findUserIdByMemoryId(@Param("memoryId") String memoryId);

    /** Paged listing — back-pressures unbounded growth and supports cursor-style UI. */
    Page<ChatSession> findByUserIdOrderByLastActivityAtDesc(UUID userId, Pageable pageable);

    Page<ChatSession> findByAnonymousVisitorIdOrderByLastActivityAtDesc(
            String anonymousVisitorId, Pageable pageable);

    /**
     * Legacy unbounded variants — retained for backwards compatibility with
     * callers that explicitly want the full set (e.g. logout-time wipe). New
     * call sites should prefer the {@link Pageable} overloads.
     */
    List<ChatSession> findByUserIdOrderByLastActivityAtDesc(UUID userId);

    List<ChatSession> findByAnonymousVisitorIdOrderByLastActivityAtDesc(String anonymousVisitorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatSession s WHERE s.memoryId = :memoryId")
    int deleteByMemoryId(@Param("memoryId") String memoryId);

    /**
     * Direct {@code lastActivityAt} write that skips the dirty-check round-trip.
     * Used by the chat memory gateway on every turn after the first — no entity
     * hydration, no managed-state book-keeping, just a single indexed UPDATE.
     */
    @Modifying
    @Query("UPDATE ChatSession s SET s.lastActivityAt = :now WHERE s.id = :id")
    int touchLastActivityAt(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /**
     * Bulk delete for logout — replaces a load-N-then-delete-N round-trip
     * pattern with a single SQL statement. Cascades to {@code chat_messages}
     * via the {@code ON DELETE CASCADE} foreign key in V1.
     *
     * <p>{@code flushAutomatically} pushes any pending writes to the DB before
     * the bulk DELETE so they participate in the same transaction; {@code
     * clearAutomatically} drops the now-stale managed entities from the
     * persistence context to avoid phantom updates on flush.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatSession s WHERE s.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatSession s WHERE s.anonymousVisitorId = :visitorId")
    int deleteAllByAnonymousVisitorId(@Param("visitorId") String visitorId);
}
