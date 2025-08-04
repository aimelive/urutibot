package com.aimelive.urutibot.service;

import com.aimelive.urutibot.model.ChatMemory;
import com.aimelive.urutibot.repository.ChatMemoryRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomMemoryStore implements ChatMemoryStore {
    private final ChatMemoryRepository chatMemoryRepository;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return messagesFromJson(chatMemoryRepository.findByMemoryId(memoryId.toString())
                .map(ChatMemory::getMessages)
                .orElse("[]"));
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        Optional<ChatMemory> message = chatMemoryRepository.findByMemoryId(memoryId.toString());
        if (message.isEmpty()) {
            chatMemoryRepository.save(new ChatMemory(memoryId.toString(), messagesToJson(list)));
        } else {
            message.ifPresent(m -> {
                m.setMessages(messagesToJson(list));
                chatMemoryRepository.save(m);
            });
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        chatMemoryRepository.deleteByMemoryId(memoryId.toString());
    }
}
