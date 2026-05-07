package com.aimelive.urutibot.appointment.security;

import com.aimelive.urutibot.shared.config.CacheConfig;
import com.aimelive.urutibot.appointment.repository.AppointmentRepository;
import com.aimelive.urutibot.auth.security.AppUserPrincipal;
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

    public boolean isOwner(Long appointmentId, Authentication authentication) {
        if (appointmentId == null
                || authentication == null
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return false;
        }
        return ownsAppointment(appointmentId, principal.getId());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.APPOINTMENT_OWNERSHIP, key = "#appointmentId + ':' + #userId.toString()")
    public boolean ownsAppointment(Long appointmentId, UUID userId) {
        return appointmentRepository.existsByIdAndUser_Id(appointmentId, userId);
    }
}
