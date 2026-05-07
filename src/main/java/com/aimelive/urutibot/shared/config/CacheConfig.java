package com.aimelive.urutibot.shared.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
@Slf4j
public class CacheConfig {

    /**
     * Email → AppUserPrincipal - JWT filter hot path. Evicted on
     * register/role-change.
     */
    public static final String USERS_BY_EMAIL = "usersByEmail";

    /**
     * UserId → User entity - chatbot tool {@code resolveUser} hot path. Evicted on
     * user mutation.
     */
    public static final String USERS_BY_ID = "usersById";

    /** RoleName → Role - static seed data, very long TTL. */
    public static final String ROLES_BY_NAME = "rolesByName";

    /** PermissionName → Permission - static seed data, very long TTL. */
    public static final String PERMISSIONS_BY_NAME = "permissionsByName";

    /**
     * AppointmentId → AppointmentResponse - detail endpoint cache, evicted on
     * cancel/update.
     */
    public static final String APPOINTMENTS_BY_ID = "appointmentsById";

    /**
     * (appointmentId, userId) → boolean —
     * {@link org.springframework.security.access.prepost.PreAuthorize} ownership
     * probe.
     */
    public static final String APPOINTMENT_OWNERSHIP = "appointmentOwnership";

    /**
     * UserId → bounded list of own appointments - chatbot tool. Evicted on the
     * user's create/cancel.
     */
    public static final String USER_APPOINTMENTS = "userAppointments";

    private final AtomicReference<SimpleCacheManager> cacheManagerRef = new AtomicReference<>();

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager mgr = new SimpleCacheManager();
        mgr.setCaches(List.of(
                build(USERS_BY_EMAIL, Duration.ofMinutes(10), 10_000),
                build(USERS_BY_ID, Duration.ofMinutes(10), 10_000),
                build(ROLES_BY_NAME, Duration.ofDays(1), 32),
                build(PERMISSIONS_BY_NAME, Duration.ofDays(1), 256),
                build(APPOINTMENTS_BY_ID, Duration.ofMinutes(5), 5_000),
                build(APPOINTMENT_OWNERSHIP, Duration.ofHours(1), 10_000),
                build(USER_APPOINTMENTS, Duration.ofMinutes(2), 5_000)));
        cacheManagerRef.set(mgr);
        return mgr;
    }

    private static CaffeineCache build(String name, Duration ttl, long maxSize) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .expireAfterWrite(ttl)
                        .maximumSize(maxSize)
                        .recordStats()
                        .build());
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void logCacheStats() {
        SimpleCacheManager mgr = cacheManagerRef.get();
        if (mgr == null)
            return;
        for (String name : mgr.getCacheNames()) {
            CaffeineCache c = (CaffeineCache) mgr.getCache(name);
            if (c == null)
                continue;
            CacheStats s = c.getNativeCache().stats();
            long size = c.getNativeCache().estimatedSize();
            long requests = s.requestCount();
            if (requests == 0)
                continue;
            log.info("cache='{}' size={} req={} hitRate={} hits={} misses={} evictions={}",
                    name, size, requests, String.format("%.2f", s.hitRate()),
                    s.hitCount(), s.missCount(), s.evictionCount());
        }
    }
}
