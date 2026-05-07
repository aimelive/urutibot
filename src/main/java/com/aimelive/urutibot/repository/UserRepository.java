package com.aimelive.urutibot.repository;

import com.aimelive.urutibot.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    /**
     * Loads a user with their full RBAC graph (roles + permissions) in one round-trip.
     * Used by the JWT auth filter and login flow so subsequent requests/services can
     * read authorities outside the original transaction without lazy-init exceptions.
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithAuthoritiesByEmail(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithAuthoritiesById(UUID id);

    boolean existsByEmail(String email);
}
