package com.aimelive.urutibot.config;

import com.aimelive.urutibot.model.Role;
import com.aimelive.urutibot.model.User;
import com.aimelive.urutibot.repository.RoleRepository;
import com.aimelive.urutibot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;


@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap.email:}")
    private String adminEmail;

    @Value("${app.admin.bootstrap.password:}")
    private String adminPassword;

    @Value("${app.admin.bootstrap.full-name:Administrator}")
    private String adminFullName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.info("AdminBootstrap: app.admin.bootstrap.email not set; skipping admin seed.");
            return;
        }
        if (userRepository.existsByEmail(adminEmail)) {
            log.debug("AdminBootstrap: admin user '{}' already exists; nothing to do.", adminEmail);
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("AdminBootstrap: ADMIN_PASSWORD is empty — cannot seed admin '{}'. " +
                    "Set ADMIN_PASSWORD and restart.", adminEmail);
            return;
        }

        Role adminRole = roleRepository.findByName(Role.RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "ADMIN role not seeded — Flyway V2 must run before AdminBootstrap."));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);

        User admin = User.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .fullName(adminFullName)
                .enabled(true)
                .roles(roles)
                .build();

        userRepository.save(admin);
        log.info("AdminBootstrap: seeded admin user '{}'", adminEmail);
    }
}
