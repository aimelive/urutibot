package com.aimelive.urutibot.repository;

import com.aimelive.urutibot.config.CacheConfig;
import com.aimelive.urutibot.model.Role;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Short> {

    /**
     * Roles are seeded by Flyway and never mutated at runtime; cache aggressively.
     * Hit on every {@code AuthService.register} and {@code AdminBootstrap} call.
     */
    @Cacheable(value = CacheConfig.ROLES_BY_NAME, key = "#name", unless = "#result == null")
    Optional<Role> findByName(Role.RoleName name);
}
