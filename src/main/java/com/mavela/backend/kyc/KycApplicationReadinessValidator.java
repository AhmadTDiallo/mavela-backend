package com.mavela.backend.kyc;

import com.mavela.backend.customer.Customer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative required-profile and evidence policy. Keeping the
 * policy in one place prevents a customer submission from being approved by a
 * weaker staff-only check later in the lifecycle.
 */
@Component
public class KycApplicationReadinessValidator {

    public List<KycDocument> requireReadyForSubmission(
            Customer customer,
            KycApplication application,
            List<KycDocument> documents
    ) {
        if (!customer.isProfileComplete()) {
            throw failure(
                    KycApplicationReadinessException.Reason.PROFILE_INCOMPLETE,
                    KycDraftStep.COMPLETE_INFORMATION
            );
        }

        if (application.getDocumentType() == null) {
            throw failure(
                    KycApplicationReadinessException.Reason.SUBMISSION_INCOMPLETE,
                    KycDraftStep.SELECT_DOCUMENT
            );
        }

        List<KycDocument> requiredEvidence = new ArrayList<>();
        if (application.getDocumentType() == KycDocumentType.PASSPORT) {
            requiredEvidence.add(findValidatedEvidence(
                    documents,
                    KycEvidenceType.DOCUMENT,
                    KycDocumentSide.PHOTO_PAGE,
                    KycDraftStep.DOCUMENT_FRONT,
                    application.getDocumentType()
            ));
        } else {
            requiredEvidence.add(findValidatedEvidence(
                    documents,
                    KycEvidenceType.DOCUMENT,
                    KycDocumentSide.FRONT,
                    KycDraftStep.DOCUMENT_FRONT,
                    application.getDocumentType()
            ));
            requiredEvidence.add(findValidatedEvidence(
                    documents,
                    KycEvidenceType.DOCUMENT,
                    KycDocumentSide.BACK,
                    KycDraftStep.DOCUMENT_BACK,
                    application.getDocumentType()
            ));
        }

        requiredEvidence.add(findValidatedEvidence(
                documents,
                KycEvidenceType.SELFIE,
                KycDocumentSide.NOT_APPLICABLE,
                KycDraftStep.SELFIE,
                null,
                KycCaptureMethod.CAMERA_CAPTURE
        ));
        return List.copyOf(requiredEvidence);
    }

    private KycDocument findValidatedEvidence(
            List<KycDocument> documents,
            KycEvidenceType evidenceType,
            KycDocumentSide documentSide,
            KycDraftStep step,
            KycDocumentType requiredDocumentType
    ) {
        return findValidatedEvidence(
                documents,
                evidenceType,
                documentSide,
                step,
                requiredDocumentType,
                null
        );
    }

    private KycDocument findValidatedEvidence(
            List<KycDocument> documents,
            KycEvidenceType evidenceType,
            KycDocumentSide documentSide,
            KycDraftStep step,
            KycDocumentType requiredDocumentType,
            KycCaptureMethod requiredCaptureMethod
    ) {
        return documents.stream()
                .filter(document -> document.isActive()
                        && document.getEvidenceType() == evidenceType
                        && document.getDocumentSide() == documentSide
                        && document.getDocumentType() == requiredDocumentType
                        && document.getUploadStatus()
                        == KycEvidenceUploadStatus.VALIDATED
                        && (requiredCaptureMethod == null
                        || document.getCaptureMethod()
                        == requiredCaptureMethod))
                .findFirst()
                .orElseThrow(() -> failure(
                        KycApplicationReadinessException.Reason.SUBMISSION_INCOMPLETE,
                        step
                ));
    }

    private KycApplicationReadinessException failure(
            KycApplicationReadinessException.Reason reason,
            KycDraftStep step
    ) {
        return new KycApplicationReadinessException(reason, step);
    }
}
