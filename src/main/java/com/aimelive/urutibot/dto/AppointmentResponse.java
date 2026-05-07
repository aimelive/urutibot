package com.aimelive.urutibot.dto;

import com.aimelive.urutibot.model.Appointment;
import com.aimelive.urutibot.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {
    private String id;
    private UUID userId;
    /** Derived from {@code appointment.user.fullName}; convenience for clients/email templates. */
    private String fullName;
    /** Derived from {@code appointment.user.email}; convenience for clients/email templates. */
    private String email;
    private String purpose;
    private LocalDateTime dateTime;
    private Appointment.Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AppointmentResponse fromAppointment(Appointment appointment) {
        User user = appointment.getUser();
        return AppointmentResponse.builder()
                .id(appointment.getId() != null ? appointment.getId().toString() : null)
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : null)
                .email(user != null ? user.getEmail() : null)
                .purpose(appointment.getPurpose())
                .dateTime(appointment.getDateTime())
                .status(appointment.getStatus())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .build();
    }
}
