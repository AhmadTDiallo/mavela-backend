package com.mavela.backend.admin.rewards;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operational guardrails for non-monetary staff Gems commands. Defaults are
 * deliberately conservative and can be narrowed by environment per deploy.
 */
@ConfigurationProperties(prefix = "mavela.admin.rewards")
public class RewardsAdminProperties {

    private int maxAdjustmentAbsoluteAmount = 10_000;
    private int maxPageSize = 50;
    private int minimumQueryLength = 2;

    public int getMaxAdjustmentAbsoluteAmount() {
        return maxAdjustmentAbsoluteAmount;
    }

    public void setMaxAdjustmentAbsoluteAmount(int value) {
        maxAdjustmentAbsoluteAmount = value;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int value) {
        maxPageSize = value;
    }

    public int getMinimumQueryLength() {
        return minimumQueryLength;
    }

    public void setMinimumQueryLength(int value) {
        minimumQueryLength = value;
    }

    public int safeMaxAdjustmentAbsoluteAmount() {
        return Math.max(1, maxAdjustmentAbsoluteAmount);
    }

    public int safeMaxPageSize() {
        return Math.clamp(maxPageSize, 1, 100);
    }

    public int safeMinimumQueryLength() {
        return Math.clamp(minimumQueryLength, 2, 20);
    }
}
