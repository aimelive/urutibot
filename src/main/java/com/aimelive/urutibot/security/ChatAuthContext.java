package com.aimelive.urutibot.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Component
@Slf4j
public class ChatAuthContext {

    public record AuthSnapshot(
            UUID userId,
            String email,
            String fullName,
            Set<String> authorities,
            Instant boundAt
    ) {
        public boolean hasAuthority(String authority) {
            return authorities != null && authorities.contains(authority);
        }
    }

    private final Map<String, AuthSnapshot> bindings = new ConcurrentHashMap<>();

    public void bind(String memoryId, AppUserPrincipal principal) {
        if (memoryId == null || principal == null) return;
        AuthSnapshot snap = new AuthSnapshot(
                principal.getId(),
                principal.getEmail(),
                principal.getUser().getFullName(),
                principal.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .collect(Collectors.toUnmodifiableSet()),
                Instant.now()
        );
        bindings.put(memoryId, snap);
        log.debug("ChatAuthContext bound memoryId={} userId={}", memoryId, principal.getId());
    }

    public void release(String memoryId) {
        if (memoryId == null) return;
        AuthSnapshot removed = bindings.remove(memoryId);
        if (removed != null) {
            log.debug("ChatAuthContext released memoryId={} userId={}", memoryId, removed.userId());
        }
    }

    public Optional<AuthSnapshot> get(String memoryId) {
        if (memoryId == null) return Optional.empty();
        return Optional.ofNullable(bindings.get(memoryId));
    }

    /**
     * Periodic safety net: SSE callbacks should release bindings, but if a
     * worker thread crashes mid-stream we don't want to leak the principal
     * snapshot indefinitely. 10 minutes is well past the 5-minute SSE timeout.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    void scheduledEviction() {
        evictOlderThan(10 * 60 * 1000L);
    }

    /** Defensive sweep — removes bindings older than the given millis. */
    public int evictOlderThan(long maxAgeMs) {
        Instant cutoff = Instant.now().minusMillis(maxAgeMs);
        int[] count = {0};
        bindings.entrySet().removeIf(e -> {
            boolean stale = e.getValue().boundAt().isBefore(cutoff);
            if (stale) count[0]++;
            return stale;
        });
        if (count[0] > 0) {
            log.info("ChatAuthContext evicted {} stale bindings (>{}ms old)", count[0], maxAgeMs);
        }
        return count[0];
    }

    int size() {
        return bindings.size();
    }
}
