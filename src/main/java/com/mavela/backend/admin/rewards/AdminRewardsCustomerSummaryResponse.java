package com.mavela.backend.admin.rewards;

import java.time.LocalDate;
import java.util.List;

/** Staff-safe rewards summary with no internal note or audit data. */
public record AdminRewardsCustomerSummaryResponse(
        AdminRewardsCustomerResponse customer,
        long availableGems,
        int currentStreakDays,
        LocalDate lastQualifiedBusinessDate,
        LocalDate lastCheckInBusinessDate,
        List<AdminRewardsActivityResponse> recentActivity,
        boolean adjustmentEligible
) {
}
