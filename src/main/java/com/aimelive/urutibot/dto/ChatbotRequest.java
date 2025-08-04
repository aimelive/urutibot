package com.aimelive.urutibot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ChatbotRequest {
    @Schema(example = "john.doe@example.com")
    private String memoryId;

    @Schema(example = "Hello, How are you?")
    private String message;
}
