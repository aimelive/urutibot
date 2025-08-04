package com.aimelive.urutibot.repository;

import com.aimelive.urutibot.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    Optional<Message> findByMemoryId(String memoryId);

    void deleteByMemoryId(String memoryId);
}
