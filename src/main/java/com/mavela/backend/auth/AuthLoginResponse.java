package com.mavela.backend.auth;

import java.util.UUID;

public record AuthLoginResponse(
        UUID customerId,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
}