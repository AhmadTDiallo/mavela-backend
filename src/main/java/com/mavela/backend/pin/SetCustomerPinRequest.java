package com.mavela.backend.pin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SetCustomerPinRequest(

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
                description = "Four-digit customer PIN. This value is not returned or stored in plain text.",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @NotNull(message = "PIN_REQUIRED")
        @Pattern(
                regexp = "[0-9]{4}",
                message = "PIN_INVALID_FORMAT"
        )
        String pin
) {
}
