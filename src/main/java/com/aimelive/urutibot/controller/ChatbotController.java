package com.aimelive.urutibot.controller;

import dev.langchain4j.service.TokenStream;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aimelive.urutibot.service.ChatbotService;
import com.aimelive.urutibot.dto.ChatbotRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

//-----------------------------------------
@Tag(name = "Chatbot", description = "API endpoints for chatbot")
// -----------------------------------------
@Slf4j
@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {
    private static final long STREAM_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ChatbotService chatbotService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatbotRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            closed.set(true);
            emitter.complete();
        });
        emitter.onCompletion(() -> closed.set(true));
        emitter.onError(t -> closed.set(true));

        TokenStream tokenStream = chatbotService.answerStream(request.getMemoryId(), request.getMessage());

        tokenStream
                .onPartialResponse(token -> {
                    if (closed.get()) return;
                    try {
                        String payload = objectMapper.writeValueAsString(token);
                        emitter.send(SseEmitter.event().name("token").data(payload));
                    } catch (IOException e) {
                        closed.set(true);
                        emitter.completeWithError(e);
                    }
                })
                .onCompleteResponse(resp -> {
                    if (closed.get()) return;
                    try {
                        emitter.send(SseEmitter.event().name("done").data("{}"));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onError(throwable -> {
                    log.warn("Streaming chat error", throwable);
                    if (closed.get()) return;
                    try {
                        String payload = safeError("An error occurred while generating a response.");
                        emitter.send(SseEmitter.event().name("error").data(payload));
                    } catch (IOException ignored) {
                        // fall through to completeWithError
                    }
                    emitter.completeWithError(throwable);
                })
                .start();

        return emitter;
    }

    private String safeError(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("message", message));
        } catch (JsonProcessingException e) {
            return "{\"message\":\"error\"}";
        }
    }
}
