package com.mavela.backend.kyc.review;

import com.mavela.backend.admin.auth.AdminPermission;
import com.mavela.backend.admin.staff.StaffUser;
import com.mavela.backend.admin.staff.StaffUserService;
import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.error.ApiErrorCode;
import com.mavela.backend.kyc.KycApplication;
import com.mavela.backend.kyc.KycApplicationReadinessException;
import com.mavela.backend.kyc.KycApplicationReadinessValidator;
import com.mavela.backend.kyc.KycApplicationRepository;
import com.mavela.backend.kyc.KycDocument;
import com.mavela.backend.kyc.KycDocumentRepository;
import com.mavela.backend.kyc.KycEvidenceUploadStatus;
import com.mavela.backend.kyc.storage.KycEvidenceStorage;
import com.mavela.backend.kyc.storage.KycEvidenceStorageException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Staff-only commands for reviewing an already submitted KYC application.
 * Identity is always resolved from the validated OIDC subject, while each
 * status transition remains server-owned and transactional.
 */
@Service
public class KycAdminReviewService {

    private static final int MAX_QUEUE_SIZE = 100;
    private static final Set<KycStatus> STAFF_VISIBLE_APPLICATION_STATUSES =
            Set.of(
                    KycStatus.SUBMITTED,
                    KycStatus.UNDER_REVIEW,
                    KycStatus.APPROVED,
                    KycStatus.REJECTED,
                    KycStatus.RESUBMISSION_REQUIRED
            );

    private final StaffUserService staffUserService;
    private final KycApplicationRepository applicationRepository;
    private final KycDocumentRepository documentRepository;
    private final KycReviewEventRepository eventRepository;
    private final KycApplicationReadinessValidator readinessValidator;
    private final KycEvidenceStorage evidenceStorage;

    public KycAdminReviewService(
            StaffUserService staffUserService,
            KycApplicationRepository applicationRepository,
            KycDocumentRepository documentRepository,
            KycReviewEventRepository eventRepository,
            KycApplicationReadinessValidator readinessValidator,
            KycEvidenceStorage evidenceStorage
    ) {
        this.staffUserService = staffUserService;
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.eventRepository = eventRepository;
        this.readinessValidator = readinessValidator;
        this.evidenceStorage = evidenceStorage;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('kyc:read')")
    public AdminKycQueueResponse findQueue(
            String externalSubject,
            KycStatus status,
            UUID assignedReviewerId,
            LocalDate submittedFrom,
            LocalDate submittedTo,
            int page,
            int size,
            String sort
    ) {
        StaffUser staff = activeStaff(externalSubject);
        boolean supervisor = hasPermission(AdminPermission.KYC_SUPERVISE);
        if (assignedReviewerId != null && !supervisor
                && !assignedReviewerId.equals(staff.getId())) {
            throw reviewException(ApiErrorCode.ADMIN_PERMISSION_DENIED,
                    HttpStatus.FORBIDDEN);
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_QUEUE_SIZE),
                queueSort(sort)
        );
        Specification<KycApplication> specification = queueSpecification(
                status,
                assignedReviewerId,
                submittedFrom,
                submittedTo,
                supervisor ? null : staff.getId()
        );
        Page<KycApplication> results = applicationRepository.findAll(
                specification,
                pageable
        );

        return new AdminKycQueueResponse(
                results.getContent().stream()
                        .map(AdminKycQueueItemResponse::from)
                        .toList(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages()
        );
    }

    @Transactional
    @PreAuthorize("hasAuthority('kyc:read')")
    public AdminKycApplicationDetailResponse getApplicationDetail(
            String externalSubject,
            UUID applicationId,
            UUID correlationId
    ) {
        StaffUser staff = activeStaff(externalSubject);
        KycApplication application = application(applicationId);
        requireStaffVisible(application);
        requireAssignedOrSupervisor(application, staff);
        appendEvent(
                application,
                staff,
                KycReviewAction.APPLICATION_VIEWED,
                application.getStatus(),
                application.getStatus(),
                null,
                null,
                null,
                List.of(),
                List.of(),
                correlationId
        );
        return detail(application);
    }

    @Transactional
    @PreAuthorize("hasAuthority('kyc:claim')")
    public AdminKycApplicationDetailResponse claim(
            String externalSubject,
            UUID applicationId,
            UUID correlationId
    ) {
        StaffUser staff = activeStaff(externalSubject);
        KycApplication application = application(applicationId);
        KycStatus previousStatus = application.getStatus();
        if (previousStatus != KycStatus.SUBMITTED) {
            throw reviewException(
                    application.getAssignedReviewer() == null
                            ? ApiErrorCode.KYC_APPLICATION_NOT_REVIEWABLE
                            : ApiErrorCode.KYC_APPLICATION_ALREADY_CLAIMED,
                    HttpStatus.CONFLICT
            );
        }

        try {
            Instant now = Instant.now();
            application.claim(staff, now);
            application.getCustomer().beginKycReview();
            applicationRepository.saveAndFlush(application);
            appendEvent(
                    application,
                    staff,
                    KycReviewAction.CLAIMED,
                    previousStatus,
                    application.getStatus(),
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    correlationId
            );
            return detail(application);
        } catch (OptimisticLockingFailureException exception) {
            throw reviewException(
                    ApiErrorCode.KYC_APPLICATION_ALREADY_CLAIMED,
                    HttpStatus.CONFLICT
            );
        } catch (IllegalStateException exception) {
            throw reviewException(
                    ApiErrorCode.KYC_INVALID_REVIEW_TRANSITION,
                    HttpStatus.CONFLICT
            );
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('kyc:supervise')")
    public AdminKycApplicationDetailResponse release(
            String externalSubject,
            UUID applicationId,
            ReleaseKycApplicationRequest request,
            UUID correlationId
    ) {
        StaffUser staff = activeStaff(externalSubject);
        KycApplication application = application(applicationId);
        requireExpectedVersion(application, request.expectedVersion());
        KycStatus previousStatus = application.getStatus();
        if (previousStatus != KycStatus.UNDER_REVIEW
                || application.getAssignedReviewer() == null) {
            throw reviewException(
                    ApiErrorCode.KYC_APPLICATION_NOT_REVIEWABLE,
                    HttpStatus.CONFLICT
            );
        }

        try {
            application.release(Instant.now());
            application.getCustomer().releaseKycReview();
            applicationRepository.saveAndFlush(application);
            appendEvent(
                    application,
                    staff,
                    KycReviewAction.RELEASED,
                    previousStatus,
                    application.getStatus(),
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    correlationId
            );
            return detail(application);
        } catch (OptimisticLockingFailureException exception) {
            throw staleVersion();
        } catch (IllegalStateException exception) {
            throw reviewException(
                    ApiErrorCode.KYC_INVALID_REVIEW_TRANSITION,
                    HttpStatus.CONFLICT
            );
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('kyc:decide')")
    public AdminKycApplicationDetailResponse approve(
            String externalSubject,
            UUID applicationId,
            ApproveKycApplicationRequest request,
            UUID correlationId
    ) {
        StaffUser staff = activeStaff(externalSubject);
        KycApplication application = application(applicationId);
        requireExpectedVersion(application, request.expectedVersion());
        requireAssignedDecision(application, staff);
        revalidateApprovalReadiness(application);

        KycStatus previousStatus = application.getStatus();
        try {
            Instant now = Instant.now();
            application.approve(now);
            application.getCustomer().approveKycApplication();
            applicationRepository.saveAndFlush(application);
            appendEvent(
                    application,
                    staff,
                    KycReviewAction.APPROVED,
                    previousStatus,
                    application.getStatus(),
                    null,
                    null,
                    trimmed(request.internalNotes()),
                    List.of(),
                    List.of(),
                    correlationId
            );
            return detail(application);
        } catch (OptimisticLockingFailureException exception) {
            throw staleVersion();
        } catch (IllegalStateException exception) {
            throw reviewException(
                    ApiErrorCode.KYC_INVALID_REVIEW_TRANSITION,
                    HttpStatus.CONFLICT
            );
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('kyc:decide')")
    public AdminKycApplicationDetailResponse requestResubmission(
            String externalSubject,
            UUID applicationId,
            RequestKycResubmissionRequest request,
            UUID correlationId
    ) {
        StaffUser staff = activeStaff(externalSubject);
        KycApplication application = application(applicationId);
        requireExpectedVersion(application, request.expectedVersion());
        requireAssignedDecision(application, staff);
        if (request.reasonCode().isTerminal()) {
            throw reviewException(
                    ApiErrorCode.KYC_INVALID_REVIEW_TRANSITION,
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        String customerMessage = requiredCustomerMessage(
                request.customerMessage()
        );
        List<KycDocument> evidence = resolveApplicationEvidence(
                application,
                request.evidenceIds()
        );
        List<KycMissingRequirement> missingRequirements = requiredCorrections(
                request.missingRequirements()
        );
        KycStatus previousStatus = application.getStatus();
        try {
            Instant now = Instant.now();
            application.requestResubmission(
                    customerMessage,
                    now
            );
            application.getCustomer().requireKycResubmission();
            applicationRepository.saveAndFlush(application);
            appendEvent(
                    application,
                    staff,
                    KycReviewAction.RESUBMISSION_REQUESTED,
                    previousStatus,
                    application.getStatus(),
                    request.reasonCode(),
                    customerMessage,
                    trimmed(request.internalNotes()),
                    evidence,
                    missingRequirements,
                    correlationId
            );
            return detail(application);
        } catch (OptimisticLockingFailureException exception) {
            throw staleVersion();
        } catch (IllegalStateException exception) {
            throw reviewException(
                    ApiErrorCode.KYC_INVALID_REVIEW_TRANSITION,
                    HttpStatus.CONFLICT
            );
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('kyc:decide')")
    public AdminKycApplicationDetailResponse reject(
            String externalSubject,
            UUID applicationId,
            RejectKycApplicationRequest request,
            UUID correlationId
    ) {
        StaffUser staff = activeStaff(externalSubject);
        KycApplication application = application(applicationId);
        requireExpectedVersion(application, request.expectedVersion());
        requireAssignedDecision(application, staff);
        if (!request.reasonCode().isTerminal()) {
            throw reviewException(
                    ApiErrorCode.KYC_INVALID_REVIEW_TRANSITION,
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        KycStatus previousStatus = application.getStatus();
        try {
            Instant now = Instant.now();
            application.reject(trimmed(request.customerMessage()), now);
            application.getCustomer().rejectKycApplication();
            applicationRepository.saveAndFlush(application);
            appendEvent(
                    application,
                    staff,
                    KycReviewAction.REJECTED,
                    previousStatus,
                    application.getStatus(),
                    request.reasonCode(),
                    trimmed(request.customerMessage()),
                    trimmed(request.internalNotes()),
                    List.of(),
                    List.of(),
                    correlationId
            );
            return detail(application);
        } catch (OptimisticLockingFailureException exception) {
            throw staleVersion();
        } catch (IllegalStateException exception) {
            throw reviewException(
                    ApiErrorCode.KYC_INVALID_REVIEW_TRANSITION,
                    HttpStatus.CONFLICT
            );
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('kyc:read')")
    public List<AdminKycReviewEventResponse> history(
            String externalSubject,
            UUID applicationId
    ) {
        StaffUser staff = activeStaff(externalSubject);
        KycApplication application = application(applicationId);
        requireStaffVisible(application);
        requireAssignedOrSupervisor(application, staff);
        return eventRepository.findAllByApplication_IdOrderByCreatedAtAsc(
                        applicationId
                ).stream()
                .map(AdminKycReviewEventResponse::from)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('kyc:read')")
    public KycEvidenceStorage.EvidenceStream openEvidence(
            String externalSubject,
            UUID applicationId,
            UUID evidenceId,
            UUID correlationId
    ) {
        StaffUser staff = activeStaff(externalSubject);
        KycApplication application = application(applicationId);
        requireStaffVisible(application);
        requireAssignedOrSupervisor(application, staff);
        KycDocument evidence = documentRepository
                .findByIdAndApplication_IdAndDeletedAtIsNull(
                        evidenceId,
                        applicationId
                ).orElseThrow(() -> reviewException(
                        ApiErrorCode.KYC_EVIDENCE_NOT_PART_OF_APPLICATION,
                        HttpStatus.NOT_FOUND
                ));
        if (!evidence.isActive()
                || evidence.getUploadStatus()
                != KycEvidenceUploadStatus.VALIDATED
                || !isAllowedImage(evidence.getMimeType())) {
            throw reviewException(
                    ApiErrorCode.KYC_EVIDENCE_NOT_PART_OF_APPLICATION,
                    HttpStatus.NOT_FOUND
            );
        }

        KycEvidenceStorage.EvidenceStream stream;
        try {
            stream = evidenceStorage.openRead(new KycEvidenceStorage.ReadRequest(
                    evidence.getStorageKey(),
                    evidence.getMimeType(),
                    evidence.getFileSize()
            ));
        } catch (KycEvidenceStorageException exception) {
            throw reviewException(
                    ApiErrorCode.KYC_EVIDENCE_STREAM_FAILED,
                    HttpStatus.BAD_GATEWAY
            );
        }

        appendEvent(
                application,
                staff,
                KycReviewAction.EVIDENCE_VIEWED,
                application.getStatus(),
                application.getStatus(),
                null,
                null,
                null,
                List.of(evidence),
                List.of(),
                correlationId
        );
        return stream;
    }

    private void revalidateApprovalReadiness(KycApplication application) {
        Customer customer = application.getCustomer();
        if (!customer.isProfileComplete()
                || !application.hasCompleteProfileSnapshot()) {
            throw reviewException(
                    ApiErrorCode.KYC_APPLICATION_NOT_REVIEWABLE,
                    HttpStatus.CONFLICT
            );
        }

        List<KycDocument> documents = documentRepository
                .findAllByApplication_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        application.getId()
                );
        List<KycDocument> requiredEvidence;
        try {
            requiredEvidence = readinessValidator.requireReadyForSubmission(
                    customer,
                    application,
                    documents
            );
        } catch (KycApplicationReadinessException exception) {
            throw reviewException(
                    ApiErrorCode.KYC_APPLICATION_NOT_REVIEWABLE,
                    HttpStatus.CONFLICT
            );
        }

        for (KycDocument evidence : requiredEvidence) {
            try {
                evidenceStorage.verifyUpload(
                        new KycEvidenceStorage.VerificationRequest(
                                evidence.getStorageKey(),
                                evidence.getMimeType(),
                                evidence.getFileSize(),
                                evidence.getSha256Checksum()
                        )
                );
            } catch (KycEvidenceStorageException exception) {
                throw reviewException(
                        ApiErrorCode.KYC_APPLICATION_NOT_REVIEWABLE,
                        HttpStatus.CONFLICT
                );
            }
        }
    }

    private AdminKycApplicationDetailResponse detail(
            KycApplication application
    ) {
        return AdminKycApplicationDetailResponse.from(
                application,
                eventRepository.findAllByApplication_IdOrderByCreatedAtAsc(
                        application.getId()
                )
        );
    }

    private KycApplication application(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> reviewException(
                        ApiErrorCode.KYC_ADMIN_APPLICATION_NOT_FOUND,
                        HttpStatus.NOT_FOUND
                ));
    }

    private StaffUser activeStaff(String externalSubject) {
        return staffUserService.requireActiveStaff(externalSubject);
    }

    /**
     * Staff review starts only after the customer has submitted. In-progress
     * drafts remain customer-private even when the caller is a supervisor.
     * Deliberately use the same non-leaking result as an absent application.
     */
    private void requireStaffVisible(KycApplication application) {
        if (!STAFF_VISIBLE_APPLICATION_STATUSES.contains(
                application.getStatus()
        )) {
            throw reviewException(
                    ApiErrorCode.KYC_ADMIN_APPLICATION_NOT_FOUND,
                    HttpStatus.NOT_FOUND
            );
        }
    }

    /**
     * Queue rows are intentionally minimal. The full profile, audit history,
     * and private evidence become readable only after explicit assignment, or
     * to a supervisor performing an oversight action.
     */
    private void requireAssignedOrSupervisor(
            KycApplication application,
            StaffUser staff
    ) {
        if (!application.isAssignedTo(staff.getId())
                && !hasPermission(AdminPermission.KYC_SUPERVISE)) {
            throw reviewException(
                    ApiErrorCode.KYC_ADMIN_APPLICATION_NOT_FOUND,
                    HttpStatus.NOT_FOUND
            );
        }
    }

    private void requireAssignedDecision(
            KycApplication application,
            StaffUser staff
    ) {
        if (application.getStatus() != KycStatus.UNDER_REVIEW) {
            throw reviewException(
                    ApiErrorCode.KYC_APPLICATION_NOT_REVIEWABLE,
                    HttpStatus.CONFLICT
            );
        }
        if (!application.isAssignedTo(staff.getId())) {
            throw reviewException(
                    ApiErrorCode.KYC_REVIEWER_ASSIGNMENT_MISMATCH,
                    HttpStatus.CONFLICT
            );
        }
    }

    private void requireExpectedVersion(
            KycApplication application,
            long expectedVersion
    ) {
        if (application.getVersion() != expectedVersion) {
            throw staleVersion();
        }
    }

    private KycAdminReviewException staleVersion() {
        return reviewException(
                ApiErrorCode.KYC_STALE_APPLICATION_VERSION,
                HttpStatus.CONFLICT
        );
    }

    private List<KycDocument> resolveApplicationEvidence(
            KycApplication application,
            Set<UUID> evidenceIds
    ) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return List.of();
        }

        return evidenceIds.stream()
                .sorted()
                .map(evidenceId -> documentRepository
                        .findByIdAndApplication_IdAndDeletedAtIsNull(
                                evidenceId,
                                application.getId()
                        ).orElseThrow(() -> reviewException(
                                ApiErrorCode.KYC_EVIDENCE_NOT_PART_OF_APPLICATION,
                                HttpStatus.NOT_FOUND
                        )))
                .toList();
    }

    private List<KycMissingRequirement> sorted(
            Set<KycMissingRequirement> requirements
    ) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        return requirements.stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    /**
     * Bean validation protects the HTTP boundary. These checks also protect
     * service callers such as batch-free integration code: a transition to
     * RESUBMISSION_REQUIRED must always leave an immutable, customer-safe
     * instruction set for the customer API to render.
     */
    private String requiredCustomerMessage(String value) {
        String customerMessage = trimmed(value);
        if (customerMessage == null) {
            throw reviewException(
                    ApiErrorCode.KYC_REVIEW_REASON_REQUIRED,
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
        return customerMessage;
    }

    private List<KycMissingRequirement> requiredCorrections(
            Set<KycMissingRequirement> requirements
    ) {
        List<KycMissingRequirement> corrections = sorted(requirements);
        if (corrections.isEmpty()) {
            throw reviewException(
                    ApiErrorCode.KYC_REVIEW_REASON_REQUIRED,
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
        return corrections;
    }

    private void appendEvent(
            KycApplication application,
            StaffUser staff,
            KycReviewAction action,
            KycStatus previousStatus,
            KycStatus newStatus,
            KycReviewReasonCode reasonCode,
            String customerMessage,
            String internalNotes,
            Collection<KycDocument> evidence,
            Collection<KycMissingRequirement> missingRequirements,
            UUID correlationId
    ) {
        KycReviewEvent event = new KycReviewEvent(
                application,
                staff,
                action,
                previousStatus,
                newStatus,
                reasonCode,
                customerMessage,
                internalNotes,
                correlationId
        );
        evidence.forEach(event::addEvidenceReference);
        missingRequirements.forEach(event::addMissingRequirement);
        eventRepository.saveAndFlush(event);
    }

    private Specification<KycApplication> queueSpecification(
            KycStatus status,
            UUID assignedReviewerId,
            LocalDate submittedFrom,
            LocalDate submittedTo,
            UUID visibleToReviewerId
    ) {
        return (root, query, builder) -> {
            var predicates = builder.conjunction();
            Join<KycApplication, StaffUser> reviewer = root.join(
                    "assignedReviewer",
                    JoinType.LEFT
            );
            predicates = builder.and(
                    predicates,
                    root.get("status").in(STAFF_VISIBLE_APPLICATION_STATUSES)
            );
            if (status != null) {
                predicates = builder.and(predicates,
                        builder.equal(root.get("status"), status));
            }
            if (assignedReviewerId != null) {
                predicates = builder.and(predicates,
                        builder.equal(reviewer.get("id"), assignedReviewerId));
            }
            if (visibleToReviewerId != null) {
                predicates = builder.and(predicates, builder.or(
                        builder.isNull(reviewer.get("id")),
                        builder.equal(reviewer.get("id"), visibleToReviewerId)
                ));
            }
            if (submittedFrom != null) {
                predicates = builder.and(predicates,
                        builder.greaterThanOrEqualTo(root.get("submittedAt"),
                                submittedFrom.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (submittedTo != null) {
                predicates = builder.and(predicates,
                        builder.lessThan(root.get("submittedAt"),
                                submittedTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return predicates;
        };
    }

    private Sort queueSort(String value) {
        if ("submittedAtDesc".equals(value)) {
            return Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.asc("id"));
        }
        return Sort.by(Sort.Order.asc("submittedAt"), Sort.Order.asc("id"));
    }

    private boolean hasPermission(AdminPermission permission) {
        return org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getAuthorities()
                .stream()
                .anyMatch(authority -> permission.authority().equals(
                        authority.getAuthority()
                ));
    }

    private boolean isAllowedImage(String mimeType) {
        String normalized = mimeType == null ? "" : mimeType
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);
        return "image/jpeg".equals(normalized) || "image/png".equals(normalized);
    }

    private String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private KycAdminReviewException reviewException(
            ApiErrorCode code,
            HttpStatus status
    ) {
        return new KycAdminReviewException(code, status);
    }
}
