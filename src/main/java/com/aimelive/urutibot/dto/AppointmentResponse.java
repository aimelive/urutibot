package com.aimelive.urutibot.dto;

import com.aimelive.urutibot.model.Appointment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {
    private String id;
    private String fullName;
    private String email;
    private String purpose;
    private LocalDateTime dateTime;
    private Appointment.Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AppointmentResponse fromAppointment(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .fullName(appointment.getFullName())
                .email(appointment.getEmail())
                .purpose(appointment.getPurpose())
                .dateTime(appointment.getDateTime())
                .status(appointment.getStatus())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .build();
    }
}