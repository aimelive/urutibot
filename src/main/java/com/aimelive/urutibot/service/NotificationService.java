package com.aimelive.urutibot.service;

import com.aimelive.urutibot.model.Appointment;

public interface NotificationService {
    /**
     * Send email notification to admin when appointment is created
     * 
     * @param appointment The appointment that was created
     */
    void sendAppointmentCreatedNotification(Appointment appointment);

    /**
     * Send email notification to admin when appointment is cancelled
     * 
     * @param appointment The appointment that was cancelled
     */
    void sendAppointmentCancelledNotification(Appointment appointment);

    /**
     * Send email notification to admin when appointment is completed
     * 
     * @param appointment The appointment that was completed
     */
    void sendAppointmentCompletedNotification(Appointment appointment);
}
