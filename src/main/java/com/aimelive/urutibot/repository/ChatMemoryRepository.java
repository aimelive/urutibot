package com.aimelive.urutibot.repository;

import com.aimelive.urutibot.model.ChatMemory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatMemoryRepository extends MongoRepository<ChatMemory, String> {
    Optional<ChatMemory> findByMemoryId(String memoryId);

    void deleteByMemoryId(String memoryId);
}
