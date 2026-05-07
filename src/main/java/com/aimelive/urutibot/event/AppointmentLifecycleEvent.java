package com.aimelive.urutibot.event;

import com.aimelive.urutibot.model.Appointment;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable snapshot of an appointment, dispatched on the application event
 * bus by {@code AppointmentService} and consumed by notification listeners.
 *
 * <p>Carrying a snapshot — instead of the live JPA entity — decouples the
 * notifier from the persistence context, so handlers running on the
 * {@code mailExecutor} thread pool never trigger lazy loads on a closed
 * Hibernate session.
 *
 * <p>The {@link Phase} discriminator lets a single listener route to the
 * correct template without registering three separate event classes.
 */
public record AppointmentLifecycleEvent(
        UUID appointmentId,
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
