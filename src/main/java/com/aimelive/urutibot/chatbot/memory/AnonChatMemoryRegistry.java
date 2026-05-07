package com.aimelive.urutibot.chatbot.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AnonChatMemoryRegistry {

    private final Map<String, ChatMemory> memories = new ConcurrentHashMap<>();

    public ChatMemory getOrCreate(String memoryId, int maxMessages) {
        return memories.computeIfAbsent(memoryId, id ->
                MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(maxMessages)
                        .build());
    }

    public void clear(String memoryId) {
        ChatMemory mem = memories.get(memoryId);
        if (mem != null) mem.clear();
    }
}
