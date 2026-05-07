package com.aimelive.urutibot.appointment;

import com.aimelive.urutibot.shared.config.CacheConfig;
import com.aimelive.urutibot.appointment.dto.AppointmentRequest;
import com.aimelive.urutibot.appointment.dto.AppointmentResponse;
import com.aimelive.urutibot.appointment.event.AppointmentLifecycleEvent;
import com.aimelive.urutibot.shared.exception.HttpException;
import com.aimelive.urutibot.appointment.model.Appointment;
import com.aimelive.urutibot.auth.model.User;
import com.aimelive.urutibot.appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {
    private static final int CHATBOT_MAX_APPOINTMENTS = 10;
    private static final Pageable CHATBOT_PAGE_REQUEST =
            PageRequest.of(0, CHATBOT_MAX_APPOINTMENTS, Sort.by(Sort.Direction.DESC, "dateTime"));

    private final AppointmentRepository appointmentRepository;
    private final ApplicationEventPublisher events;


    @Transactional
    @CacheEvict(value = CacheConfig.USER_APPOINTMENTS, key = "#user.id")
    public AppointmentResponse createAppointment(AppointmentRequest request, User user) {
        Appointment appointment = Appointment.builder()
                .user(user)
                .purpose(request.getPurpose())
                .dateTime(request.getDateTime())
                .status(Appointment.Status.BOOKED)
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        events.publishEvent(AppointmentLifecycleEvent.of(saved, AppointmentLifecycleEvent.Phase.CREATED));
        return AppointmentResponse.fromAppointment(saved);
    }


    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.USER_APPOINTMENTS, key = "#userId")
    public List<AppointmentResponse> getAppointmentsForUser(UUID userId) {
        return appointmentRepository.findByUserIdOrderByDateTimeDesc(userId, CHATBOT_PAGE_REQUEST).stream()
                .map(AppointmentResponse::fromAppointment)
                .toList();
    }


    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAppointmentsForUser(UUID userId, Pageable pageable) {
        return appointmentRepository.findByUserId(userId, pageable)
                .map(AppointmentResponse::fromAppointment);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAllAppointments(Pageable pageable) {
        return appointmentRepository.findAllByOrderByDateTimeDesc(pageable)
                .map(AppointmentResponse::fromAppointment);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.APPOINTMENTS_BY_ID, key = "#id", unless = "#result == null")
    public Optional<AppointmentResponse> getAppointmentById(Long id) {
        return appointmentRepository.findWithUserById(id).map(AppointmentResponse::fromAppointment);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.APPOINTMENTS_BY_ID, key = "#id"),
            @CacheEvict(value = CacheConfig.USER_APPOINTMENTS,
                    key = "#result.userId",
                    condition = "#result != null && #result.userId != null")
    })
    public AppointmentResponse cancelAppointment(Long id) {
        Appointment appointment = appointmentRepository.findWithUserById(id)
                .orElseThrow(() -> new HttpException(HttpStatus.NOT_FOUND, "Appointment not found"));
        appointment.setStatus(Appointment.Status.CANCELLED);
        events.publishEvent(AppointmentLifecycleEvent.of(appointment, AppointmentLifecycleEvent.Phase.CANCELLED));
        return AppointmentResponse.fromAppointment(appointment);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.APPOINTMENTS_BY_ID, key = "#appointment.id"),
            @CacheEvict(value = CacheConfig.USER_APPOINTMENTS,
                    key = "#appointment.user.id",
                    condition = "#appointment.user != null")
    })
    public AppointmentResponse cancelLoaded(Appointment appointment) {
        appointment.setStatus(Appointment.Status.CANCELLED);
        events.publishEvent(AppointmentLifecycleEvent.of(appointment, AppointmentLifecycleEvent.Phase.CANCELLED));
        return AppointmentResponse.fromAppointment(appointment);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.APPOINTMENTS_BY_ID, key = "#id"),
            @CacheEvict(value = CacheConfig.USER_APPOINTMENTS,
                    key = "#result.userId",
                    condition = "#result != null && #result.userId != null")
    })
    public AppointmentResponse updateStatus(Long id, Appointment.Status target) {
        Appointment appointment = appointmentRepository.findWithUserById(id)
                .orElseThrow(() -> new HttpException(HttpStatus.NOT_FOUND, "Appointment not found"));
        appointment.setStatus(target);

        switch (target) {
            case CANCELLED -> events.publishEvent(
                    AppointmentLifecycleEvent.of(appointment, AppointmentLifecycleEvent.Phase.CANCELLED));
            case COMPLETED -> events.publishEvent(
                    AppointmentLifecycleEvent.of(appointment, AppointmentLifecycleEvent.Phase.COMPLETED));
            case BOOKED -> {
                // No notification for re-opening an appointment.
            }
        }

        return AppointmentResponse.fromAppointment(appointment);
    }
}
