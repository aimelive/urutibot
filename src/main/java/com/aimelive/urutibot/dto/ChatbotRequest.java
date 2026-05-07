package com.aimelive.urutibot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatbotRequest {

    @Schema(description = "Stable session identifier. For anonymous visitors, the frontend " +
            "generates a UUID and persists it in localStorage. For authenticated users, the same " +
            "id is reused so the conversation continues seamlessly.",
            example = "8e1a4a55-3c4e-4f64-9a6c-7d3eb6c1d8ee")
    @NotBlank(message = "Memory ID is required")
    @Size(max = 128)
    private String memoryId;

    @Schema(description = "The anonymous visitor ID from the browser's localStorage. Required " +
            "for anonymous chats; optional for authenticated requests.")
    @Size(max = 128)
    private String anonymousVisitorId;

    @Schema(example = "Hello, How are you?")
    @NotBlank(message = "Message is required")
    private String message;
}
