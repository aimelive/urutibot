package com.aimelive.urutibot.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "chat_memories")
@Data
@NoArgsConstructor
public class ChatMemory {
    @Id
    private String id;
    private String memoryId;
    private String messages;

    public ChatMemory(String memoryId, String messages) {
        this.memoryId = memoryId;
        this.messages = messages;
    }
}
