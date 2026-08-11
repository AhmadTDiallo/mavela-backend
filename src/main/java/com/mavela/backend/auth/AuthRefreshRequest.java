package com.mavela.backend.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AuthRefreshRequest(

        @Schema(
                description = "Refresh token issued during sign-in or a prior refresh.",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @NotBlank(message = "REFRESH_TOKEN_REQUIRED")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]{43}$",
                message = "REFRESH_TOKEN_INVALID_FORMAT"
        )
        String refreshToken
) {
}
