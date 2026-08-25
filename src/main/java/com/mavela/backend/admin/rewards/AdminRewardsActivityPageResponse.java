package com.mavela.backend.admin.rewards;

import java.util.List;

public record AdminRewardsActivityPageResponse(
        List<AdminRewardsActivityResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
