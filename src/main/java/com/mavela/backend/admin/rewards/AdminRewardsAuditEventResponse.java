package com.mavela.backend.admin.rewards;

import java.time.Instant;

/** Staff-safe audit projection. Internal notes and idempotency values stay server-only. */
public record AdminRewardsAuditEventResponse(
        RewardsAdminAction action,
        int amount,
        String reasonCode,
        String staffDisplayName,
        Instant createdAt
) {

    static AdminRewardsAuditEventResponse from(RewardsAdminAuditEvent event) {
        return new AdminRewardsAuditEventResponse(
                event.getAction(),
                event.getAmount(),
                event.getReasonCode(),
                event.getStaffUser().getDisplayName(),
                event.getCreatedAt()
        );
    }
}
