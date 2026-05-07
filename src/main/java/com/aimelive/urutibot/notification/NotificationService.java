package com.aimelive.urutibot.notification;

import com.aimelive.urutibot.appointment.event.AppointmentLifecycleEvent;

public interface NotificationService {
    void onAppointmentEvent(AppointmentLifecycleEvent event);
}
