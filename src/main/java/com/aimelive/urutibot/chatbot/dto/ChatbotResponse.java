package com.aimelive.urutibot.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatbotResponse {
    private String memoryId;
    private String responseMessage;
}
