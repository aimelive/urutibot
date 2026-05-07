package com.aimelive.urutibot.dto;

import com.aimelive.urutibot.model.Appointment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppointmentStatusUpdateRequest {

    @Schema(description = "Target appointment status", example = "COMPLETED")
    @NotNull(message = "Status is required")
    private Appointment.Status status;
}
