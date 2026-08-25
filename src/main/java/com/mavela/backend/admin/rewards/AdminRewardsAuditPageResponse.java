package com.mavela.backend.admin.rewards;

import java.util.List;

public record AdminRewardsAuditPageResponse(
        List<AdminRewardsAuditEventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
