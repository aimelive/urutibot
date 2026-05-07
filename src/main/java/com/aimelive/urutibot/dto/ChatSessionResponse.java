package com.aimelive.urutibot.dto;

import com.aimelive.urutibot.model.ChatSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionResponse {
    private UUID id;
    private String memoryId;
    private String title;
    private LocalDateTime startedAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime endedAt;

    public static ChatSessionResponse fromEntity(ChatSession s) {
        return ChatSessionResponse.builder()
                .id(s.getId())
                .memoryId(s.getMemoryId())
                .title(s.getTitle())
                .startedAt(s.getStartedAt())
                .lastActivityAt(s.getLastActivityAt())
                .endedAt(s.getEndedAt())
                .build();
    }
}
