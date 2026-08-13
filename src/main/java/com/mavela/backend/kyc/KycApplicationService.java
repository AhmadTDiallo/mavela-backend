package com.mavela.backend.kyc;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.error.ApiErrorCode;
import com.mavela.backend.kyc.storage.KycEvidenceStorage;
import com.mavela.backend.kyc.storage.KycEvidenceStorageException;
import com.mavela.backend.kyc.storage.KycEvidenceStorageProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Server-authoritative customer KYC draft and evidence workflow. Every public
 * operation scopes its lookup through the authenticated customer's UUID.
 */
@Service
public class KycApplicationService {

    private static final long DEFAULT_MAX_IMAGE_SIZE_BYTES = 10_485_760;

    private final CustomerRepository customerRepository;
    private final KycApplicationRepository applicationRepository;
    private final KycDocumentRepository documentRepository;
    private final KycEvidenceStorage evidenceStorage;
    private final KycEvidenceStorageProperties storageProperties;

    public KycApplicationService(
            CustomerRepository customerRepository,
            KycApplicationRepository applicationRepository,
            KycDocumentRepository documentRepository,
            KycEvidenceStorage evidenceStorage,
            KycEvidenceStorageProperties storageProperties
    ) {
        this.customerRepository = customerRepository;
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.evidenceStorage = evidenceStorage;
        this.storageProperties = storageProperties;
    }

    /**
     * Creates a resumable draft. Profile completion belongs to final
     * submission, not draft creation.
     */
    @Transactional
    public KycApplicationResponse startApplication(
            UUID authenticatedCustomerId
    ) {
        Customer customer = customerRepository
                .findByIdForUpdate(authenticatedCustomerId)
                .orElseThrow(this::authenticatedCustomerNotFound);

        if (applicationRepository.existsByCustomer_Id(authenticatedCustomerId)) {
            throw workflowException(ApiErrorCode.KYC_APPLICATION_ALREADY_EXISTS);
        }

        if (customer.getKycStatus() != KycStatus.NOT_STARTED) {
            throw workflowException(ApiErrorCode.KYC_START_NOT_ALLOWED);
        }

        KycApplication application = new KycApplication(customer, 1);
        customer.startKycApplication();

        try {
            KycApplication savedApplication = applicationRepository
                    .saveAndFlush(application);
            return KycApplicationResponse.from(savedApplication);
        } catch (DataIntegrityViolationException exception) {
            /*
             * The unique database constraints guard the application creation
             * path if a concurrent request wins the race.
             */
            throw workflowException(ApiErrorCode.KYC_APPLICATION_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public KycApplicationResponse getCurrentApplication(
            UUID authenticatedCustomerId
    ) {
        customerRepository.findById(authenticatedCustomerId)
                .orElseThrow(this::authenticatedCustomerNotFound);

        return KycApplicationResponse.from(
                currentApplicationForCustomer(authenticatedCustomerId)
        );
    }

    @Transactional
    public KycApplicationResponse updateDraft(
            UUID authenticatedCustomerId,
            UpdateKycApplicationDraftRequest request
    ) {
        customerRepository.findById(authenticatedCustomerId)
                .orElseThrow(this::authenticatedCustomerNotFound);

        KycApplication application = currentApplicationForCustomer(
                authenticatedCustomerId
        );
        requireEditable(application);

        application.updateDraft(request.currentStep(), request.documentType());
        return KycApplicationResponse.from(
                applicationRepository.saveAndFlush(application)
        );
    }

    @Transactional
    public KycEvidenceUploadSessionResponse requestEvidenceUpload(
            UUID authenticatedCustomerId,
            RequestKycEvidenceUploadRequest request
    ) {
        customerRepository.findById(authenticatedCustomerId)
                .orElseThrow(this::authenticatedCustomerNotFound);

        KycApplication application = currentApplicationForCustomer(
                authenticatedCustomerId
        );
        requireEditable(application);

        validateEvidenceRequest(application, request);

        List<KycDocument> existingSlotDocuments = documentRepository
                .findAllByApplication_IdAndEvidenceTypeAndDocumentSideAndDeletedAtIsNull(
                        application.getId(),
                        request.evidenceType(),
                        request.documentSide()
                );
        for (KycDocument existing : existingSlotDocuments) {
            if (existing.isActive()) {
                removeEvidenceFromStorage(existing);
                existing.markRemoved(Instant.now());
            }
        }
        /*
         * Flush removals before inserting the replacement. PostgreSQL's
         * partial unique index otherwise observes both active slot rows in a
         * single Hibernate flush, where inserts may precede updates.
         */
        if (!existingSlotDocuments.isEmpty()) {
            documentRepository.saveAllAndFlush(existingSlotDocuments);
        }

        String mimeType = normalizeMimeType(request.mimeType());
        String sha256 = normalizeChecksum(request.sha256());
        String storageKey = newStorageKey(
                authenticatedCustomerId,
                application.getId()
        );
        KycDocument evidence = new KycDocument(
                application,
                request.evidenceType(),
                request.documentType(),
                request.documentSide(),
                request.captureMethod(),
                storageKey,
                mimeType,
                request.byteSize(),
                sha256
        );

        KycDocument savedEvidence = documentRepository.saveAndFlush(evidence);
        application.recordEvidenceChange(Instant.now());
        applicationRepository.saveAndFlush(application);
        KycEvidenceStorage.UploadSession session;
        try {
            session = evidenceStorage.requestUpload(
                    new KycEvidenceStorage.UploadRequest(
                            storageKey,
                            mimeType,
                            request.byteSize(),
                            sha256
                    )
            );
        } catch (KycEvidenceStorageException exception) {
            throw workflowException(ApiErrorCode.KYC_EVIDENCE_UPLOAD_FAILED);
        }

        return new KycEvidenceUploadSessionResponse(
                savedEvidence.getId(),
                session.uploadUrl(),
                session.requiredHeaders(),
                session.expiresAt(),
                maximumImageSizeBytes()
        );
    }

    @Transactional(noRollbackFor = KycWorkflowException.class)
    public KycApplicationResponse completeEvidenceUpload(
            UUID authenticatedCustomerId,
            UUID evidenceId,
            CompleteKycEvidenceUploadRequest request
    ) {
        KycDocument evidence = currentEvidenceForCustomer(
                authenticatedCustomerId,
                evidenceId
        );
        KycApplication application = evidence.getApplication();
        requireEditable(application);

        if (evidence.getUploadStatus() != KycEvidenceUploadStatus.REQUESTED
                || !evidence.isActive()) {
            throw workflowException(ApiErrorCode.KYC_EVIDENCE_UPLOAD_NOT_ALLOWED);
        }

        String suppliedChecksum = normalizeChecksum(request.sha256());
        if (!evidence.getSha256Checksum().equals(suppliedChecksum)) {
            evidence.markUploadFailed();
            documentRepository.saveAndFlush(evidence);
            throw workflowException(
                    ApiErrorCode.KYC_EVIDENCE_INVALID,
                    "UPLOAD"
            );
        }

        try {
            KycEvidenceStorage.VerifiedObject verified = evidenceStorage
                    .verifyUpload(new KycEvidenceStorage.VerificationRequest(
                            evidence.getStorageKey(),
                            evidence.getMimeType(),
                            evidence.getFileSize(),
                            evidence.getSha256Checksum()
                    ));

            if (!evidence.getMimeType().equals(normalizeMimeType(
                    verified.mimeType()
            )) || evidence.getFileSize() != verified.byteSize()
                    || !evidence.getSha256Checksum().equals(
                    normalizeChecksum(verified.sha256Checksum())
            )) {
                evidence.markUploadFailed();
                documentRepository.saveAndFlush(evidence);
                throw workflowException(
                        ApiErrorCode.KYC_EVIDENCE_INVALID,
                        "UPLOAD"
                );
            }
        } catch (KycEvidenceStorageException exception) {
            evidence.markUploadFailed();
            documentRepository.saveAndFlush(evidence);
            throw workflowException(
                    ApiErrorCode.KYC_EVIDENCE_UPLOAD_FAILED,
                    "UPLOAD"
            );
        }

        Instant now = Instant.now();
        evidence.markUploadVerified(now);
        application.recordEvidenceChange(now);
        documentRepository.saveAndFlush(evidence);
        applicationRepository.saveAndFlush(application);
        return KycApplicationResponse.from(application);
    }

    @Transactional
    public void deleteEvidence(
            UUID authenticatedCustomerId,
            UUID evidenceId
    ) {
        KycDocument evidence = currentEvidenceForCustomer(
                authenticatedCustomerId,
                evidenceId
        );
        requireEditable(evidence.getApplication());

        removeEvidenceFromStorage(evidence);
        Instant now = Instant.now();
        evidence.markRemoved(now);
        evidence.getApplication().recordEvidenceChange(now);
        documentRepository.saveAndFlush(evidence);
        applicationRepository.saveAndFlush(evidence.getApplication());
    }

    @Transactional
    public KycApplicationResponse submitCurrentApplication(
            UUID authenticatedCustomerId
    ) {
        Customer customer = customerRepository
                .findByIdForUpdate(authenticatedCustomerId)
                .orElseThrow(this::authenticatedCustomerNotFound);
        KycApplication application = currentApplicationForCustomer(
                authenticatedCustomerId
        );

        if (application.getStatus() == KycStatus.SUBMITTED) {
            return KycApplicationResponse.from(application);
        }

        if (!application.isEditable()) {
            throw workflowException(ApiErrorCode.KYC_SUBMISSION_NOT_ALLOWED);
        }

        if (!customer.isProfileComplete()) {
            throw workflowException(
                    ApiErrorCode.KYC_PROFILE_INCOMPLETE,
                    KycDraftStep.COMPLETE_INFORMATION.name()
            );
        }

        if (application.getDocumentType() == null) {
            throw workflowException(
                    ApiErrorCode.KYC_SUBMISSION_INCOMPLETE,
                    KycDraftStep.SELECT_DOCUMENT.name()
            );
        }

        List<KycDocument> documents = documentRepository
                .findAllByApplication_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        application.getId()
                );
        requireMandatoryEvidence(application.getDocumentType(), documents);

        Instant now = Instant.now();
        application.captureProfileSnapshot(customer);
        application.submit(now);
        customer.submitKycApplication();

        return KycApplicationResponse.from(
                applicationRepository.saveAndFlush(application)
        );
    }

    private KycApplication currentApplicationForCustomer(UUID customerId) {
        return applicationRepository
                .findFirstByCustomer_IdOrderByAttemptNumberDesc(customerId)
                .orElseThrow(() -> workflowException(
                        ApiErrorCode.KYC_APPLICATION_NOT_FOUND
                ));
    }

    private KycDocument currentEvidenceForCustomer(
            UUID customerId,
            UUID evidenceId
    ) {
        customerRepository.findById(customerId)
                .orElseThrow(this::authenticatedCustomerNotFound);

        return documentRepository
                .findByIdAndApplication_Customer_IdAndDeletedAtIsNull(
                        evidenceId,
                        customerId
                )
                .orElseThrow(() -> workflowException(
                        ApiErrorCode.KYC_EVIDENCE_NOT_FOUND
                ));
    }

    private void validateEvidenceRequest(
            KycApplication application,
            RequestKycEvidenceUploadRequest request
    ) {
        if (request.byteSize() <= 0
                || request.byteSize() > maximumImageSizeBytes()
                || !isSupportedImageMimeType(request.mimeType())
                || !isSha256(request.sha256())) {
            throw workflowException(
                    ApiErrorCode.KYC_EVIDENCE_INVALID,
                    "UPLOAD"
            );
        }

        if (request.evidenceType() == KycEvidenceType.SELFIE) {
            if (request.documentType() != null
                    || request.documentSide()
                    != KycDocumentSide.NOT_APPLICABLE
                    || request.captureMethod()
                    != KycCaptureMethod.CAMERA_CAPTURE) {
                throw workflowException(
                        ApiErrorCode.KYC_EVIDENCE_INVALID,
                        KycDraftStep.SELFIE.name()
                );
            }
            return;
        }

        if (request.evidenceType() != KycEvidenceType.DOCUMENT
                || request.documentType() == null
                || application.getDocumentType() == null
                || request.documentType() != application.getDocumentType()
                || request.documentSide() == KycDocumentSide.NOT_APPLICABLE) {
            throw workflowException(
                    ApiErrorCode.KYC_EVIDENCE_INVALID,
                    KycDraftStep.SELECT_DOCUMENT.name()
            );
        }

        if (request.documentType() == KycDocumentType.PASSPORT
                && request.documentSide() != KycDocumentSide.PHOTO_PAGE) {
            throw workflowException(
                    ApiErrorCode.KYC_EVIDENCE_INVALID,
                    KycDraftStep.DOCUMENT_FRONT.name()
            );
        }

        if (request.documentType() != KycDocumentType.PASSPORT
                && request.documentSide() != KycDocumentSide.FRONT
                && request.documentSide() != KycDocumentSide.BACK) {
            throw workflowException(
                    ApiErrorCode.KYC_EVIDENCE_INVALID,
                    KycDraftStep.DOCUMENT_FRONT.name()
            );
        }
    }

    private void requireMandatoryEvidence(
            KycDocumentType documentType,
            List<KycDocument> documents
    ) {
        if (documentType == KycDocumentType.PASSPORT) {
            requireValidatedEvidence(
                    documents,
                    KycEvidenceType.DOCUMENT,
                    KycDocumentSide.PHOTO_PAGE,
                    KycDraftStep.DOCUMENT_FRONT
            );
        } else {
            requireValidatedEvidence(
                    documents,
                    KycEvidenceType.DOCUMENT,
                    KycDocumentSide.FRONT,
                    KycDraftStep.DOCUMENT_FRONT
            );
            requireValidatedEvidence(
                    documents,
                    KycEvidenceType.DOCUMENT,
                    KycDocumentSide.BACK,
                    KycDraftStep.DOCUMENT_BACK
            );
        }

        requireValidatedEvidence(
                documents,
                KycEvidenceType.SELFIE,
                KycDocumentSide.NOT_APPLICABLE,
                KycDraftStep.SELFIE
        );
    }

    private void requireValidatedEvidence(
            List<KycDocument> documents,
            KycEvidenceType evidenceType,
            KycDocumentSide documentSide,
            KycDraftStep step
    ) {
        boolean present = documents.stream().anyMatch(document ->
                document.isActive()
                        && document.getEvidenceType() == evidenceType
                        && document.getDocumentSide() == documentSide
                        && document.getUploadStatus()
                        == KycEvidenceUploadStatus.VALIDATED
        );

        if (!present) {
            throw workflowException(
                    ApiErrorCode.KYC_SUBMISSION_INCOMPLETE,
                    step.name()
            );
        }
    }

    private void requireEditable(KycApplication application) {
        if (!application.isEditable()) {
            throw workflowException(ApiErrorCode.KYC_DRAFT_NOT_EDITABLE);
        }
    }

    private void removeEvidenceFromStorage(KycDocument evidence) {
        try {
            evidenceStorage.delete(evidence.getStorageKey());
        } catch (KycEvidenceStorageException exception) {
            throw workflowException(ApiErrorCode.KYC_EVIDENCE_UPLOAD_FAILED);
        }
    }

    private String newStorageKey(UUID customerId, UUID applicationId) {
        return "kyc/" + customerId + "/" + applicationId + "/"
                + UUID.randomUUID();
    }

    private long maximumImageSizeBytes() {
        long configured = storageProperties.getMaxImageSizeBytes();
        if (configured <= 0) {
            return DEFAULT_MAX_IMAGE_SIZE_BYTES;
        }
        return Math.min(configured, DEFAULT_MAX_IMAGE_SIZE_BYTES);
    }

    private String normalizeMimeType(String value) {
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeChecksum(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isSupportedImageMimeType(String value) {
        if (value == null) {
            return false;
        }

        String normalized = normalizeMimeType(value);
        return "image/jpeg".equals(normalized)
                || "image/png".equals(normalized);
    }

    private boolean isSha256(String value) {
        return value != null && value.matches("(?i)^[a-f0-9]{64}$");
    }

    private ResponseStatusException authenticatedCustomerNotFound() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATED_CUSTOMER_NOT_FOUND"
        );
    }

    private KycWorkflowException workflowException(ApiErrorCode code) {
        return workflowException(code, null);
    }

    private KycWorkflowException workflowException(
            ApiErrorCode code,
            String step
    ) {
        return switch (code) {
            case KYC_APPLICATION_NOT_FOUND,
                 KYC_EVIDENCE_NOT_FOUND -> new KycWorkflowException(
                    code,
                    HttpStatus.NOT_FOUND,
                    step
            );
            case KYC_EVIDENCE_UPLOAD_FAILED -> new KycWorkflowException(
                    code,
                    HttpStatus.BAD_GATEWAY,
                    step
            );
            case KYC_EVIDENCE_INVALID -> new KycWorkflowException(
                    code,
                    HttpStatus.BAD_REQUEST,
                    step
            );
            case KYC_PROFILE_INCOMPLETE,
                 KYC_APPLICATION_ALREADY_EXISTS,
                 KYC_START_NOT_ALLOWED,
                 KYC_DRAFT_NOT_EDITABLE,
                 KYC_EVIDENCE_UPLOAD_NOT_ALLOWED,
                 KYC_SUBMISSION_INCOMPLETE,
                 KYC_SUBMISSION_NOT_ALLOWED -> new KycWorkflowException(
                    code,
                    HttpStatus.CONFLICT,
                    step
            );
            default -> throw new IllegalArgumentException(
                    "Unsupported KYC workflow error code."
            );
        };
    }
}
