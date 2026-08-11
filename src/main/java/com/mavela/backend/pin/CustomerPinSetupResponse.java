package com.mavela.backend.pin;

import com.mavela.backend.customer.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerPinSetupResponse(
        UUID customerId,
        CustomerStatus status,
        Instant pinCreatedAt
) {
}