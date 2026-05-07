package com.aimelive.urutibot.repository;

import com.aimelive.urutibot.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /**
     * Page of an authenticated user's own appointments — eager-fetches the user
     * (single join) so DTO mapping doesn't trigger lazy loads outside the txn.
     * Roles/permissions are NOT pulled in: the response DTO only needs name/email.
     */
    @EntityGraph(attributePaths = "user")
    Page<Appointment> findByUserId(UUID userId, Pageable pageable);

    /**
     * Bounded list variant retained for the chatbot tool — caller passes a
     * Pageable to enforce a hard cap (default 50) so a chatty user doesn't
     * stream their entire 10-year history into the LLM prompt.
     */
    @EntityGraph(attributePaths = "user")
    List<Appointment> findByUserIdOrderByDateTimeDesc(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Appointment> findAllByOrderByDateTimeDesc(Pageable pageable);

    /**
     * Single-row fetch with the owning user joined — used by detail endpoints,
     * cancel paths, and chatbot tools (where the call may run outside an
     * outer transaction in a LangChain4j worker thread).
     */
    @EntityGraph(attributePaths = "user")
    Optional<Appointment> findWithUserById(UUID id);

    /**
     * Lightweight ownership probe — replaces a full Appointment + User + roles
     * + permissions hydration with a single indexed boolean query.
     */
    boolean existsByIdAndUser_Id(UUID id, UUID userId);

    /**
     * Single-shot owner-aware fetch — returns the appointment only when the
     * caller actually owns it. Used by the chatbot cancellation tool to
     * collapse the previous {@code existsByIdAndUser_Id} + {@code existsById}
     * + {@code findWithUserById} chain into a single indexed lookup.
     */
    @EntityGraph(attributePaths = "user")
    Optional<Appointment> findByIdAndUser_Id(UUID id, UUID userId);
}
