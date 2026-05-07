package com.aimelive.urutibot.repository;

import com.aimelive.urutibot.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderBySequenceAsc(UUID sessionId);

    /**
     * Paged history fetch — used by the REST history endpoint to avoid
     * pulling potentially thousands of rows into memory for long sessions.
     */
    List<ChatMessage> findBySessionIdOrderBySequenceAsc(UUID sessionId, Pageable pageable);

    /**
     * Page variant returning total count alongside the slice — used by the
     * REST history endpoint so the client can render pagination controls
     * without a second round-trip.
     */
    Page<ChatMessage> findPageBySessionId(UUID sessionId, Pageable pageable);

    /**
     * Tail window for the LangChain4j memory store — only the most recent
     * N messages are needed to reconstruct the rolling window, so we avoid
     * loading the full session history every turn.
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId ORDER BY m.sequence DESC")
    List<ChatMessage> findTopBySessionIdOrderedDesc(@Param("sessionId") UUID sessionId, Pageable pageable);

    /** Highest sequence (for append-only writes); -1 when the session has no rows yet. */
    @Query("SELECT COALESCE(MAX(m.sequence), -1) FROM ChatMessage m WHERE m.session.id = :sessionId")
    int findMaxSequenceBySessionId(@Param("sessionId") UUID sessionId);
}
