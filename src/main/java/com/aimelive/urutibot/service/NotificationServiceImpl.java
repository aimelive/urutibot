package com.aimelive.urutibot.service;

import com.aimelive.urutibot.event.AppointmentLifecycleEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.admin.email:aimendayambaje24@gmail.com}")
    private String adminEmail;

    /**
     * Dispatch is gated on {@code AFTER_COMMIT} — the email never goes out
     * until the appointment row is durably persisted, eliminating the
     * "@Async-fires-before-commit" race that previously could mail out
     * rolled-back state. The {@code @Async} on the same method then frees
     * the request thread immediately after commit.
     */
    @Override
    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentEvent(AppointmentLifecycleEvent event) {
        try {
            String action = switch (event.phase()) {
                case CREATED -> "created";
                case CANCELLED -> "cancelled";
                case COMPLETED -> "completed";
            };
            String subjectPrefix = switch (event.phase()) {
                case CREATED -> "New Appointment Scheduled";
                case CANCELLED -> "Appointment Cancelled";
                case COMPLETED -> "Appointment Completed";
            };
            sendAppointmentEmail(event, subjectPrefix + " - " + event.userFullName(), action);
            log.info("Appointment {} notification sent for appointment ID: {}", action, event.appointmentId());
        } catch (Exception e) {
            log.error("Failed to send {} notification for appointment ID: {}",
                    event.phase(), event.appointmentId(), e);
            // Swallow — email failure must not bubble back into the booking flow.
        }
    }

    private void sendAppointmentEmail(AppointmentLifecycleEvent event, String subject, String action) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("UrutiBot <" + fromEmail + ">");
            helper.setTo(adminEmail);
            helper.setSubject(subject);

            Context context = new Context();
            context.setVariable("appointment", new AppointmentEmailView(event));
            context.setVariable("formattedDateTime", formatDateTime(event.dateTime()));
            context.setVariable("googleCalendarUrl", generateGoogleCalendarUrl(event));
            context.setVariable("action", action);

            String htmlContent = templateEngine.process("appointment-email-template", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email notification sent successfully for appointment ID: {}", event.appointmentId());
        } catch (MessagingException e) {
            log.error("Failed to send email notification for appointment ID: {}", event.appointmentId(), e);
            throw new RuntimeException("Failed to send email notification", e);
        }
    }

    private String formatDateTime(java.time.LocalDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");
        return dateTime.format(formatter);
    }

    /**
     * Thymeleaf-facing view exposing the property paths the existing
     * {@code appointment-email-template.html} expects ({@code appointment.user.fullName},
     * {@code appointment.statusLower}, etc.). Lets us swap the underlying entity
     * for an immutable event snapshot without rewriting the template.
     */
    public static final class AppointmentEmailView {
        private final AppointmentLifecycleEvent ev;
        private final UserView user;
        AppointmentEmailView(AppointmentLifecycleEvent ev) {
            this.ev = ev;
            this.user = new UserView(ev.userFullName(), ev.userEmail());
        }
        public String getId() { return ev.appointmentId() != null ? ev.appointmentId().toString() : ""; }
        public String getPurpose() { return ev.purpose(); }
        public java.time.LocalDateTime getDateTime() { return ev.dateTime(); }
        public com.aimelive.urutibot.model.Appointment.Status getStatus() { return ev.status(); }
        public String getStatusLower() { return ev.status() != null ? ev.status().name().toLowerCase() : ""; }
        public UserView getUser() { return user; }
    }

    public record UserView(String fullName, String email) {
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
    }

    private String generateGoogleCalendarUrl(AppointmentLifecycleEvent event) {
        try {
            String eventTitle = URLEncoder.encode("Appointment with " + event.userFullName(),
                    StandardCharsets.UTF_8);
            String eventDescription = URLEncoder.encode(
                    "Purpose: " + event.purpose() + "\nClient Email: " + event.userEmail(),
                    StandardCharsets.UTF_8);

            String startDate = event.dateTime().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
            String endDate = event.dateTime().plusHours(1)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));

            return String.format(
                    "https://calendar.google.com/calendar/render?action=TEMPLATE&text=%s&details=%s&dates=%s/%s&ctz=UTC",
                    eventTitle, eventDescription, startDate, endDate);
        } catch (Exception e) {
            log.error("Failed to generate Google Calendar URL for appointment ID: {}", event.appointmentId(), e);
            return "#";
        }
    }
}
