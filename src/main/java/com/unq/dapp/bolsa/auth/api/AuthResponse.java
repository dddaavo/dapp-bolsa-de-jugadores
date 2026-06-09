package com.unq.dapp.bolsa.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(description = "ID del usuario autenticado", example = "1")
        Long userId,
        @Schema(description = "JWT de acceso", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "Tiempo de expiración en segundos", example = "900")
        long expiresIn
) {}
