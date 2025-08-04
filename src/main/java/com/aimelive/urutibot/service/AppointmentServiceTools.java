package com.aimelive.urutibot.service;

import com.aimelive.urutibot.dto.AppointmentRequest;
import com.aimelive.urutibot.dto.AppointmentResponse;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AppointmentServiceTools {
    private final AppointmentService appointmentService;

    @Tool("Retrieve details of an appointment by its ID. Returns appointment information including full name, email, purpose, date and time, and status.")
    public AppointmentResponse getAppointmentDetails(String appointmentId) {
        return appointmentService.getAppointmentById(appointmentId)
                .orElse(null);
    }

    @Tool("List all appointments by user email. Returns a list of appointment information including full name, email, purpose, date and time, and status.")
    public List<AppointmentResponse> getAppointmentsByEmail(String email) {
        return appointmentService.getAppointmentsByEmail(email);
    }

    @Tool("Book an appointment. Returns appointment information including full name, email, purpose, date and time, and status.")
    public AppointmentResponse createAppointment(String fullName, String email, String purpose, String dateTime) {
        // Handle different date formats
        LocalDateTime parsedDateTime;
        try {
            // Try ISO format first (2025-08-05T14:00:00)
            parsedDateTime = LocalDateTime.parse(dateTime);
        } catch (Exception e1) {
            try {
                // Try format without seconds (2025-08-05 14:00)
                parsedDateTime = LocalDateTime.parse(dateTime,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (Exception e2) {
                try {
                    // Try format with date only (2025-08-05)
                    parsedDateTime = LocalDateTime.parse(dateTime + " 00:00",
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                } catch (Exception e3) {
                    throw new IllegalArgumentException(
                            "Invalid date format. Please use format: yyyy-MM-dd HH:mm or yyyy-MM-dd'T'HH:mm:ss");
                }
            }
        }

        AppointmentRequest request = new AppointmentRequest(fullName, email, purpose, parsedDateTime);
        return appointmentService.createAppointment(request);
    }

    @Tool("Cancel an appointment by its ID. Returns appointment information including full name, email, purpose, date and time, and status.")
    public AppointmentResponse cancelAppointment(String appointmentId) {
        return appointmentService.cancelAppointment(appointmentId)
                .orElse(null);
    }
}
