package com.aimelive.urutibot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

@Data
public class ChatbotRequest {
    @Schema(example = "john.doe@example.com")
    @NotBlank(message = "Memory ID is required")
    @Email(message = "Invalid email address")
    private String memoryId;

    @Schema(example = "Hello, How are you?")
    @NotBlank(message = "Message is required")
    private String message;
}
