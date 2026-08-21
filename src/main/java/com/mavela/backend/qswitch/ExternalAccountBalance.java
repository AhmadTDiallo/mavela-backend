package com.mavela.backend.qswitch;

import java.math.BigDecimal;
import java.time.Instant;

public record ExternalAccountBalance(
        ExternalAccount account,
        BigDecimal availableAmount,
        Instant asOf
) {
}
