package com.aimelive.urutibot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "appointments")
@AllArgsConstructor
@Data
@Builder
public class Appointment {
    @Id
    private String id;
    private String fullName;
    private String email;
    private LocalDateTime dateTime;
    private String purpose;
    private Status status;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum Status {
        BOOKED,
        COMPLETED,
        CANCELLED
    }
}
