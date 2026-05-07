package com.aimelive.urutibot.service;

import com.aimelive.urutibot.dto.AppointmentRequest;
import com.aimelive.urutibot.dto.AppointmentResponse;
import com.aimelive.urutibot.model.Appointment;
import com.aimelive.urutibot.model.User;
import com.aimelive.urutibot.repository.AppointmentRepository;
import com.aimelive.urutibot.security.ChatAuthContext.AuthSnapshot;
import com.aimelive.urutibot.security.ChatPrincipalResolver;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentServiceTools {

    private static final Map<String, Object> AUTH_REQUIRED = Map.of(
            "requiresAuth", true,
            "message", "Please ask the user to log in or create an account before performing this action."
    );

    /**
     * Pre-compiled formatters — DateTimeFormatter is thread-safe but compiling
     * the pattern is non-trivial. Hoisting these out of the parse hot path
     * shaves measurable allocation per tool call.
     */
    private static final DateTimeFormatter DATE_TIME_SPACE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppointmentService appointmentService;
    private final AppointmentRepository appointmentRepository;
    private final ChatPrincipalResolver principalResolver;

    @Tool("Retrieve details of an appointment by its ID. Returns appointment information or {requiresAuth:true} if the user is not authenticated, or null if not found / not owned by current user.")
    @Transactional(readOnly = true)
    public Object getAppointmentDetails(@ToolMemoryId String memoryId, String appointmentId) {
        Optional<AuthSnapshot> snap = principalResolver.resolveSnapshot(memoryId);
        if (snap.isEmpty()) {
            log.debug("Tool getAppointmentDetails: anonymous (memoryId={})", memoryId);
            return AUTH_REQUIRED;
        }

        UUID id = parseUuid(appointmentId);
        if (id == null) return null;

        AuthSnapshot s = snap.get();
        // Admins can see any appointment; everyone else only their own — and
        // both paths use a single owner-aware (or plain) SELECT instead of
        // a "load + filter" pattern that would always pull the row first.
        if (s.hasAuthority("ROLE_ADMIN")) {
            return appointmentRepository.findWithUserById(id)
                    .map(AppointmentResponse::fromAppointment)
                    .orElse(null);
        }
        return appointmentRepository.findByIdAndUser_Id(id, s.userId())
                .map(AppointmentResponse::fromAppointment)
                .orElse(null);
    }

    // Note: takes a single unused parameter to work around a langchain4j-anthropic
    // 1.0.0-beta1 bug where zero-arg tool calls produce empty `arguments` strings
    // that then fail JSON parsing when the follow-up request is built.
    @Tool("List the authenticated user's own appointments. The 'filter' arg is unused — pass any string (e.g. \"all\"). Returns {requiresAuth:true} when called by an anonymous visitor.")
    @Transactional(readOnly = true)
    public Object getMyAppointments(@ToolMemoryId String memoryId, String filter) {
        return principalResolver.resolveSnapshot(memoryId)
                .<Object>map(s -> {
                    log.debug("Tool getMyAppointments: userId={} memoryId={}", s.userId(), memoryId);
                    return appointmentService.getAppointmentsForUser(s.userId());
                })
                .orElseGet(() -> {
                    log.debug("Tool getMyAppointments: anonymous (memoryId={})", memoryId);
                    return AUTH_REQUIRED;
                });
    }

    @Tool("Book an appointment for the authenticated user. Provide purpose and dateTime (ISO format yyyy-MM-dd'T'HH:mm:ss or yyyy-MM-dd HH:mm). Full name and email are taken from the authenticated user. Returns {requiresAuth:true} when called by an anonymous visitor.")
    public Object createAppointment(@ToolMemoryId String memoryId, String purpose, String dateTime) {
        Optional<User> userOpt = principalResolver.resolveUser(memoryId);
        if (userOpt.isEmpty()) {
            log.debug("Tool createAppointment: anonymous (memoryId={})", memoryId);
            return AUTH_REQUIRED;
        }

        User user = userOpt.get();
        LocalDateTime parsed = parseDateTime(dateTime);
        AppointmentRequest request = AppointmentRequest.builder()
                .purpose(purpose)
                .dateTime(parsed)
                .build();
        log.info("Tool createAppointment: booking for userId={} memoryId={}", user.getId(), memoryId);
        return appointmentService.createAppointment(request, user);
    }

    @Tool("Cancel an appointment by ID. Only the owning user (or an admin) can cancel. Returns {requiresAuth:true} for anonymous visitors, or {error:...} if not allowed.")
    @Transactional
    public Object cancelAppointment(@ToolMemoryId String memoryId, String appointmentId) {
        Optional<AuthSnapshot> snap = principalResolver.resolveSnapshot(memoryId);
        if (snap.isEmpty()) {
            log.debug("Tool cancelAppointment: anonymous (memoryId={})", memoryId);
            return AUTH_REQUIRED;
        }

        UUID id = parseUuid(appointmentId);
        if (id == null) return Map.of("error", "Invalid appointment ID");

        AuthSnapshot snapshot = snap.get();
        boolean admin = snapshot.hasAuthority("ROLE_ADMIN");

        // Single owner-aware (or plain) fetch with the user joined — the tool's
        // access check and the cancel write share one SELECT instead of the
        // earlier exists + find + cancel chain. The cancel path then mutates
        // the already-loaded entity through the {@code cancelLoaded} overload.
        Optional<Appointment> opt = admin
                ? appointmentRepository.findWithUserById(id)
                : appointmentRepository.findByIdAndUser_Id(id, snapshot.userId());

        if (opt.isEmpty()) {
            if (!admin && appointmentRepository.existsById(id)) {
                return Map.of("error", "You are not allowed to cancel this appointment.");
            }
            return Map.of("error", "Appointment not found");
        }

        log.info("Tool cancelAppointment: cancelling appointmentId={} userId={} memoryId={}",
                id, snapshot.userId(), memoryId);
        return appointmentService.cancelLoaded(opt.get());
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String dateTime) {
        try {
            return LocalDateTime.parse(dateTime);
        } catch (Exception e1) {
            try {
                return LocalDateTime.parse(dateTime, DATE_TIME_SPACE_FMT);
            } catch (Exception e2) {
                try {
                    return LocalDateTime.parse(dateTime + " 00:00", DATE_TIME_SPACE_FMT);
                } catch (Exception e3) {
                    throw new IllegalArgumentException(
                            "Invalid date format. Please use yyyy-MM-dd HH:mm or yyyy-MM-dd'T'HH:mm:ss");
                }
            }
        }
    }
}
