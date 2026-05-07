package com.aimelive.urutibot.chatbot.repository;

import com.aimelive.urutibot.chatbot.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Paginated history for the GET /conversation endpoint, oldest first. */
    Page<ChatMessage> findByUserIdOrderByIdAsc(UUID userId, Pageable pageable);

    /** Most-recent N messages for the LangChain memory window. */
    @Query("SELECT m FROM ChatMessage m WHERE m.userId = :userId ORDER BY m.id DESC")
    List<ChatMessage> findTopByUserIdOrderedDesc(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Roll back a failed turn - drops every message inserted after the
     * recorded turn-start id snapshot. {@code clearAutomatically} discards
     * stale managed entities so a subsequent {@code messages()} call sees
     * the post-rollback state.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatMessage m WHERE m.userId = :userId AND m.id > :id")
    int deleteByUserIdAndIdGreaterThan(
            @Param("userId") UUID userId,
            @Param("id") long id);

    /** Wipes the user's entire conversation - used by "New chat" and logout. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatMessage m WHERE m.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
