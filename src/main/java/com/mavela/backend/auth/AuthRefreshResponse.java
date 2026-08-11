package com.mavela.backend.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record AuthRefreshResponse(
        @Schema(description = "Identifier of the authenticated customer.")
        UUID customerId,

        @Schema(
                description = "New JWT access token for authenticated API requests. Treat this value as sensitive.",
                format = "JWT",
                accessMode = Schema.AccessMode.READ_ONLY
        )
        String accessToken,

        @Schema(
                description = "New refresh token for a future token refresh. Treat this value as sensitive.",
                accessMode = Schema.AccessMode.READ_ONLY
        )
        String refreshToken,

        @Schema(description = "Token type for the access token.")
        String tokenType,

        @Schema(description = "Access token lifetime in seconds.")
        long expiresInSeconds
) {
}
