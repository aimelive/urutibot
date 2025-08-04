package com.aimelive.urutibot.service;

import com.aimelive.urutibot.model.Appointment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
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

    @Override
    public void sendAppointmentCreatedNotification(Appointment appointment) {
        try {
            String subject = "📅 New Appointment Scheduled - " + appointment.getFullName();
            sendAppointmentEmail(appointment, subject, "created");
            log.info("Appointment created notification sent for appointment ID: {}", appointment.getId());
        } catch (Exception e) {
            log.error("Failed to send appointment created notification for appointment ID: {}", appointment.getId(), e);
        }
    }

    @Override
    public void sendAppointmentCancelledNotification(Appointment appointment) {
        try {
            String subject = "❌ Appointment Cancelled - " + appointment.getFullName();
            sendAppointmentEmail(appointment, subject, "cancelled");
            log.info("Appointment cancelled notification sent for appointment ID: {}", appointment.getId());
        } catch (Exception e) {
            log.error("Failed to send appointment cancelled notification for appointment ID: {}", appointment.getId(),
                    e);
        }
    }

    @Override
    public void sendAppointmentCompletedNotification(Appointment appointment) {
        try {
            String subject = "✅ Appointment Completed - " + appointment.getFullName();
            sendAppointmentEmail(appointment, subject, "completed");
            log.info("Appointment completed notification sent for appointment ID: {}", appointment.getId());
        } catch (Exception e) {
            log.error("Failed to send appointment completed notification for appointment ID: {}", appointment.getId(),
                    e);
        }
    }

    private void sendAppointmentEmail(Appointment appointment, String subject, String action) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("UrutiBot <" + fromEmail + ">");
            helper.setTo(adminEmail);
            helper.setSubject(subject);

            // Prepare template context
            Context context = new Context();
            context.setVariable("appointment", appointment);
            context.setVariable("formattedDateTime", formatDateTime(appointment.getDateTime()));
            context.setVariable("googleCalendarUrl", generateGoogleCalendarUrl(appointment));
            context.setVariable("action", action);

            // Process template
            String htmlContent = templateEngine.process("appointment-email-template", context);
            helper.setText(htmlContent, true);

            // Send email
            mailSender.send(message);
            log.info("Email notification sent successfully for appointment ID: {}", appointment.getId());

        } catch (MessagingException e) {
            log.error("Failed to send email notification for appointment ID: {}", appointment.getId(), e);
            throw new RuntimeException("Failed to send email notification", e);
        }
    }

    private String formatDateTime(java.time.LocalDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");
        return dateTime.format(formatter);
    }

    private String generateGoogleCalendarUrl(Appointment appointment) {
        try {
            String eventTitle = URLEncoder.encode("Appointment with " + appointment.getFullName(),
                    StandardCharsets.UTF_8);
            String eventDescription = URLEncoder.encode(
                    "Purpose: " + appointment.getPurpose() + "\nClient Email: " + appointment.getEmail(),
                    StandardCharsets.UTF_8);

            // Format dates for Google Calendar
            String startDate = appointment.getDateTime().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
            String endDate = appointment.getDateTime().plusHours(1)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));

            return String.format(
                    "https://calendar.google.com/calendar/render?action=TEMPLATE&text=%s&details=%s&dates=%s/%s&ctz=UTC",
                    eventTitle, eventDescription, startDate, endDate);
        } catch (Exception e) {
            log.error("Failed to generate Google Calendar URL for appointment ID: {}", appointment.getId(), e);
            return "#";
        }
    }
}