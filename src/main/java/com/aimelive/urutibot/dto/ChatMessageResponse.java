package com.aimelive.urutibot.dto;

import com.aimelive.urutibot.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private Integer sequence;
    private ChatMessage.MessageRole role;
    private String content;
    private LocalDateTime createdAt;

    // Stored user messages include the LangChain4j @UserMessage wrapper
    // (<userMessage>…</userMessage>) and any RAG retriever context the
    // augmentor appends ("Answer using the following information: …").
    // Both are necessary for LLM memory but should never be shown back
    // to the user when restoring chat history.
    private static final Pattern USER_MSG_PATTERN =
            Pattern.compile("<userMessage>(.*?)</userMessage>", Pattern.DOTALL);
    private static final String RAG_MARKER = "Answer using the following information:";

    public static ChatMessageResponse fromEntity(ChatMessage m) {
        String content = m.getContent();
        if (m.getRole() == ChatMessage.MessageRole.USER) {
            content = sanitizeUserContent(content);
        }
        return ChatMessageResponse.builder()
                .id(m.getId())
                .sequence(m.getSequence())
                .role(m.getRole())
                .content(content)
                .createdAt(m.getCreatedAt())
                .build();
    }

    private static String sanitizeUserContent(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        Matcher m = USER_MSG_PATTERN.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        int idx = raw.indexOf(RAG_MARKER);
        if (idx >= 0) {
            return raw.substring(0, idx).trim();
        }
        return raw;
    }
}
