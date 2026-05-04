package com.unq.dapp.bolsa.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "Email del usuario", example = "jugador@example.com")
        @NotBlank @Email String email,
        @Schema(description = "Contraseña del usuario", example = "secret123")
        @NotBlank String password
) {}
