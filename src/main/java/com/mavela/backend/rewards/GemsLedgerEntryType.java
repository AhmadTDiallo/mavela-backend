package com.mavela.backend.rewards;

/**
 * Immutable reasons for a change in a customer's non-monetary Gems balance.
 */
public enum GemsLedgerEntryType {
    DAILY_CHECK_IN,
    STREAK_MILESTONE,
    MANUAL_ADJUSTMENT,
    REDEMPTION,
    REVERSAL,
    EXPIRATION
}
