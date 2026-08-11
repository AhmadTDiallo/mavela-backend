package com.mavela.backend.username;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SetCustomerUsernameRequest(

        @Schema(
                description = "Short-lived token issued after successful phone verification.",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @NotNull(message = "PIN_SETUP_TOKEN_REQUIRED")
        @Pattern(
                regexp = "[A-Za-z0-9_-]{43}",
                message = "PIN_SETUP_TOKEN_INVALID_FORMAT"
        )
        String pinSetupToken,

        @Schema(
                description = "Unique customer username containing 3 to 20 lowercase letters or digits."
        )
        @NotNull(message = "USERNAME_REQUIRED")
        @Pattern(
                regexp = "[a-z0-9]{3,20}",
                message = "USERNAME_INVALID_FORMAT"
        )
        String username
) {
}
