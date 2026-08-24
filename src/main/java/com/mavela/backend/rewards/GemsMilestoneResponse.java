package com.mavela.backend.rewards;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A Mavela Gems daily-streak milestone.")
public record GemsMilestoneResponse(
        @Schema(description = "Consecutive business-day streak required.")
        int day,

        @Schema(description = "Non-monetary Gems awarded at this milestone.")
        int gems
) {
}
