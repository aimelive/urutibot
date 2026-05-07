package com.aimelive.urutibot.appointment.repository;

import com.aimelive.urutibot.appointment.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @EntityGraph(attributePaths = "user")
    Page<Appointment> findByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    List<Appointment> findByUserIdOrderByDateTimeDesc(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Appointment> findAllByOrderByDateTimeDesc(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<Appointment> findWithUserById(Long id);

    boolean existsByIdAndUser_Id(Long id, UUID userId);

    @EntityGraph(attributePaths = "user")
    Optional<Appointment> findByIdAndUser_Id(Long id, UUID userId);
}
