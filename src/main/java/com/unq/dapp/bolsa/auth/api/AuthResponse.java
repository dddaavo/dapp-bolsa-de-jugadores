package com.unq.dapp.bolsa.auth.api;

public record AuthResponse(
        String accessToken,
        long expiresIn
) {}
