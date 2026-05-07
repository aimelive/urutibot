package com.aimelive.urutibot.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @Schema(example = "john.doe@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    private String email;

    @Schema(example = "S3cur3P@ssw0rd")
    @NotBlank(message = "Password is required")
    private String password;
}
