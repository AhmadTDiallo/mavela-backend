package com.mavela.backend.rewards;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Current customer-owned Mavela Gems summary.")
public record GemsSummaryResponse(
        long availableGems,
        int currentStreak,
        boolean checkedInToday,
        GemsMilestoneResponse nextMilestone,
        List<GemsActivityEntryResponse> recentActivity
) {
}
