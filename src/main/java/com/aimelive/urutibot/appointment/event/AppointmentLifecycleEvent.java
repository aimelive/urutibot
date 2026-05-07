package com.aimelive.urutibot.appointment.event;

import com.aimelive.urutibot.appointment.model.Appointment;

import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentLifecycleEvent(
        Long appointmentId,
        UUID userId,
        String userFullName,
        String userEmail,
        String purpose,
        LocalDateTime dateTime,
        Appointment.Status status,
        Phase phase
) {
    public enum Phase { CREATED, CANCELLED, COMPLETED }

    public static AppointmentLifecycleEvent of(Appointment a, Phase phase) {
        return new AppointmentLifecycleEvent(
                a.getId(),
                a.getUser() != null ? a.getUser().getId() : null,
                a.getUser() != null ? a.getUser().getFullName() : "(unknown)",
                a.getUser() != null ? a.getUser().getEmail() : "",
                a.getPurpose(),
                a.getDateTime(),
                a.getStatus(),
                phase
        );
    }
}
