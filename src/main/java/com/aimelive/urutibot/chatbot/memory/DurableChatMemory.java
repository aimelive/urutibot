package com.aimelive.urutibot.chatbot.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;


public class DurableChatMemory implements ChatMemory {

    private final String memoryId;
    private final int promptWindow;
    private final DurableChatMemoryGateway gateway;

    public DurableChatMemory(String memoryId, int promptWindow, DurableChatMemoryGateway gateway) {
        this.memoryId = memoryId;
        this.promptWindow = promptWindow;
        this.gateway = gateway;
    }

    @Override
    public Object id() {
        return memoryId;
    }

    @Override
    public void add(ChatMessage message) {
        gateway.append(memoryId, message);
    }

    @Override
    public List<ChatMessage> messages() {
        return gateway.tail(memoryId, promptWindow);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException(
                "Durable chat history is not cleared via ChatMemory; delete the session instead.");
    }
}
