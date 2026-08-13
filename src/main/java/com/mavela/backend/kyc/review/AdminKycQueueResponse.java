package com.mavela.backend.kyc.review;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Capped, paginated staff KYC review queue.")
public record AdminKycQueueResponse(
        List<AdminKycQueueItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
