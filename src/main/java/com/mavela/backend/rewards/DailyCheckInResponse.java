package com.mavela.backend.rewards;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Server-authoritative result of a daily Mavela Gems check-in.")
public record DailyCheckInResponse(
        @Schema(description = "Available non-monetary Mavela Gems after this request.")
        long availableGems,
        @Schema(description = "Gems newly awarded by this request, including any reached milestone.")
        int awardedGems,
        @Schema(description = "Current consecutive Kinshasa business-day check-in count.")
        int currentStreak,
        @Schema(description = "Whether today was already checked in before this request.")
        boolean alreadyCompletedToday,
        @Schema(description = "Milestone newly reached by this request, when any.")
        GemsMilestoneResponse milestoneReached,
        @Schema(description = "The next available streak milestone, when any.")
        GemsMilestoneResponse nextMilestone,
        @Schema(description = "Short customer-safe status message.")
        String message
) {
}
