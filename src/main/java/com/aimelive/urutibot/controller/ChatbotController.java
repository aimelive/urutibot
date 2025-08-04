package com.aimelive.urutibot.controller;

import com.aimelive.urutibot.dto.ChatbotResponse;
import dev.langchain4j.service.Result;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.aimelive.urutibot.service.ChatbotService;
import com.aimelive.urutibot.dto.ChatbotRequest;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

//-----------------------------------------
@Tag(name = "Chatbot", description = "API endpoints for chatbot")
// -----------------------------------------
@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {
    private final ChatbotService chatbotService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatbotResponse> chat(@RequestBody ChatbotRequest request) {
        Result<String> answer = chatbotService.answer(request.getMemoryId(), request.getMessage());
        return ResponseEntity.ok(new ChatbotResponse(request.getMemoryId(), answer.content()));
    }
}
