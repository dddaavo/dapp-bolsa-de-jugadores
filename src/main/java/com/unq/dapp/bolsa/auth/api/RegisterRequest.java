package com.unq.dapp.bolsa.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(description = "Email del usuario", example = "jugador@example.com")
        @NotBlank @Email String email,
        @Schema(description = "Contraseña (mínimo 8 caracteres)", example = "secret123")
        @NotBlank @Size(min = 8) String password
) {}
