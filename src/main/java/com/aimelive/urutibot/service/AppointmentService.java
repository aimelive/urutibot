package com.aimelive.urutibot.service;

import com.aimelive.urutibot.dto.AppointmentRequest;
import com.aimelive.urutibot.dto.AppointmentResponse;
import com.aimelive.urutibot.model.Appointment;
import com.aimelive.urutibot.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;

    public AppointmentResponse createAppointment(AppointmentRequest request) {
        Appointment appointment = Appointment.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .purpose(request.getPurpose())
                .dateTime(request.getDateTime())
                .status(Appointment.Status.BOOKED)
                .build();

        Appointment savedAppointment = appointmentRepository.save(appointment);

        // Send email notification to admin
        try {
            notificationService.sendAppointmentCreatedNotification(savedAppointment);
        } catch (Exception e) {
            log.error("Failed to send appointment created notification for appointment ID: {}",
                    savedAppointment.getId(), e);
            // Don't fail the appointment creation if email fails
        }

        return AppointmentResponse.fromAppointment(savedAppointment);
    }

    public List<AppointmentResponse> getAppointmentsByEmail(String email) {
        List<Appointment> appointments = appointmentRepository.findByEmail(email);
        return appointments.stream()
                .map(AppointmentResponse::fromAppointment)
                .toList();
    }

    public Optional<AppointmentResponse> getAppointmentById(String id) {
        return appointmentRepository.findById(id)
                .map(AppointmentResponse::fromAppointment);
    }

    public Optional<AppointmentResponse> cancelAppointment(String id) {
        return appointmentRepository.findById(id)
                .map(appointment -> {
                    appointment.setStatus(Appointment.Status.CANCELLED);
                    Appointment savedAppointment = appointmentRepository.save(appointment);

                    // Send email notification to admin
                    try {
                        notificationService.sendAppointmentCancelledNotification(savedAppointment);
                    } catch (Exception e) {
                        log.error("Failed to send appointment cancelled notification for appointment ID: {}",
                                savedAppointment.getId(), e);
                        // Don't fail the appointment cancellation if email fails
                    }

                    return AppointmentResponse.fromAppointment(savedAppointment);
                });
    }

    public Optional<AppointmentResponse> completeAppointment(String id) {
        return appointmentRepository.findById(id)
                .map(appointment -> {
                    appointment.setStatus(Appointment.Status.COMPLETED);
                    Appointment savedAppointment = appointmentRepository.save(appointment);

                    // Send email notification to admin
                    try {
                        notificationService.sendAppointmentCompletedNotification(savedAppointment);
                    } catch (Exception e) {
                        log.error("Failed to send appointment completed notification for appointment ID: {}",
                                savedAppointment.getId(), e);
                        // Don't fail the appointment completion if email fails
                    }

                    return AppointmentResponse.fromAppointment(savedAppointment);
                });
    }
}
