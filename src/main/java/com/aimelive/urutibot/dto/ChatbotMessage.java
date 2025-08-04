package com.aimelive.urutibot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatbotMessage {
    @Schema(example = "Hello, how can I help you?", description = "The content of the chatbot message")
    private String message;
    @Schema(example = "true", description = "Indicates if the message is from the user")
    private Boolean isUser;
}
