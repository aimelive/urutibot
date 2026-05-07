package com.aimelive.urutibot.appointment.dto;

import com.aimelive.urutibot.appointment.model.Appointment;
import com.aimelive.urutibot.auth.model.User;
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
    /**
     * Derived from {@code appointment.user.fullName}; convenience for clients/email
     * templates.
     */
    private String fullName;
    /**
     * Derived from {@code appointment.user.email}; convenience for clients/email
     * templates.
     */
    private String email;
    private String purpose;
    private LocalDateTime dateTime;
    private Appointment.Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AppointmentResponse fromAppointment(Appointment appointment) {
        User user = appointment.getUser();
        return AppointmentResponse.builder()
                .id(formatId(appointment.getId()))
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

    /**
     * Pads the appointment id to a minimum width of 3 digits ({@code 1 → "001"})
     * for display. Longer numbers are returned as-is - {@code %03d} is a
     * minimum-width specifier, not a truncating format. Parses back to the
     * underlying {@code Long} via {@link Long#parseLong} (base-10), which
     * ignores leading zeros.
     */
    private static String formatId(Long id) {
        return id != null ? String.format("%03d", id) : null;
    }
}
