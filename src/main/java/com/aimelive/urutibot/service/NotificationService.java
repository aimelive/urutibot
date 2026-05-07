package com.aimelive.urutibot.service;

import com.aimelive.urutibot.event.AppointmentLifecycleEvent;

/**
 * Public hook the rest of the codebase can call directly when an in-process
 * {@link AppointmentLifecycleEvent} dispatch isn't available (e.g. ad-hoc
 * admin tooling). The HTTP/booking flow uses the
 * {@code @TransactionalEventListener(AFTER_COMMIT)} pathway in
 * {@link NotificationServiceImpl} so emails only fire after the DB write
 * has durably committed.
 */
public interface NotificationService {
    void onAppointmentEvent(AppointmentLifecycleEvent event);
}
