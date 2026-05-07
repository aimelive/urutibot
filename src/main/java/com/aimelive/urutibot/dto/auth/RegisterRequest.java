package com.aimelive.urutibot.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @Schema(example = "John Doe")
    @NotBlank(message = "Full name is required")
    private String fullName;

    @Schema(example = "john.doe@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    private String email;

    @Schema(example = "S3cur3P@ssw0rd")
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    @Schema(example = "+250788123456")
    private String phone;
}
