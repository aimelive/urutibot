package com.aimelive.urutibot.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_sessions", indexes = {
        @Index(name = "idx_chat_sessions_memory_id", columnList = "memory_id", unique = true),
        @Index(name = "idx_chat_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_chat_sessions_anonymous_visitor_id", columnList = "anonymous_visitor_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "memory_id", nullable = false, unique = true, length = 128)
    private String memoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "anonymous_visitor_id", length = 128)
    private String anonymousVisitorId;

    @Column(length = 255)
    private String title;

    @CreatedDate
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();
}
