package com.aimelive.urutibot.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "chat_messages")
@Data
@NoArgsConstructor
public class Message {
    @Id
    private String id;
    private String memoryId;
    private String messages;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Message(String memoryId, String messages) {
        this.memoryId = memoryId;
        this.messages = messages;
    }
}
