package com.mavela.backend.pin;

import com.mavela.backend.customer.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record CustomerPinSetupResponse(
        @Schema(description = "Identifier of the customer whose PIN was created.")
        UUID customerId,

        @Schema(description = "Customer lifecycle status after PIN creation.")
        CustomerStatus status,

        @Schema(description = "Timestamp when the PIN credential was created.")
        Instant pinCreatedAt
) {
}
