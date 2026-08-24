package com.mavela.backend.rewards;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Capped, paginated customer-owned Mavela Gems activity.")
public record GemsActivityPageResponse(
        List<GemsActivityEntryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
