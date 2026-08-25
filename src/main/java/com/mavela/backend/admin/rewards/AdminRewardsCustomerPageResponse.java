package com.mavela.backend.admin.rewards;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Capped page of minimal staff rewards customer records.")
public record AdminRewardsCustomerPageResponse(
        List<AdminRewardsCustomerResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
