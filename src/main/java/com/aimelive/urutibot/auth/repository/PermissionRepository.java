package com.aimelive.urutibot.auth.repository;

import com.aimelive.urutibot.shared.config.CacheConfig;
import com.aimelive.urutibot.auth.model.Permission;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Short> {

    /**
     * Permissions are seeded by Flyway and never mutated at runtime; cache aggressively.
     */
    @Cacheable(value = CacheConfig.PERMISSIONS_BY_NAME, key = "#name", unless = "#result == null")
    Optional<Permission> findByName(String name);
}
