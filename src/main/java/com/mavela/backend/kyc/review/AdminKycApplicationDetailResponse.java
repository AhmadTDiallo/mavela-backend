package com.mavela.backend.kyc.review;

import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.kyc.KycApplication;
import com.mavela.backend.kyc.KycDocumentType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Purpose-built staff detail response; no entity, object key, or URL leaks. */
public record AdminKycApplicationDetailResponse(
        UUID id,
        int attemptNumber,
        long version,
        KycStatus status,
        KycDocumentType documentType,
        Instant submittedAt,
        Instant reviewStartedAt,
        Instant reviewedAt,
        Instant decidedAt,
        AdminStaffSummary assignedReviewer,
        AdminCustomerTriageResponse customer,
        AdminKycProfileSnapshotResponse profile,
        List<AdminKycEvidenceMetadataResponse> evidence,
        List<AdminKycReviewEventResponse> reviewHistory
) {

    public static AdminKycApplicationDetailResponse from(
            KycApplication application,
            List<KycReviewEvent> reviewHistory
    ) {
        return new AdminKycApplicationDetailResponse(
                application.getId(),
                application.getAttemptNumber(),
                application.getVersion(),
                application.getStatus(),
                application.getDocumentType(),
                application.getSubmittedAt(),
                application.getReviewStartedAt(),
                application.getReviewedAt(),
                application.getDecidedAt(),
                AdminStaffSummary.from(application.getAssignedReviewer()),
                AdminCustomerTriageResponse.from(application.getCustomer()),
                AdminKycProfileSnapshotResponse.from(application),
                application.getDocuments().stream()
                        .map(AdminKycEvidenceMetadataResponse::from)
                        .toList(),
                reviewHistory.stream()
                        .map(AdminKycReviewEventResponse::from)
                        .toList()
        );
    }
}
