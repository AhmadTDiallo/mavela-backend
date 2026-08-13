package com.mavela.backend.kyc.review;

import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.kyc.KycApplication;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record AdminKycQueueItemResponse(
        UUID id,
        long version,
        KycStatus status,
        Instant submittedAt,
        AdminStaffSummary assignedReviewer,
        AdminCustomerTriageResponse customer
) {

    public static AdminKycQueueItemResponse from(KycApplication application) {
        return new AdminKycQueueItemResponse(
                application.getId(),
                application.getVersion(),
                application.getStatus(),
                application.getSubmittedAt(),
                AdminStaffSummary.from(application.getAssignedReviewer()),
                AdminCustomerTriageResponse.from(application.getCustomer())
        );
    }
}
