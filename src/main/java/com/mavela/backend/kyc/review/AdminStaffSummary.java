package com.mavela.backend.kyc.review;

import com.mavela.backend.admin.staff.StaffUser;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record AdminStaffSummary(
        @Schema(description = "Internal staff allowlist identifier.") UUID id,
        @Schema(description = "Staff display name.") String displayName
) {

    public static AdminStaffSummary from(StaffUser staffUser) {
        if (staffUser == null) {
            return null;
        }
        return new AdminStaffSummary(staffUser.getId(), staffUser.getDisplayName());
    }
}
