package com.mavela.backend.kyc.review;

import com.mavela.backend.TestcontainersConfiguration;
import com.mavela.backend.admin.auth.AdminPermission;
import com.mavela.backend.admin.staff.StaffUser;
import com.mavela.backend.admin.staff.StaffUserRepository;
import com.mavela.backend.admin.staff.StaffUserStatus;
import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.customer.Gender;
import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.error.ApiErrorCode;
import com.mavela.backend.kyc.CompleteKycEvidenceUploadRequest;
import com.mavela.backend.kyc.KycApplication;
import com.mavela.backend.kyc.KycApplicationRepository;
import com.mavela.backend.kyc.KycApplicationService;
import com.mavela.backend.kyc.KycCaptureMethod;
import com.mavela.backend.kyc.KycDocument;
import com.mavela.backend.kyc.KycDocumentRepository;
import com.mavela.backend.kyc.KycDocumentSide;
import com.mavela.backend.kyc.KycDocumentType;
import com.mavela.backend.kyc.KycDraftStep;
import com.mavela.backend.kyc.KycEvidenceType;
import com.mavela.backend.kyc.KycEvidenceUploadStatus;
import com.mavela.backend.kyc.KycResubmissionResponse;
import com.mavela.backend.kyc.KycWorkflowException;
import com.mavela.backend.kyc.RequestKycEvidenceUploadRequest;
import com.mavela.backend.kyc.UpdateKycApplicationDraftRequest;
import com.mavela.backend.kyc.storage.KycEvidenceStorage;
import com.mavela.backend.kyc.support.InMemoryKycEvidenceStorage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class KycAdminReviewIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private KycApplicationRepository applicationRepository;

    @Autowired
    private KycDocumentRepository documentRepository;

    @Autowired
    private KycReviewEventRepository eventRepository;

    @Autowired
    private KycApplicationService customerKycService;

    @Autowired
    private KycAdminReviewService reviewService;

    @Autowired
    private KycEvidenceStorage evidenceStorage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanDatabase() {
        SecurityContextHolder.clearContext();
        eventRepository.deleteAll();
        documentRepository.deleteAll();
        applicationRepository.deleteAll();
        customerRepository.deleteAll();
        staffUserRepository.deleteAll();
    }

    @Test
    void v14AppliesSuccessfully() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '14' AND success = TRUE
                """,
                Integer.class
        );
        assertEquals(1, count);
        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'staff_users'
                """,
                Integer.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'kyc_review_events'
                """,
                Integer.class
        ));
        assertConstraintExists("public", "uq_staff_users_external_subject");
        assertConstraintExists("public", "chk_staff_users_status");
        assertIndexExists("public", "idx_staff_users_status");
        assertIndexExists("public", "idx_kyc_applications_status_submitted_at");
        assertIndexExists("public", "idx_kyc_applications_assigned_reviewer");
        assertIndexExists(
                "public",
                "idx_kyc_review_events_application_created_at"
        );
        assertIndexExists(
                "public",
                "idx_kyc_review_events_reviewer_created_at"
        );

        String staffStatusDefinition = constraintDefinition(
                "public",
                "chk_staff_users_status"
        );
        assertTrue(staffStatusDefinition.contains("ACTIVE"));
        assertTrue(staffStatusDefinition.contains("DISABLED"));

        String reasonCodeDefinition = constraintDefinition(
                "public",
                "chk_kyc_review_events_reason_code"
        );
        assertTrue(reasonCodeDefinition.contains("DOCUMENT_UNREADABLE"));
        assertTrue(reasonCodeDefinition.contains("COMPLIANCE_RESTRICTION"));
    }

    @Test
    void v13UpgradesWithoutChangingExistingApplicationOrDocument() {
        String schema = "kyc_v14_upgrade_" + UUID.randomUUID()
                .toString().replace("-", "");
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        try {
            migrateSchema(schema, "13");
            jdbcTemplate.update(
                    """
                    INSERT INTO %s.customers (
                        id, username, phone_number, first_name, last_name,
                        preferred_locale, status, kyc_status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(schema),
                    customerId, "legacyadmin", "+243810000901", "Legacy",
                    "Customer", "en", "ACTIVE", "SUBMITTED"
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO %s.kyc_applications (
                        id, customer_id, attempt_number, status, created_at,
                        updated_at, submitted_at
                    ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP)
                    """.formatted(schema),
                    applicationId, customerId, 1, "SUBMITTED"
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO %s.kyc_documents (
                        id, kyc_application_id, evidence_type, document_type,
                        document_side, capture_method, storage_key, mime_type,
                        file_size, sha256_checksum, upload_status, created_at,
                        uploaded_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP)
                    """.formatted(schema),
                    documentId, applicationId, "SELFIE", null,
                    "NOT_APPLICABLE", "CAMERA_CAPTURE", "legacy/private/key",
                    "image/png", 8L, "a".repeat(64), "VALIDATED"
            );

            migrateSchema(schema, "14");

            assertEquals("SUBMITTED", jdbcTemplate.queryForObject(
                    "SELECT status FROM %s.kyc_applications WHERE id = ?"
                            .formatted(schema),
                    String.class, applicationId
            ));
            assertEquals("VALIDATED", jdbcTemplate.queryForObject(
                    "SELECT upload_status FROM %s.kyc_documents WHERE id = ?"
                            .formatted(schema),
                    String.class, documentId
            ));
            assertEquals(1, jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = 'kyc_applications'
                      AND column_name = 'assigned_reviewer_id'
                    """,
                    Integer.class, schema
            ));
            assertEquals(1, jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = ? AND table_name = 'kyc_review_events'
                    """,
                    Integer.class, schema
            ));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void submittedApplicationCanBeClaimedAndApprovedAtomically()
            throws IOException {
        KycApplication application = submittedApplication();
        StaffUser reviewer = staff("reviewer-subject");
        authenticate(reviewer.getExternalSubject(),
                AdminPermission.KYC_READ,
                AdminPermission.KYC_CLAIM,
                AdminPermission.KYC_DECIDE);

        AdminKycApplicationDetailResponse claimed = reviewService.claim(
                reviewer.getExternalSubject(), application.getId(), UUID.randomUUID()
        );
        assertEquals(KycStatus.UNDER_REVIEW, claimed.status());
        assertEquals(reviewer.getId(), claimed.assignedReviewer().id());
        assertEquals(KycStatus.UNDER_REVIEW, applicationStatus(application));
        assertEquals(KycStatus.UNDER_REVIEW, customerStatus(application));

        AdminKycApplicationDetailResponse approved = reviewService.approve(
                reviewer.getExternalSubject(),
                application.getId(),
                new ApproveKycApplicationRequest(
                        claimed.version(),
                        "Evidence reviewed."
                ),
                UUID.randomUUID()
        );
        assertEquals(KycStatus.APPROVED, approved.status());
        assertNotNull(approved.decidedAt());
        assertEquals(KycStatus.APPROVED, applicationStatus(application));
        assertEquals(KycStatus.APPROVED, customerStatus(application));
        assertEquals(2, eventRepository
                .findAllByApplication_IdOrderByCreatedAtAsc(application.getId())
                .size());
    }

    @Test
    void incompleteEvidenceCannotBeApproved() {
        Customer customer = completeCustomer();
        customerKycService.startApplication(customer.getId());
        KycApplication application = applicationRepository
                .findFirstByCustomer_IdOrderByAttemptNumberDesc(customer.getId())
                .orElseThrow();
        application.updateDraft(
                KycDraftStep.SELECT_DOCUMENT,
                KycDocumentType.NATIONAL_ID
        );
        application.submit(Instant.now());
        Customer persistedCustomer = customerRepository.findById(customer.getId())
                .orElseThrow();
        persistedCustomer.submitKycApplication();
        applicationRepository.saveAndFlush(application);
        customerRepository.saveAndFlush(persistedCustomer);

        StaffUser reviewer = staff("incomplete-reviewer");
        authenticate(reviewer.getExternalSubject(),
                AdminPermission.KYC_CLAIM,
                AdminPermission.KYC_DECIDE);
        AdminKycApplicationDetailResponse claimed = reviewService.claim(
                reviewer.getExternalSubject(), application.getId(), UUID.randomUUID()
        );

        KycAdminReviewException exception = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.approve(
                        reviewer.getExternalSubject(), application.getId(),
                        new ApproveKycApplicationRequest(claimed.version(), null),
                        UUID.randomUUID()
                )
        );
        assertEquals("KYC_APPLICATION_NOT_REVIEWABLE",
                exception.getCode().name());
        assertEquals(KycStatus.UNDER_REVIEW, applicationStatus(application));
    }

    @Test
    void anotherReviewerCannotDecideClaimedApplication() throws IOException {
        KycApplication application = submittedApplication();
        StaffUser assignedReviewer = staff("assigned-reviewer");
        StaffUser otherReviewer = staff("other-reviewer");
        authenticate(assignedReviewer.getExternalSubject(),
                AdminPermission.KYC_CLAIM,
                AdminPermission.KYC_DECIDE);
        AdminKycApplicationDetailResponse claimed = reviewService.claim(
                assignedReviewer.getExternalSubject(), application.getId(),
                UUID.randomUUID()
        );

        authenticate(otherReviewer.getExternalSubject(), AdminPermission.KYC_DECIDE);
        KycAdminReviewException exception = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.approve(
                        otherReviewer.getExternalSubject(), application.getId(),
                        new ApproveKycApplicationRequest(claimed.version(), null),
                        UUID.randomUUID()
                )
        );
        assertEquals("KYC_REVIEWER_ASSIGNMENT_MISMATCH",
                exception.getCode().name());
    }

    @Test
    void reviewerQueueExcludesCasesClaimedByAnotherReviewer()
            throws IOException {
        KycApplication unassignedApplication = submittedApplication();
        KycApplication otherReviewersApplication = submittedApplication();
        StaffUser assignedReviewer = staff("queue-assigned-reviewer");
        StaffUser viewingReviewer = staff("queue-viewing-reviewer");

        authenticate(assignedReviewer.getExternalSubject(),
                AdminPermission.KYC_CLAIM);
        reviewService.claim(
                assignedReviewer.getExternalSubject(),
                otherReviewersApplication.getId(),
                UUID.randomUUID()
        );

        authenticate(viewingReviewer.getExternalSubject(),
                AdminPermission.KYC_READ);
        AdminKycQueueResponse queue = reviewService.findQueue(
                viewingReviewer.getExternalSubject(),
                null,
                null,
                null,
                null,
                0,
                100,
                null
        );

        Set<UUID> visibleIds = queue.content().stream()
                .map(AdminKycQueueItemResponse::id)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(visibleIds.contains(unassignedApplication.getId()));
        assertFalse(visibleIds.contains(otherReviewersApplication.getId()));
    }

    @Test
    void unassignedReviewerCannotReadUnassignedCaseDetailsOrEvidence()
            throws IOException {
        KycApplication application = submittedApplication();
        StaffUser reviewer = staff("unassigned-case-reviewer");
        authenticate(reviewer.getExternalSubject(), AdminPermission.KYC_READ);

        KycAdminReviewException detailException = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.getApplicationDetail(
                        reviewer.getExternalSubject(),
                        application.getId(),
                        UUID.randomUUID()
                )
        );
        assertEquals("KYC_ADMIN_APPLICATION_NOT_FOUND",
                detailException.getCode().name());

        KycAdminReviewException evidenceException = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.openEvidence(
                        reviewer.getExternalSubject(),
                        application.getId(),
                        firstEvidence(application).getId(),
                        UUID.randomUUID()
                )
        );
        assertEquals("KYC_ADMIN_APPLICATION_NOT_FOUND",
                evidenceException.getCode().name());
    }

    @Test
    void staffCannotQueueOrReadAnUnsubmittedCustomerDraft() {
        KycApplication draft = inProgressApplication();
        StaffUser supervisor = staff("draft-visibility-supervisor");
        authenticate(
                supervisor.getExternalSubject(),
                AdminPermission.KYC_READ,
                AdminPermission.KYC_SUPERVISE
        );

        AdminKycQueueResponse queue = reviewService.findQueue(
                supervisor.getExternalSubject(),
                null,
                null,
                null,
                null,
                0,
                100,
                null
        );
        assertFalse(queue.content().stream()
                .map(AdminKycQueueItemResponse::id)
                .anyMatch(draft.getId()::equals));

        KycAdminReviewException detailException = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.getApplicationDetail(
                        supervisor.getExternalSubject(),
                        draft.getId(),
                        UUID.randomUUID()
                )
        );
        assertEquals("KYC_ADMIN_APPLICATION_NOT_FOUND",
                detailException.getCode().name());

        KycAdminReviewException historyException = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.history(
                        supervisor.getExternalSubject(),
                        draft.getId()
                )
        );
        assertEquals("KYC_ADMIN_APPLICATION_NOT_FOUND",
                historyException.getCode().name());

        KycAdminReviewException evidenceException = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.openEvidence(
                        supervisor.getExternalSubject(),
                        draft.getId(),
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        );
        assertEquals("KYC_ADMIN_APPLICATION_NOT_FOUND",
                evidenceException.getCode().name());
    }

    @Test
    void staleExpectedVersionCannotDecideApplication() throws IOException {
        KycApplication application = submittedApplication();
        StaffUser reviewer = staff("stale-reviewer");
        authenticate(reviewer.getExternalSubject(),
                AdminPermission.KYC_CLAIM,
                AdminPermission.KYC_DECIDE);
        long versionBeforeClaim = application.getVersion();
        reviewService.claim(reviewer.getExternalSubject(), application.getId(),
                UUID.randomUUID());

        KycAdminReviewException exception = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.approve(
                        reviewer.getExternalSubject(), application.getId(),
                        new ApproveKycApplicationRequest(versionBeforeClaim, null),
                        UUID.randomUUID()
                )
        );
        assertEquals("KYC_STALE_APPLICATION_VERSION", exception.getCode().name());
    }

    @Test
    void concurrentDecisionsCannotBothSucceed() throws Exception {
        KycApplication application = submittedApplication();
        StaffUser reviewer = staff("concurrent-reviewer");
        authenticate(reviewer.getExternalSubject(),
                AdminPermission.KYC_CLAIM,
                AdminPermission.KYC_DECIDE);
        AdminKycApplicationDetailResponse claimed = reviewService.claim(
                reviewer.getExternalSubject(), application.getId(),
                UUID.randomUUID()
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<DecisionResult>> decisions = List.of(
                    executor.submit(() -> approveAtTheSameTime(
                            reviewer, application.getId(), claimed.version(),
                            ready, start
                    )),
                    executor.submit(() -> approveAtTheSameTime(
                            reviewer, application.getId(), claimed.version(),
                            ready, start
                    ))
            );

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<DecisionResult> results = List.of(
                    decisions.getFirst().get(10, TimeUnit.SECONDS),
                    decisions.getLast().get(10, TimeUnit.SECONDS)
            );
            assertEquals(1, results.stream()
                    .filter(DecisionResult::succeeded)
                    .count());
            assertEquals(1, results.stream()
                    .filter(result -> !result.succeeded())
                    .count());
            assertEquals(KycStatus.APPROVED, applicationStatus(application));
            assertEquals(KycStatus.APPROVED, customerStatus(application));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void resubmissionPersistsExplicitSafeCustomerInstructions()
            throws Exception {
        KycApplication application = submittedApplication();
        StaffUser reviewer = staff("resubmission-reviewer");
        authenticate(reviewer.getExternalSubject(),
                AdminPermission.KYC_CLAIM,
                AdminPermission.KYC_DECIDE);
        AdminKycApplicationDetailResponse claimed = reviewService.claim(
                reviewer.getExternalSubject(), application.getId(), UUID.randomUUID()
        );
        KycDocument front = documentRepository
                .findAllByApplication_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        application.getId()
                ).stream()
                .filter(document -> document.getDocumentSide()
                        == KycDocumentSide.FRONT)
                .findFirst().orElseThrow();

        AdminKycApplicationDetailResponse resubmission =
                reviewService.requestResubmission(
                        reviewer.getExternalSubject(), application.getId(),
                        new RequestKycResubmissionRequest(
                                claimed.version(),
                                KycReviewReasonCode.DOCUMENT_UNREADABLE,
                                "Please upload a clearer front image.",
                                "Glare obscures the number.",
                                Set.of(),
                                Set.of(KycMissingRequirement.DOCUMENT_FRONT)
                        ),
                        UUID.randomUUID()
                );

        assertEquals(KycStatus.RESUBMISSION_REQUIRED, resubmission.status());
        assertEquals(KycStatus.RESUBMISSION_REQUIRED, applicationStatus(application));
        assertEquals(KycStatus.RESUBMISSION_REQUIRED, customerStatus(application));
        assertEquals(KycEvidenceUploadStatus.VALIDATED,
                documentRepository.findById(front.getId()).orElseThrow()
                        .getUploadStatus());
        var customerResponse = customerKycService.getCurrentApplication(
                customerId(application)
        );
        assertEquals("Please upload a clearer front image.", customerResponse
                .rejectionReason());
        assertNotNull(customerResponse.resubmission());
        assertEquals("Please upload a clearer front image.", customerResponse
                .resubmission().customerMessage());
        assertEquals(List.of(KycMissingRequirement.DOCUMENT_FRONT),
                customerResponse.resubmission().requiredCorrections());
        assertEquals(List.of(), customerResponse.resubmission()
                .completedCorrections());
        assertEquals(
                List.of(
                        "customerMessage",
                        "requiredCorrections",
                        "completedCorrections"
                ),
                Arrays.stream(KycResubmissionResponse.class
                                .getRecordComponents())
                        .map(component -> component.getName())
                        .toList()
        );
        mockMvc.perform(get("/api/v1/kyc/applications/current")
                        .with(jwt().jwt(jwt -> jwt.subject(
                                customerId(application).toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resubmission.customerMessage",
                        is("Please upload a clearer front image.")))
                .andExpect(jsonPath("$.resubmission.requiredCorrections[0]",
                        is("DOCUMENT_FRONT")))
                .andExpect(jsonPath("$.resubmission.completedCorrections")
                        .isArray())
                .andExpect(jsonPath("$.resubmission.internalNotes")
                        .doesNotExist())
                .andExpect(jsonPath("$.resubmission.evidenceIds")
                        .doesNotExist())
                .andExpect(jsonPath("$.resubmission.reviewer")
                        .doesNotExist())
                .andExpect(jsonPath("$.resubmission.storageKey")
                        .doesNotExist());
        KycWorkflowException unrequestedSelfie = assertThrows(
                KycWorkflowException.class,
                () -> customerKycService.requestEvidenceUpload(
                        customerId(application),
                        new RequestKycEvidenceUploadRequest(
                                KycEvidenceType.SELFIE,
                                null,
                                KycDocumentSide.NOT_APPLICABLE,
                                KycCaptureMethod.CAMERA_CAPTURE,
                                "image/png",
                                1,
                                "a".repeat(64)
                        )
                )
        );
        assertEquals(ApiErrorCode.KYC_EVIDENCE_UPLOAD_NOT_ALLOWED,
                unrequestedSelfie.getCode());
        completeEvidence(
                customerId(application),
                KycEvidenceType.DOCUMENT,
                KycDocumentType.NATIONAL_ID,
                KycDocumentSide.FRONT,
                KycCaptureMethod.CAMERA_CAPTURE,
                syntheticPng()
        );
        var correctedResponse = customerKycService.getCurrentApplication(
                customerId(application)
        );
        assertEquals(List.of(KycMissingRequirement.DOCUMENT_FRONT),
                correctedResponse.resubmission().completedCorrections());
        assertEquals(KycStatus.SUBMITTED, customerKycService
                .submitCurrentApplication(customerId(application)).status());
        AdminKycReviewEventResponse event = resubmission.reviewHistory()
                .getLast();
        assertEquals(KycReviewAction.RESUBMISSION_REQUESTED,
                event.action());
        assertEquals(List.of(), event.evidenceIds());
        assertEquals(List.of(KycMissingRequirement.DOCUMENT_FRONT),
                event.missingRequirements());
    }

    @Test
    void resubmissionCannotBeCreatedWithoutSafeMessageAndCorrections()
            throws IOException {
        KycApplication application = submittedApplication();
        StaffUser reviewer = staff("resubmission-instructions-reviewer");
        authenticate(reviewer.getExternalSubject(),
                AdminPermission.KYC_CLAIM,
                AdminPermission.KYC_DECIDE);
        AdminKycApplicationDetailResponse claimed = reviewService.claim(
                reviewer.getExternalSubject(), application.getId(),
                UUID.randomUUID()
        );

        KycAdminReviewException missingMessage = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.requestResubmission(
                        reviewer.getExternalSubject(), application.getId(),
                        new RequestKycResubmissionRequest(
                                claimed.version(),
                                KycReviewReasonCode.DOCUMENT_UNREADABLE,
                                "   ",
                                null,
                                Set.of(),
                                Set.of(KycMissingRequirement.DOCUMENT_FRONT)
                        ),
                        UUID.randomUUID()
                )
        );
        assertEquals(ApiErrorCode.KYC_REVIEW_REASON_REQUIRED,
                missingMessage.getCode());

        KycAdminReviewException missingCorrections = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.requestResubmission(
                        reviewer.getExternalSubject(), application.getId(),
                        new RequestKycResubmissionRequest(
                                claimed.version(),
                                KycReviewReasonCode.DOCUMENT_UNREADABLE,
                                "Please update your document.",
                                null,
                                Set.of(),
                                Set.of()
                        ),
                        UUID.randomUUID()
                )
        );
        assertEquals(ApiErrorCode.KYC_REVIEW_REASON_REQUIRED,
                missingCorrections.getCode());
        assertEquals(KycStatus.UNDER_REVIEW, applicationStatus(application));
        assertEquals(1, eventRepository.count());
    }

    @Test
    void reviewerCannotReleaseButSupervisorCanReleaseAnAssignedCase()
            throws IOException {
        KycApplication application = submittedApplication();
        StaffUser reviewer = staff("release-reviewer");
        StaffUser supervisor = staff("release-supervisor");
        authenticate(reviewer.getExternalSubject(), AdminPermission.KYC_CLAIM);
        AdminKycApplicationDetailResponse claimed = reviewService.claim(
                reviewer.getExternalSubject(), application.getId(),
                UUID.randomUUID()
        );

        assertThrows(AccessDeniedException.class, () -> reviewService.release(
                reviewer.getExternalSubject(),
                application.getId(),
                new ReleaseKycApplicationRequest(claimed.version()),
                UUID.randomUUID()
        ));

        authenticate(supervisor.getExternalSubject(),
                AdminPermission.KYC_SUPERVISE);
        AdminKycApplicationDetailResponse released = reviewService.release(
                supervisor.getExternalSubject(),
                application.getId(),
                new ReleaseKycApplicationRequest(claimed.version()),
                UUID.randomUUID()
        );

        assertEquals(KycStatus.SUBMITTED, released.status());
        assertNull(released.assignedReviewer());
        assertEquals(KycStatus.SUBMITTED, applicationStatus(application));
        assertEquals(KycStatus.SUBMITTED, customerStatus(application));
        AdminKycReviewEventResponse event = released.reviewHistory().getLast();
        assertEquals(KycReviewAction.RELEASED, event.action());
        assertEquals(supervisor.getId(), event.actor().id());
    }

    @Test
    void evidenceFromAnotherApplicationIsNotReadable() throws IOException {
        KycApplication first = submittedApplication();
        KycApplication second = submittedApplication();
        StaffUser reviewer = staff("reader-reviewer");
        authenticate(
                reviewer.getExternalSubject(),
                AdminPermission.KYC_READ,
                AdminPermission.KYC_CLAIM
        );
        reviewService.claim(
                reviewer.getExternalSubject(),
                first.getId(),
                UUID.randomUUID()
        );
        KycDocument secondEvidence = documentRepository
                .findAllByApplication_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        second.getId()
                ).getFirst();

        KycAdminReviewException exception = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.openEvidence(
                        reviewer.getExternalSubject(),
                        first.getId(), secondEvidence.getId(), UUID.randomUUID()
                )
        );
        assertEquals("KYC_EVIDENCE_NOT_PART_OF_APPLICATION",
                exception.getCode().name());
    }

    @Test
    void unassignedReviewerCannotReadEvidenceFromAnotherReviewersCase()
            throws IOException {
        KycApplication application = submittedApplication();
        StaffUser assignedReviewer = staff("evidence-assigned-reviewer");
        StaffUser otherReviewer = staff("evidence-other-reviewer");
        authenticate(assignedReviewer.getExternalSubject(),
                AdminPermission.KYC_READ,
                AdminPermission.KYC_CLAIM);
        reviewService.claim(
                assignedReviewer.getExternalSubject(),
                application.getId(),
                UUID.randomUUID()
        );
        KycDocument evidence = firstEvidence(application);

        authenticate(otherReviewer.getExternalSubject(), AdminPermission.KYC_READ);
        KycAdminReviewException exception = assertThrows(
                KycAdminReviewException.class,
                () -> reviewService.openEvidence(
                        otherReviewer.getExternalSubject(),
                        application.getId(),
                        evidence.getId(),
                        UUID.randomUUID()
                )
        );

        assertEquals(
                "KYC_ADMIN_APPLICATION_NOT_FOUND",
                exception.getCode().name()
        );
    }

    @Test
    void supervisorCanReadAssignedEvidenceAndTheViewIsAudited()
            throws IOException {
        KycApplication application = submittedApplication();
        StaffUser assignedReviewer = staff("evidence-owner-reviewer");
        StaffUser supervisor = staff("evidence-supervisor");
        authenticate(assignedReviewer.getExternalSubject(),
                AdminPermission.KYC_READ,
                AdminPermission.KYC_CLAIM);
        reviewService.claim(
                assignedReviewer.getExternalSubject(),
                application.getId(),
                UUID.randomUUID()
        );
        KycDocument evidence = firstEvidence(application);

        authenticate(supervisor.getExternalSubject(),
                AdminPermission.KYC_READ,
                AdminPermission.KYC_SUPERVISE);
        try (KycEvidenceStorage.EvidenceStream stream = reviewService.openEvidence(
                supervisor.getExternalSubject(),
                application.getId(),
                evidence.getId(),
                UUID.randomUUID()
        )) {
            assertEquals("image/png", stream.mimeType());
            assertEquals(evidence.getFileSize(), stream.byteSize());
            assertEquals(1, stream.inputStream().readNBytes(1).length);
        }

        AdminKycReviewEventResponse event = reviewService.history(
                supervisor.getExternalSubject(),
                application.getId()
        ).getLast();
        assertEquals(KycReviewAction.EVIDENCE_VIEWED, event.action());
        assertEquals(supervisor.getId(), event.actor().id());
        assertEquals(List.of(evidence.getId()), event.evidenceIds());
    }

    private KycDocument firstEvidence(KycApplication application) {
        return documentRepository
                .findAllByApplication_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        application.getId()
                )
                .getFirst();
    }

    private DecisionResult approveAtTheSameTime(
            StaffUser reviewer,
            UUID applicationId,
            long expectedVersion,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        authenticate(reviewer.getExternalSubject(), AdminPermission.KYC_DECIDE);
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent review test timed out.");
            }
            reviewService.approve(
                    reviewer.getExternalSubject(),
                    applicationId,
                    new ApproveKycApplicationRequest(expectedVersion, null),
                    UUID.randomUUID()
            );
            return new DecisionResult(true);
        } catch (KycAdminReviewException exception) {
            return new DecisionResult(false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private KycApplication inProgressApplication() {
        Customer customer = completeCustomer();
        customerKycService.startApplication(customer.getId());
        return applicationRepository
                .findFirstByCustomer_IdOrderByAttemptNumberDesc(customer.getId())
                .orElseThrow();
    }

    private KycApplication submittedApplication() throws IOException {
        Customer customer = completeCustomer();
        customerKycService.startApplication(customer.getId());
        customerKycService.updateDraft(customer.getId(),
                new UpdateKycApplicationDraftRequest(
                        KycDraftStep.SELECT_DOCUMENT,
                        KycDocumentType.NATIONAL_ID
                ));
        byte[] image = syntheticPng();
        completeEvidence(customer.getId(), KycEvidenceType.DOCUMENT,
                KycDocumentType.NATIONAL_ID, KycDocumentSide.FRONT,
                KycCaptureMethod.CAMERA_CAPTURE, image);
        completeEvidence(customer.getId(), KycEvidenceType.DOCUMENT,
                KycDocumentType.NATIONAL_ID, KycDocumentSide.BACK,
                KycCaptureMethod.GALLERY_UPLOAD, image);
        completeEvidence(customer.getId(), KycEvidenceType.SELFIE,
                null, KycDocumentSide.NOT_APPLICABLE,
                KycCaptureMethod.CAMERA_CAPTURE, image);
        customerKycService.submitCurrentApplication(customer.getId());
        return applicationRepository
                .findFirstByCustomer_IdOrderByAttemptNumberDesc(customer.getId())
                .orElseThrow();
    }

    private void completeEvidence(
            UUID customerId,
            KycEvidenceType type,
            KycDocumentType documentType,
            KycDocumentSide side,
            KycCaptureMethod captureMethod,
            byte[] image
    ) {
        var request = new RequestKycEvidenceUploadRequest(
                type, documentType, side, captureMethod, "image/png",
                image.length, sha256(image)
        );
        var session = customerKycService.requestEvidenceUpload(customerId, request);
        KycDocument evidence = documentRepository.findById(session.evidenceId())
                .orElseThrow();
        testStorage().put(evidence.getStorageKey(), "image/png", image);
        customerKycService.completeEvidenceUpload(customerId, evidence.getId(),
                new CompleteKycEvidenceUploadRequest(sha256(image)));
    }

    private Customer completeCustomer() {
        Customer customer = new Customer(
                "+2438" + String.format("%08d",
                        Math.abs(UUID.randomUUID().hashCode()) % 100_000_000L),
                null, "Ada", "Lovelace", "en"
        );
        customer.updateProfile(
                "Ada", "Lovelace", "en", LocalDate.of(1995, 4, 12),
                "CD", Gender.FEMALE, "1 Mavela Avenue", "Kinshasa",
                "Kinshasa", Instant.now()
        );
        return customerRepository.saveAndFlush(customer);
    }

    private StaffUser staff(String subject) {
        return staffUserRepository.saveAndFlush(new StaffUser(
                subject, subject + "@mavela.test", "Reviewer " + subject,
                StaffUserStatus.ACTIVE
        ));
    }

    private KycStatus applicationStatus(KycApplication application) {
        return applicationRepository.findById(application.getId())
                .orElseThrow().getStatus();
    }

    private KycStatus customerStatus(KycApplication application) {
        return customerRepository.findById(customerId(application))
                .orElseThrow().getKycStatus();
    }

    private UUID customerId(KycApplication application) {
        return jdbcTemplate.queryForObject(
                "SELECT customer_id FROM kyc_applications WHERE id = ?",
                UUID.class,
                application.getId()
        );
    }

    private void authenticate(String subject, AdminPermission... permissions) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                subject,
                "not-a-token",
                List.of(permissions).stream()
                        .map(permission -> new SimpleGrantedAuthority(
                                permission.authority()))
                        .toList()
        );
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private InMemoryKycEvidenceStorage testStorage() {
        return (InMemoryKycEvidenceStorage) evidenceStorage;
    }

    private byte[] syntheticPng() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.MAGENTA.getRGB());
        image.setRGB(1, 0, Color.DARK_GRAY.getRGB());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                value.append(String.format("%02x", current));
            }
            return value.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertConstraintExists(String schema, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_constraint constraint_definition
                JOIN pg_namespace namespace
                    ON namespace.oid = constraint_definition.connamespace
                WHERE namespace.nspname = ?
                    AND constraint_definition.conname = ?
                """,
                Integer.class,
                schema,
                constraintName
        );
        assertEquals(1, count);
    }

    private void assertIndexExists(String schema, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = ?
                    AND indexname = ?
                """,
                Integer.class,
                schema,
                indexName
        );
        assertEquals(1, count);
    }

    private String constraintDefinition(String schema, String constraintName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT pg_get_constraintdef(constraint_definition.oid)
                FROM pg_constraint constraint_definition
                JOIN pg_namespace namespace
                    ON namespace.oid = constraint_definition.connamespace
                WHERE namespace.nspname = ?
                    AND constraint_definition.conname = ?
                """,
                String.class,
                schema,
                constraintName
        );
    }

    private void migrateSchema(String schema, String targetVersion) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(targetVersion)
                .load()
                .migrate();
    }

    private record DecisionResult(boolean succeeded) {
    }
}
