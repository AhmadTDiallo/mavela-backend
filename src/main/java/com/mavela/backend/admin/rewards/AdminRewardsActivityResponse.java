package com.mavela.backend.admin.rewards;

import com.mavela.backend.rewards.GemsLedgerEntry;
import com.mavela.backend.rewards.GemsLedgerEntryStatus;
import com.mavela.backend.rewards.GemsLedgerEntryType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Safe staff representation of an immutable ledger entry. */
public record AdminRewardsActivityResponse(
        UUID activityId,
        GemsLedgerEntryType entryType,
        int amount,
        GemsLedgerEntryStatus status,
        LocalDate businessDate,
        Instant createdAt,
        String reference,
        boolean eligibleForReversal
) {

    static AdminRewardsActivityResponse from(
            GemsLedgerEntry entry,
            boolean eligibleForReversal
    ) {
        return new AdminRewardsActivityResponse(
                entry.getPublicId(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getStatus(),
                entry.getBusinessDate(),
                entry.getCreatedAt(),
                entry.getReference(),
                eligibleForReversal
        );
    }
}
