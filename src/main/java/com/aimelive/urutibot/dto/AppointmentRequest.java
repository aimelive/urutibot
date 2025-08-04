package com.aimelive.urutibot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentRequest {
    @Schema(description = "The full name of the person making the appointment", example = "John Doe")
    private String fullName;
    @Schema(description = "The email address of the person making the appointment", example = "john.doe@example.com")
    private String email;
    @Schema(description = "The purpose of the appointment", example = "Consultation")
    private String purpose;
    @Schema(description = "The date and time of the appointment", example = "2025-08-08T10:00:00")
    private LocalDateTime dateTime;
}