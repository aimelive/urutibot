package com.aimelive.urutibot.security;

import com.aimelive.urutibot.config.CacheConfig;
import com.aimelive.urutibot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Hot path — invoked by {@code JwtAuthenticationFilter} on every authenticated
     * HTTP request. Loads the user with roles + permissions in one query (via
     * {@code @EntityGraph}) and caches the resulting {@link AppUserPrincipal}
     * snapshot in Caffeine. Roles are already materialised on the snapshot, so
     * there is no lazy-init risk when downstream code (e.g. the streaming
     * chatbot worker thread) reads them.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.USERS_BY_EMAIL, key = "#email", unless = "#result == null")
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findWithAuthoritiesByEmail(email)
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    /**
     * Invalidate the email-keyed cache entry when a user's authority graph
     * changes (registration, role grant/revoke, password reset, etc.). The
     * id-keyed cache is evicted separately by callers that already know the
     * user id — see {@code AuthService}.
     */
    @CacheEvict(value = CacheConfig.USERS_BY_EMAIL, key = "#email")
    public void evict(String email) {
        // no-op — annotation handles eviction
    }
}
