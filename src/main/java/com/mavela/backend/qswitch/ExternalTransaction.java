package com.mavela.backend.qswitch;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sanitized Mavela transaction representation. Future QSwitch-specific DTOs
 * must be translated before reaching this type.
 */
public record ExternalTransaction(
        String reference,
        Instant bookedAt,
        ExternalTransactionDirection direction,
        BigDecimal amount,
        ExternalAccountCurrency currency,
        String description
) {
}
