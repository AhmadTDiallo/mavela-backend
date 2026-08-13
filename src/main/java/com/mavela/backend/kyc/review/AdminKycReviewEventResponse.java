package com.mavela.backend.kyc.review;

import com.mavela.backend.customer.KycStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Staff-only immutable review-history item. */
public record AdminKycReviewEventResponse(
        UUID id,
        AdminStaffSummary actor,
        KycReviewAction action,
        KycStatus previousStatus,
        KycStatus newStatus,
        KycReviewReasonCode reasonCode,
        String customerMessage,
        String internalNotes,
        List<UUID> evidenceIds,
        List<KycMissingRequirement> missingRequirements,
        UUID correlationId,
        Instant createdAt
) {

    public static AdminKycReviewEventResponse from(KycReviewEvent event) {
        return new AdminKycReviewEventResponse(
                event.getId(),
                AdminStaffSummary.from(event.getReviewer()),
                event.getAction(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getReasonCode(),
                event.getCustomerMessage(),
                event.getInternalNotes(),
                event.getEvidenceReferences().stream()
                        .map(reference -> reference.getEvidence().getId())
                        .toList(),
                event.getMissingRequirements().stream()
                        .map(KycReviewEventMissingRequirement::getRequirement)
                        .toList(),
                event.getCorrelationId(),
                event.getCreatedAt()
        );
    }
}
