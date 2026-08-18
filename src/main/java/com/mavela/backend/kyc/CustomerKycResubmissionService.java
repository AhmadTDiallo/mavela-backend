package com.mavela.backend.kyc;

import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.kyc.review.KycMissingRequirement;
import com.mavela.backend.kyc.review.KycReviewAction;
import com.mavela.backend.kyc.review.KycReviewEvent;
import com.mavela.backend.kyc.review.KycReviewEventEvidence;
import com.mavela.backend.kyc.review.KycReviewEventRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Builds the customer-visible resubmission contract from the immutable staff
 * review event. The customer API never exposes review metadata or evidence
 * identifiers from that event.
 */
@Service
public class CustomerKycResubmissionService {

    private final KycReviewEventRepository reviewEventRepository;

    public CustomerKycResubmissionService(
            KycReviewEventRepository reviewEventRepository
    ) {
        this.reviewEventRepository = reviewEventRepository;
    }

    public Optional<KycResubmissionResponse> responseFor(
            KycApplication application
    ) {
        if (application.getStatus() != KycStatus.RESUBMISSION_REQUIRED) {
            return Optional.empty();
        }

        return resubmissionEvent(application).flatMap(event -> {
            String customerMessage = customerMessage(application, event);
            List<KycMissingRequirement> requiredCorrections =
                    requiredCorrections(event);
            // Older records can predate this contract. Do not turn an
            // incomplete audit entry into a guessed customer instruction set.
            if (customerMessage == null || customerMessage.isBlank()
                    || requiredCorrections.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new KycResubmissionResponse(
                    customerMessage,
                    requiredCorrections,
                    completedCorrections(application, event)
            ));
        });
    }

    public boolean permitsEvidenceChange(
            KycApplication application,
            KycEvidenceType evidenceType,
            KycDocumentSide documentSide
    ) {
        if (application.getStatus() != KycStatus.RESUBMISSION_REQUIRED) {
            return true;
        }

        return resubmissionEvent(application)
                .map(this::requiredCorrections)
                .map(requirements -> requirements.contains(
                        correctionFor(evidenceType, documentSide)
                ))
                .orElse(false);
    }

    private Optional<KycReviewEvent> resubmissionEvent(
            KycApplication application
    ) {
        return reviewEventRepository
                .findFirstByApplication_IdAndActionOrderByCreatedAtDesc(
                        application.getId(),
                        KycReviewAction.RESUBMISSION_REQUESTED
                );
    }

    private List<KycMissingRequirement> requiredCorrections(
            KycReviewEvent event
    ) {
        EnumSet<KycMissingRequirement> corrections = EnumSet.noneOf(
                KycMissingRequirement.class
        );
        event.getMissingRequirements().forEach(requirement -> corrections.add(
                requirement.getRequirement()
        ));
        event.getEvidenceReferences().stream()
                .map(KycReviewEventEvidence::getEvidence)
                .map(evidence -> correctionFor(
                        evidence.getEvidenceType(),
                        evidence.getDocumentSide()
                ))
                .forEach(corrections::add);

        return corrections.stream()
                .sorted(Comparator.comparing(KycMissingRequirement::name))
                .toList();
    }

    public boolean areAllCorrectionsComplete(KycApplication application) {
        return resubmissionEvent(application)
                .map(event -> {
                    List<KycMissingRequirement> required = requiredCorrections(
                            event
                    );
                    return !required.isEmpty() && completedCorrections(
                            application,
                            event
                    ).containsAll(required);
                })
                .orElse(false);
    }

    private List<KycMissingRequirement> completedCorrections(
            KycApplication application,
            KycReviewEvent event
    ) {
        EnumSet<KycMissingRequirement> required = EnumSet.noneOf(
                KycMissingRequirement.class
        );
        required.addAll(requiredCorrections(event));
        EnumSet<KycMissingRequirement> completed = EnumSet.noneOf(
                KycMissingRequirement.class
        );

        if (required.contains(KycMissingRequirement.PROFILE_INFORMATION)
                && application.getCustomer().getUpdatedAt() != null
                && application.getCustomer().getUpdatedAt().isAfter(
                event.getCreatedAt()
        )) {
            completed.add(KycMissingRequirement.PROFILE_INFORMATION);
        }

        application.getDocuments().stream()
                .filter(KycDocument::isActive)
                .filter(document -> document.getUploadStatus()
                        == KycEvidenceUploadStatus.VALIDATED)
                .filter(document -> document.getCreatedAt() != null
                        && document.getCreatedAt().isAfter(event.getCreatedAt()))
                .map(document -> correctionFor(
                        document.getEvidenceType(),
                        document.getDocumentSide()
                ))
                .filter(required::contains)
                .forEach(completed::add);

        return completed.stream()
                .sorted(Comparator.comparing(KycMissingRequirement::name))
                .toList();
    }

    private KycMissingRequirement correctionFor(
            KycEvidenceType evidenceType,
            KycDocumentSide documentSide
    ) {
        if (evidenceType == KycEvidenceType.SELFIE) {
            return KycMissingRequirement.SELFIE;
        }

        return switch (documentSide) {
            case FRONT -> KycMissingRequirement.DOCUMENT_FRONT;
            case BACK -> KycMissingRequirement.DOCUMENT_BACK;
            case PHOTO_PAGE -> KycMissingRequirement.DOCUMENT_PHOTO_PAGE;
            case NOT_APPLICABLE -> throw new IllegalArgumentException(
                    "Document evidence must have a document side."
            );
        };
    }

    private String customerMessage(
            KycApplication application,
            KycReviewEvent event
    ) {
        String message = event.getCustomerMessage();
        return message == null || message.isBlank()
                ? application.getRejectionReason()
                : message;
    }
}
