package com.aimelive.urutibot.controller;

import com.aimelive.urutibot.dto.AppointmentRequest;
import com.aimelive.urutibot.dto.AppointmentResponse;
import com.aimelive.urutibot.dto.AppointmentStatusUpdateRequest;
import com.aimelive.urutibot.exception.HttpException;
import com.aimelive.urutibot.security.AppUserPrincipal;
import com.aimelive.urutibot.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Appointment", description = "API endpoints for appointment management")
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @Operation(summary = "Create a new appointment for the authenticated user")
    @PreAuthorize("hasAuthority('APPOINTMENT_CREATE')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AppointmentResponse> createAppointment(
            @Valid @RequestBody AppointmentRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        AppointmentResponse response = appointmentService.createAppointment(request, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List the authenticated user's appointments")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ_OWN')")
    @GetMapping("/me")
    public ResponseEntity<List<AppointmentResponse>> getMyAppointments(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(appointmentService.getAppointmentsForUser(principal.getId()));
    }

    @Operation(summary = "List all appointments (admin)")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ_ALL')")
    @GetMapping
    public ResponseEntity<Page<AppointmentResponse>> listAll(Pageable pageable) {
        return ResponseEntity.ok(appointmentService.getAllAppointments(pageable));
    }

    @Operation(summary = "Get an appointment by ID (admin or owner)")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ_ALL') or @appointmentSecurity.isOwner(#id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getAppointmentById(@PathVariable UUID id) {
        return appointmentService.getAppointmentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Cancel an appointment (admin or owner)")
    @PreAuthorize("hasAuthority('APPOINTMENT_UPDATE_STATUS') or " +
            "(hasAuthority('APPOINTMENT_CANCEL_OWN') and @appointmentSecurity.isOwner(#id, authentication))")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancelAppointment(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.cancelAppointment(id));
    }

    @Operation(summary = "Update appointment status (admin)")
    @PreAuthorize("hasAuthority('APPOINTMENT_UPDATE_STATUS')")
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AppointmentResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AppointmentStatusUpdateRequest request) {
        if (request.getStatus() == null) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        return ResponseEntity.ok(appointmentService.updateStatus(id, request.getStatus()));
    }
}
