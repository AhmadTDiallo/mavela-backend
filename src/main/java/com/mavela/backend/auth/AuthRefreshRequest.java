package com.mavela.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AuthRefreshRequest(

        @NotBlank(message = "REFRESH_TOKEN_REQUIRED")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]{43}$",
                message = "REFRESH_TOKEN_INVALID_FORMAT"
        )
        String refreshToken
) {
}