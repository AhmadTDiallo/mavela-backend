package com.mavela.backend.rewards;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Customer-safe Mavela Gems ledger activity.")
public record GemsActivityEntryResponse(
        GemsLedgerEntryType entryType,
        int amount,
        GemsLedgerEntryStatus status,
        String reference,
        LocalDate businessDate,
        Instant createdAt
) {

    public static GemsActivityEntryResponse from(GemsLedgerEntry entry) {
        return new GemsActivityEntryResponse(
                entry.getEntryType(),
                entry.getAmount(),
                entry.getStatus(),
                entry.getReference(),
                entry.getBusinessDate(),
                entry.getCreatedAt()
        );
    }
}
