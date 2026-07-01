package com.unq.dapp.bolsa.auth.api;

public record AuthResponse(
        Long userId,
        String accessToken,
        long expiresIn
) {}
