package com.aimelive.urutibot.security;

import com.aimelive.urutibot.config.CacheConfig;
import com.aimelive.urutibot.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("appointmentSecurity")
@RequiredArgsConstructor
public class AppointmentSecurity {

    private final AppointmentRepository appointmentRepository;

    /**
     * Ownership probe used by {@code @PreAuthorize}. Hits a tight 2-minute
     * cache so a request that fans out into several authorized handlers (or
     * an authenticated user clicking through their own appointments) doesn't
     * pay the {@code existsByIdAndUser_Id} lookup more than once.
     *
     * <p>The cache is keyed on {@code (appointmentId, userId)}; we cannot key
     * on the {@code Authentication} object (it is not a stable hash key), so
     * the cacheable indirection is split out into {@link #ownsAppointment}.
     */
    public boolean isOwner(UUID appointmentId, Authentication authentication) {
        if (appointmentId == null
                || authentication == null
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return false;
        }
        return ownsAppointment(appointmentId, principal.getId());
    }

    /**
     * Cached membership check — the underlying {@code existsByIdAndUser_Id}
     * uses the {@code idx_appointments_user_datetime} index, but skipping the
     * lookup entirely is still meaningfully cheaper on the hot path.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.APPOINTMENT_OWNERSHIP, key = "#appointmentId.toString() + ':' + #userId.toString()")
    public boolean ownsAppointment(UUID appointmentId, UUID userId) {
        return appointmentRepository.existsByIdAndUser_Id(appointmentId, userId);
    }
}
