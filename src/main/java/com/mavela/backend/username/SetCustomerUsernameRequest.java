package com.mavela.backend.username;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SetCustomerUsernameRequest(

        @NotNull(message = "PIN_SETUP_TOKEN_REQUIRED")
        @Pattern(
                regexp = "[A-Za-z0-9_-]{43}",
                message = "PIN_SETUP_TOKEN_INVALID_FORMAT"
        )
        String pinSetupToken,

        @NotNull(message = "USERNAME_REQUIRED")
        @Pattern(
                regexp = "[a-z0-9]{3,20}",
                message = "USERNAME_INVALID_FORMAT"
        )
        String username
) {
}