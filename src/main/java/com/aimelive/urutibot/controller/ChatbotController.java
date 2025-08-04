package com.aimelive.urutibot.controller;

import com.aimelive.urutibot.dto.ChatbotMessage;
import com.aimelive.urutibot.dto.ChatbotResponse;
import com.aimelive.urutibot.repository.MessageRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.service.Result;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.aimelive.urutibot.service.ChatbotService;
import com.aimelive.urutibot.dto.ChatbotRequest;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;

//-----------------------------------------
@Tag(name = "Chatbot", description = "API endpoints for chatbot")
// -----------------------------------------
@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {
    private final ChatbotService chatbotService;
    private final MessageRepository messageRepository;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatbotResponse> chat(@RequestBody ChatbotRequest request) {
        Result<String> answer = chatbotService.answer(request.getMemoryId(), request.getMessage());
        return ResponseEntity.ok(new ChatbotResponse(request.getMemoryId(), answer.content()));
    }

    @SuppressWarnings("removal")
    @GetMapping("/messages/{memoryId}")
    public ResponseEntity<List<ChatbotMessage>> getMessages(
            @PathVariable @Schema(example = "john.doe@example.com") String memoryId) {
        List<ChatMessage> messages = messageRepository.findByMemoryId(memoryId)
                .map(message -> messagesFromJson(message.getMessages()))
                .orElse(List.of());

        return messageRepository.findByMemoryId(memoryId)
                .map(message -> ResponseEntity.ok(messages.stream()
                        .map(m -> new ChatbotMessage(m.text(), m.type().equals(ChatMessageType.USER))).toList()))
                .orElse(ResponseEntity.notFound().build());
    }
}
