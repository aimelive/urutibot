package com.aimelive.urutibot.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatbotRequest {

    @Schema(description = "Tab-scoped session identifier - only required for anonymous visitors, " +
            "where it scopes the in-memory chat window for that browser tab and is discarded on " +
            "page refresh. Ignored for authenticated requests; the server keys persistence by " +
            "the user id from the JWT.", example = "8e1a4a55-3c4e-4f64-9a6c-7d3eb6c1d8ee")
    @Size(max = 128)
    private String memoryId;

    @Schema(example = "Hello, How are you?")
    @NotBlank(message = "Message is required")
    private String message;
}
