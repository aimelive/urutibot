package com.aimelive.urutibot.service;

import com.aimelive.urutibot.config.CacheConfig;
import com.aimelive.urutibot.dto.auth.AuthResponse;
import com.aimelive.urutibot.dto.auth.LoginRequest;
import com.aimelive.urutibot.dto.auth.RegisterRequest;
import com.aimelive.urutibot.exception.HttpException;
import com.aimelive.urutibot.model.Role;
import com.aimelive.urutibot.model.User;
import com.aimelive.urutibot.repository.RoleRepository;
import com.aimelive.urutibot.repository.UserRepository;
import com.aimelive.urutibot.security.CustomUserDetailsService;
import com.aimelive.urutibot.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Defensive eviction of {@link CacheConfig#USERS_BY_ID} on registration.
     * The new user's id is unknown until the row is saved, so we evict by
     * key after save (see body) — this annotation handles the rare case where
     * a re-registration follows an account deletion under the same email.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.USERS_BY_EMAIL, key = "#request.email")
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new HttpException(HttpStatus.CONFLICT, "Email is already registered");
        }

        Role userRole = roleRepository.findByName(Role.RoleName.USER)
                .orElseThrow(() -> new HttpException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Default USER role is not seeded"));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .enabled(true)
                .roles(roles)
                .build();

        User saved = userRepository.save(user);
        // Defensive eviction in case a previous tombstone was cached for this email.
        userDetailsService.evict(saved.getEmail());
        return buildAuthResponse(saved);
    }

    /**
     * Login uses {@code findWithAuthoritiesByEmail} so role names can be
     * read after the transaction commits — without it, building the JWT
     * response would trigger a {@code LazyInitializationException} now that
     * {@code User.roles} is fetched lazily.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException e) {
            throw new HttpException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        User user = userRepository.findWithAuthoritiesByEmail(request.getEmail())
                .orElseThrow(() -> new HttpException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(jwtService.getExpirationMs())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(user.getRoles().stream()
                        .map(r -> r.getName().name())
                        .collect(Collectors.toSet()))
                .build();
    }
}
