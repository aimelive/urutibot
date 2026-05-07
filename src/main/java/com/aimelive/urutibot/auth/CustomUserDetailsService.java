package com.aimelive.urutibot.auth;

import com.aimelive.urutibot.shared.config.CacheConfig;
import com.aimelive.urutibot.auth.repository.UserRepository;
import com.aimelive.urutibot.auth.security.AppUserPrincipal;
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

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.USERS_BY_EMAIL, key = "#email", unless = "#result == null")
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findWithAuthoritiesByEmail(email)
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    @CacheEvict(value = CacheConfig.USERS_BY_EMAIL, key = "#email")
    public void evict(String email) {
        // no-op - annotation handles eviction
    }
}
