package com.aimelive.urutibot.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;

/**
 * Append-only chat memory backed by the {@code chat_messages} table.
 *
 * <p>Each {@link #add(ChatMessage)} call performs one INSERT, in order, with
 * no diffing or deletion — the durable row history is never truncated by the
 * model layer. {@link #messages()} returns only the last {@code promptWindow}
 * rows so the prompt context stays bounded.
 *
 * <p>This is a per-{@code memoryId} POJO created by the
 * {@code ChatMemoryProvider}; the {@link DurableChatMemoryGateway} bean
 * carries the actual transactional JPA work.
 */
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
