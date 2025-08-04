package com.aimelive.urutibot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Future;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentRequest {
    @Schema(description = "The full name of the person making the appointment", example = "John Doe")
    @NotBlank(message = "Full name is required")
    private String fullName;

    @Schema(description = "The email address of the person making the appointment", example = "john.doe@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    private String email;

    @Schema(description = "The purpose of the appointment", example = "Consultation")
    @NotBlank(message = "Purpose is required")
    private String purpose;

    @Schema(description = "The date and time of the appointment", example = "2025-08-08T10:00:00")
    @NotNull(message = "Date and time are required")
    @Future(message = "Date and time must be in the future")
    private LocalDateTime dateTime;
}