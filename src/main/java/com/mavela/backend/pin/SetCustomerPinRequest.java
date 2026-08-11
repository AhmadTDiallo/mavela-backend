package com.mavela.backend.pin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SetCustomerPinRequest(

        @NotNull(message = "PIN_SETUP_TOKEN_REQUIRED")
        @Pattern(
                regexp = "[A-Za-z0-9_-]{43}",
                message = "PIN_SETUP_TOKEN_INVALID_FORMAT"
        )
        String pinSetupToken,

        @NotNull(message = "PIN_REQUIRED")
        @Pattern(
                regexp = "[0-9]{4}",
                message = "PIN_INVALID_FORMAT"
        )
        String pin
) {
}