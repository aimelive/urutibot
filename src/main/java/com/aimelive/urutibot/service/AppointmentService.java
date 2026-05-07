package com.aimelive.urutibot.service;

import com.aimelive.urutibot.config.CacheConfig;
import com.aimelive.urutibot.dto.AppointmentRequest;
import com.aimelive.urutibot.dto.AppointmentResponse;
import com.aimelive.urutibot.event.AppointmentLifecycleEvent;
import com.aimelive.urutibot.exception.HttpException;
import com.aimelive.urutibot.model.Appointment;
import com.aimelive.urutibot.model.User;
import com.aimelive.urutibot.repository.AppointmentRepository;
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

    /** Hard cap for the chatbot tool listing — defends the LLM context window. */
    private static final int CHATBOT_MAX_APPOINTMENTS = 50;
    private static final Pageable CHATBOT_PAGE_REQUEST =
            PageRequest.of(0, CHATBOT_MAX_APPOINTMENTS, Sort.by(Sort.Direction.DESC, "dateTime"));

    private final AppointmentRepository appointmentRepository;
    private final ApplicationEventPublisher events;

    /**
     * Evicts only the owning user's bounded-list cache — the new appointment's
     * id has never been cached (it didn't exist), so the per-id cache is left
     * alone to avoid a no-op cache miss.
     */
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

    /**
     * Returns the authenticated user's most-recent appointments, capped at
     * {@link #CHATBOT_MAX_APPOINTMENTS}. Cached for 60s under the user id —
     * eviction fires on this user's create/cancel/updateStatus paths.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.USER_APPOINTMENTS, key = "#userId")
    public List<AppointmentResponse> getAppointmentsForUser(UUID userId) {
        return appointmentRepository.findByUserIdOrderByDateTimeDesc(userId, CHATBOT_PAGE_REQUEST).stream()
                .map(AppointmentResponse::fromAppointment)
                .toList();
    }

    /**
     * Paged variant — Pageable carries arbitrary page/size/sort combinations
     * which would explode the cache key cardinality, so this endpoint stays
     * uncached and pays the SELECT directly.
     */
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

    /**
     * Detail fetch — cached by id under {@link CacheConfig#APPOINTMENTS_BY_ID}.
     * The cache holds {@link AppointmentResponse} (a detached value object)
     * rather than the JPA entity, so cached reads do not trigger lazy fetches
     * outside a transaction. Eviction fires on cancel/updateStatus.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.APPOINTMENTS_BY_ID, key = "#id", unless = "#result == null")
    public Optional<AppointmentResponse> getAppointmentById(UUID id) {
        return appointmentRepository.findWithUserById(id).map(AppointmentResponse::fromAppointment);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.APPOINTMENTS_BY_ID, key = "#id"),
            @CacheEvict(value = CacheConfig.USER_APPOINTMENTS,
                    key = "#result.userId",
                    condition = "#result != null && #result.userId != null")
    })
    public AppointmentResponse cancelAppointment(UUID id) {
        Appointment appointment = appointmentRepository.findWithUserById(id)
                .orElseThrow(() -> new HttpException(HttpStatus.NOT_FOUND, "Appointment not found"));
        // Internal call — proxy is not re-entered, so this method's @CacheEvict
        // does the eviction work for the controller path. The chatbot path
        // calls cancelLoaded externally and triggers that method's evictions.
        appointment.setStatus(Appointment.Status.CANCELLED);
        events.publishEvent(AppointmentLifecycleEvent.of(appointment, AppointmentLifecycleEvent.Phase.CANCELLED));
        return AppointmentResponse.fromAppointment(appointment);
    }

    /**
     * Cancellation overload for callers that have already loaded the appointment
     * (with its {@code user} graph) — typically the chatbot tool path. Evicts
     * both the per-id cache and the owning user's appointment list so the
     * next read sees the cancelled status.
     */
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
    public AppointmentResponse updateStatus(UUID id, Appointment.Status target) {
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
