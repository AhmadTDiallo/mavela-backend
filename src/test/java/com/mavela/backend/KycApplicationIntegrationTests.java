package com.mavela.backend;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.customer.Gender;
import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.kyc.KycApplicationRepository;
import com.mavela.backend.kyc.KycApplicationResponse;
import com.mavela.backend.kyc.KycApplicationService;
import com.mavela.backend.kyc.KycCaptureMethod;
import com.mavela.backend.kyc.KycDocument;
import com.mavela.backend.kyc.KycDocumentRepository;
import com.mavela.backend.kyc.KycDocumentSide;
import com.mavela.backend.kyc.KycDocumentType;
import com.mavela.backend.kyc.KycDraftStep;
import com.mavela.backend.kyc.KycEvidenceType;
import com.mavela.backend.kyc.KycEvidenceUploadStatus;
import com.mavela.backend.kyc.KycWorkflowException;
import com.mavela.backend.kyc.CompleteKycEvidenceUploadRequest;
import com.mavela.backend.kyc.RequestKycEvidenceUploadRequest;
import com.mavela.backend.kyc.UpdateKycApplicationDraftRequest;
import com.mavela.backend.kyc.support.InMemoryKycEvidenceStorage;
import com.mavela.backend.kyc.storage.KycEvidenceStorage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class KycApplicationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private KycApplicationRepository applicationRepository;

    @Autowired
    private KycDocumentRepository documentRepository;

    @Autowired
    private KycEvidenceStorage evidenceStorage;

    @Autowired
    private KycApplicationService applicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanDatabase() {
        documentRepository.deleteAll();
        applicationRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void v11AppliesSuccessfully() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '11' AND success = TRUE
                """,
                Integer.class
        );

        assertEquals(1, count);
    }

    @Test
    void v12AppliesSuccessfully() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '12' AND success = TRUE
                """,
                Integer.class
        );

        assertEquals(1, count);
    }

    @Test
    void v11UpgradesPopulatedV8DataWithoutInvalidatingLegacyActiveCustomers() {
        String schema = "kyc_upgrade_" + UUID.randomUUID()
                .toString()
                .replace("-", "");

        try {
            migrateSchema(schema, "8");

            UUID notStartedCustomerId = UUID.randomUUID();
            UUID pendingCustomerId = UUID.randomUUID();
            UUID verifiedCustomerId = UUID.randomUUID();

            insertLegacyActiveCustomer(
                    schema,
                    notStartedCustomerId,
                    "+243810000001",
                    "NOT_STARTED"
            );
            insertLegacyActiveCustomer(
                    schema,
                    pendingCustomerId,
                    "+243810000002",
                    "PENDING"
            );
            insertLegacyActiveCustomer(
                    schema,
                    verifiedCustomerId,
                    "+243810000003",
                    "VERIFIED"
            );

            migrateSchema(schema, "11");

            assertEquals(
                    "NOT_STARTED",
                    customerKycStatus(schema, notStartedCustomerId)
            );
            assertEquals(
                    "SUBMITTED",
                    customerKycStatus(schema, pendingCustomerId)
            );
            assertEquals(
                    "APPROVED",
                    customerKycStatus(schema, verifiedCustomerId)
            );
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM %s.flyway_schema_history
                            WHERE version = '11' AND success = TRUE
                            """.formatted(schema),
                            Integer.class
                    )
            );
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM information_schema.tables
                            WHERE table_schema = ?
                              AND table_name = 'kyc_applications'
                            """,
                            Integer.class,
                            schema
                    )
            );
            assertEquals(
                    false,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT convalidated
                            FROM pg_constraint
                            WHERE conname = 'chk_customers_active_username'
                              AND conrelid = (? || '.customers')::regclass
                            """,
                            Boolean.class,
                            schema
                    )
            );

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(
                            """
                            UPDATE %s.customers
                            SET first_name = 'Updated Legacy Customer'
                            WHERE id = ?
                            """.formatted(schema),
                            notStartedCustomerId
                    )
            );
            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(
                            """
                            INSERT INTO %s.customers (
                                id,
                                phone_number,
                                first_name,
                                last_name,
                                preferred_locale,
                                status,
                                kyc_status,
                                created_at,
                                updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """.formatted(schema),
                            UUID.randomUUID(),
                            "+243810000004",
                            "New",
                            "Customer",
                            "en",
                            "ACTIVE",
                            "NOT_STARTED"
                    )
            );
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void v12UpgradesPopulatedV11DataWithoutChangingExistingApplications() {
        String schema = "kyc_v12_upgrade_" + UUID.randomUUID()
                .toString()
                .replace("-", "");
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        try {
            migrateSchema(schema, "11");

            jdbcTemplate.update(
                    """
                    INSERT INTO %s.customers (
                        id, username, phone_number, first_name, last_name,
                        preferred_locale, status, kyc_status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(schema),
                    customerId,
                    "legacyv11",
                    "+243810000099",
                    "Legacy",
                    "Customer",
                    "fr-CD",
                    "ACTIVE",
                    "IN_PROGRESS"
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO %s.kyc_applications (
                        id, customer_id, attempt_number, status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(schema),
                    applicationId,
                    customerId,
                    1,
                    "IN_PROGRESS"
            );

            migrateSchema(schema, "12");

            assertEquals(
                    "IN_PROGRESS",
                    jdbcTemplate.queryForObject(
                            "SELECT status FROM %s.kyc_applications WHERE id = ?"
                                    .formatted(schema),
                            String.class,
                            applicationId
                    )
            );
            assertEquals(
                    "CONFIRM_INFORMATION",
                    jdbcTemplate.queryForObject(
                            "SELECT current_step FROM %s.kyc_applications WHERE id = ?"
                                    .formatted(schema),
                            String.class,
                            applicationId
                    )
            );
            assertEquals(
                    "fr-CD",
                    jdbcTemplate.queryForObject(
                            "SELECT preferred_locale FROM %s.customers WHERE id = ?"
                                    .formatted(schema),
                            String.class,
                            customerId
                    )
            );
            assertEquals(
                    1,
                    jdbcTemplate.update(
                            "UPDATE %s.customers SET preferred_locale = 'fr' WHERE id = ?"
                                    .formatted(schema),
                            customerId
                    )
            );
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM information_schema.tables
                            WHERE table_schema = ? AND table_name = 'kyc_documents'
                            """,
                            Integer.class,
                            schema
                    )
            );
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void completeProfileCustomerCanCreateAttemptOneAndStartKyc() {
        Customer customer = saveCompleteCustomer();

        KycApplicationResponse response = applicationService.startApplication(
                customer.getId()
        );

        assertNotNull(response.id());
        assertEquals(1, response.attemptNumber());
        assertEquals(KycStatus.IN_PROGRESS, response.status());
        assertEquals(
                KycStatus.IN_PROGRESS,
                customerRepository.findById(customer.getId())
                        .orElseThrow()
                        .getKycStatus()
        );
        assertEquals(
                KycStatus.IN_PROGRESS,
                applicationRepository.findById(response.id())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void incompleteProfileCustomerCanStartResumableDraft() {
        Customer customer = customerRepository.saveAndFlush(
                newCustomer("incomplete")
        );

        KycApplicationResponse response = applicationService.startApplication(
                customer.getId()
        );

        assertEquals(
                KycStatus.IN_PROGRESS,
                response.status()
        );
        assertEquals(
                KycDraftStep.CONFIRM_INFORMATION,
                response.currentStep()
        );
    }

    @Test
    void secondApplicationCannotBeStartedForTheSameCustomer() {
        Customer customer = saveCompleteCustomer();
        applicationService.startApplication(customer.getId());

        KycWorkflowException exception = assertThrows(
                KycWorkflowException.class,
                () -> applicationService.startApplication(customer.getId())
        );

        assertEquals(
                "KYC_APPLICATION_ALREADY_EXISTS",
                exception.getCode().name()
        );
    }

    @Test
    void draftProgressPersistsWithoutSubmittingTheApplication() {
        Customer customer = saveCompleteCustomer();
        applicationService.startApplication(customer.getId());

        KycApplicationResponse response = applicationService.updateDraft(
                customer.getId(),
                new UpdateKycApplicationDraftRequest(
                        KycDraftStep.SELECT_DOCUMENT,
                        KycDocumentType.NATIONAL_ID
                )
        );

        assertEquals(KycStatus.IN_PROGRESS, response.status());
        assertEquals(KycDraftStep.SELECT_DOCUMENT, response.currentStep());
        assertEquals(KycDocumentType.NATIONAL_ID, response.documentType());
        assertEquals(
                KycStatus.IN_PROGRESS,
                customerRepository.findById(customer.getId())
                        .orElseThrow()
                        .getKycStatus()
        );
    }

    @Test
    void validatedEvidenceAndFinalSubmitTransitionToSubmittedIdempotently()
            throws IOException {
        Customer customer = saveCompleteCustomer();
        applicationService.startApplication(customer.getId());
        applicationService.updateDraft(
                customer.getId(),
                new UpdateKycApplicationDraftRequest(
                        KycDraftStep.SELECT_DOCUMENT,
                        KycDocumentType.NATIONAL_ID
                )
        );

        byte[] syntheticImage = syntheticPng();
        completeEvidence(
                customer.getId(),
                KycEvidenceType.DOCUMENT,
                KycDocumentType.NATIONAL_ID,
                KycDocumentSide.FRONT,
                KycCaptureMethod.CAMERA_CAPTURE,
                syntheticImage
        );
        completeEvidence(
                customer.getId(),
                KycEvidenceType.DOCUMENT,
                KycDocumentType.NATIONAL_ID,
                KycDocumentSide.BACK,
                KycCaptureMethod.GALLERY_UPLOAD,
                syntheticImage
        );
        completeEvidence(
                customer.getId(),
                KycEvidenceType.SELFIE,
                null,
                KycDocumentSide.NOT_APPLICABLE,
                KycCaptureMethod.CAMERA_CAPTURE,
                syntheticImage
        );

        KycApplicationResponse submitted = applicationService
                .submitCurrentApplication(customer.getId());
        KycApplicationResponse repeated = applicationService
                .submitCurrentApplication(customer.getId());

        assertEquals(KycStatus.SUBMITTED, submitted.status());
        assertNotNull(submitted.submittedAt());
        assertEquals(submitted.id(), repeated.id());
        assertEquals(KycStatus.SUBMITTED, repeated.status());
        assertEquals(
                KycStatus.SUBMITTED,
                customerRepository.findById(customer.getId())
                        .orElseThrow()
                        .getKycStatus()
        );
        assertEquals(
                "Ada",
                applicationRepository.findById(submitted.id())
                        .orElseThrow()
                        .getProfileFirstName()
        );
        KycWorkflowException immutable = assertThrows(
                KycWorkflowException.class,
                () -> applicationService.updateDraft(
                        customer.getId(),
                        new UpdateKycApplicationDraftRequest(
                                KycDraftStep.REVIEW,
                                KycDocumentType.NATIONAL_ID
                        )
                )
        );
        assertEquals("KYC_DRAFT_NOT_EDITABLE", immutable.getCode().name());
    }

    @Test
    void incompleteProfileCannotSubmitDraft() {
        Customer customer = customerRepository.saveAndFlush(
                newCustomer("submit-incomplete")
        );
        applicationService.startApplication(customer.getId());

        KycWorkflowException exception = assertThrows(
                KycWorkflowException.class,
                () -> applicationService.submitCurrentApplication(customer.getId())
        );

        assertEquals("KYC_PROFILE_INCOMPLETE", exception.getCode().name());
        assertEquals(
                KycDraftStep.COMPLETE_INFORMATION.name(),
                exception.getStep()
        );
    }

    @Test
    void customerCannotCompleteAnotherCustomersEvidence()
            throws IOException {
        Customer owner = saveCompleteCustomer();
        applicationService.startApplication(owner.getId());
        applicationService.updateDraft(
                owner.getId(),
                new UpdateKycApplicationDraftRequest(
                        KycDraftStep.SELECT_DOCUMENT,
                        KycDocumentType.PASSPORT
                )
        );
        byte[] syntheticImage = syntheticPng();
        var session = applicationService.requestEvidenceUpload(
                owner.getId(),
                evidenceRequest(
                        KycEvidenceType.DOCUMENT,
                        KycDocumentType.PASSPORT,
                        KycDocumentSide.PHOTO_PAGE,
                        KycCaptureMethod.CAMERA_CAPTURE,
                        syntheticImage
                )
        );
        Customer otherCustomer = saveCompleteCustomer();

        KycWorkflowException exception = assertThrows(
                KycWorkflowException.class,
                () -> applicationService.completeEvidenceUpload(
                        otherCustomer.getId(),
                        session.evidenceId(),
                        new CompleteKycEvidenceUploadRequest(sha256(syntheticImage))
                )
        );

        assertEquals("KYC_EVIDENCE_NOT_FOUND", exception.getCode().name());
    }

    @Test
    void unsupportedEvidenceMimeTypeIsRejectedBeforeStorageIsRequested() {
        Customer customer = saveCompleteCustomer();
        applicationService.startApplication(customer.getId());
        applicationService.updateDraft(
                customer.getId(),
                new UpdateKycApplicationDraftRequest(
                        KycDraftStep.SELECT_DOCUMENT,
                        KycDocumentType.PASSPORT
                )
        );

        KycWorkflowException exception = assertThrows(
                KycWorkflowException.class,
                () -> applicationService.requestEvidenceUpload(
                        customer.getId(),
                        new RequestKycEvidenceUploadRequest(
                                KycEvidenceType.DOCUMENT,
                                KycDocumentType.PASSPORT,
                                KycDocumentSide.PHOTO_PAGE,
                                KycCaptureMethod.CAMERA_CAPTURE,
                                "image/svg+xml",
                                128,
                                "a".repeat(64)
                        )
                )
        );

        assertEquals("KYC_EVIDENCE_INVALID", exception.getCode().name());
    }

    @Test
    void replacingAnEditableEvidenceSlotSoftRemovesThePreviousItem()
            throws IOException {
        Customer customer = saveCompleteCustomer();
        applicationService.startApplication(customer.getId());
        applicationService.updateDraft(
                customer.getId(),
                new UpdateKycApplicationDraftRequest(
                        KycDraftStep.SELECT_DOCUMENT,
                        KycDocumentType.PASSPORT
                )
        );
        byte[] image = syntheticPng();
        var first = applicationService.requestEvidenceUpload(
                customer.getId(),
                evidenceRequest(
                        KycEvidenceType.DOCUMENT,
                        KycDocumentType.PASSPORT,
                        KycDocumentSide.PHOTO_PAGE,
                        KycCaptureMethod.CAMERA_CAPTURE,
                        image
                )
        );
        var replacement = applicationService.requestEvidenceUpload(
                customer.getId(),
                evidenceRequest(
                        KycEvidenceType.DOCUMENT,
                        KycDocumentType.PASSPORT,
                        KycDocumentSide.PHOTO_PAGE,
                        KycCaptureMethod.GALLERY_UPLOAD,
                        image
                )
        );

        assertEquals(
                KycEvidenceUploadStatus.REMOVED,
                documentRepository.findById(first.evidenceId())
                        .orElseThrow()
                        .getUploadStatus()
        );
        assertEquals(
                KycEvidenceUploadStatus.REQUESTED,
                documentRepository.findById(replacement.evidenceId())
                        .orElseThrow()
                        .getUploadStatus()
        );
    }

    @Test
    void getCurrentReturnsOnlyTheAuthenticatedCustomersApplication()
            throws Exception {
        Customer customer = saveCompleteCustomer();
        KycApplicationResponse started = applicationService.startApplication(
                customer.getId()
        );

        mockMvc.perform(
                        get("/api/v1/kyc/applications/current")
                                .with(authenticatedAs(customer.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(started.id().toString())))
                .andExpect(jsonPath("$.attemptNumber", is(1)))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
    }

    @Test
    void customerCannotRetrieveAnotherCustomersApplication()
            throws Exception {
        Customer owner = saveCompleteCustomer();
        applicationService.startApplication(owner.getId());
        Customer otherCustomer = saveCompleteCustomer();

        mockMvc.perform(
                        get("/api/v1/kyc/applications/current")
                                .with(authenticatedAs(otherCustomer.getId()))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(
                        "$.code",
                        is("KYC_APPLICATION_NOT_FOUND")
                ));
    }

    @Test
    void getCurrentReturnsApplicationNotFoundWhenNoneExists()
            throws Exception {
        Customer customer = saveCompleteCustomer();

        mockMvc.perform(
                        get("/api/v1/kyc/applications/current")
                                .with(authenticatedAs(customer.getId()))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(
                        "$.code",
                        is("KYC_APPLICATION_NOT_FOUND")
                ));
    }

    @Test
    void endpointsRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/kyc/applications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/kyc/applications/current"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        patch("/api/v1/kyc/applications/current")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentStep\":\"CONFIRM_INFORMATION\"}")
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/v1/kyc/applications/current/evidence/upload-requests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/v1/kyc/applications/current/evidence/"
                                + UUID.randomUUID() + "/complete")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        delete("/api/v1/kyc/applications/current/evidence/"
                                + UUID.randomUUID())
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/kyc/applications/current/submit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startEndpointCreatesTheAuthenticatedCustomersApplication()
            throws Exception {
        Customer customer = saveCompleteCustomer();

        mockMvc.perform(
                        post("/api/v1/kyc/applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .with(authenticatedAs(customer.getId()))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNumber", is(1)))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
    }

    private void completeEvidence(
            UUID customerId,
            KycEvidenceType evidenceType,
            KycDocumentType documentType,
            KycDocumentSide documentSide,
            KycCaptureMethod captureMethod,
            byte[] image
    ) {
        var session = applicationService.requestEvidenceUpload(
                customerId,
                evidenceRequest(
                        evidenceType,
                        documentType,
                        documentSide,
                        captureMethod,
                        image
                )
        );
        KycDocument evidence = documentRepository.findById(session.evidenceId())
                .orElseThrow();
        testStorage().put(evidence.getStorageKey(), "image/png", image);

        KycApplicationResponse response = applicationService
                .completeEvidenceUpload(
                        customerId,
                        evidence.getId(),
                        new CompleteKycEvidenceUploadRequest(sha256(image))
                );

        assertEquals(
                KycEvidenceUploadStatus.VALIDATED,
                response.documents()
                        .stream()
                        .filter(document -> document.id().equals(evidence.getId()))
                        .findFirst()
                        .orElseThrow()
                        .status()
        );
    }

    private RequestKycEvidenceUploadRequest evidenceRequest(
            KycEvidenceType evidenceType,
            KycDocumentType documentType,
            KycDocumentSide documentSide,
            KycCaptureMethod captureMethod,
            byte[] image
    ) {
        return new RequestKycEvidenceUploadRequest(
                evidenceType,
                documentType,
                documentSide,
                captureMethod,
                "image/png",
                image.length,
                sha256(image)
        );
    }

    private InMemoryKycEvidenceStorage testStorage() {
        return (InMemoryKycEvidenceStorage) evidenceStorage;
    }

    private byte[] syntheticPng() throws IOException {
        BufferedImage image = new BufferedImage(
                2,
                2,
                BufferedImage.TYPE_INT_RGB
        );
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
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                result.append(String.format("%02x", current));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Customer saveCompleteCustomer() {
        Customer customer = newCustomer(UUID.randomUUID().toString());
        customer.updateProfile(
                "Ada",
                "Lovelace",
                "en",
                LocalDate.of(1995, 4, 12),
                "CD",
                Gender.FEMALE,
                "1 Mavela Avenue",
                "Kinshasa",
                "Kinshasa",
                Instant.now()
        );
        return customerRepository.saveAndFlush(customer);
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

    private void insertLegacyActiveCustomer(
            String schema,
            UUID customerId,
            String phoneNumber,
            String kycStatus
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO %s.customers (
                    id,
                    phone_number,
                    first_name,
                    last_name,
                    preferred_locale,
                    status,
                    kyc_status,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.formatted(schema),
                customerId,
                phoneNumber,
                "Legacy",
                "Customer",
                "en",
                "ACTIVE",
                kycStatus
        );
    }

    private String customerKycStatus(String schema, UUID customerId) {
        return jdbcTemplate.queryForObject(
                "SELECT kyc_status FROM %s.customers WHERE id = ?"
                        .formatted(schema),
                String.class,
                customerId
        );
    }

    private Customer newCustomer(String suffix) {
        String digits = String.valueOf(Math.abs(suffix.hashCode()));
        String phoneNumber = "+2438" + String.format(
                "%08d",
                Long.parseLong(digits) % 100000000L
        );
        return new Customer(
                phoneNumber,
                null,
                "Ada",
                "Lovelace",
                "en"
        );
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor
    authenticatedAs(UUID customerId) {
        return jwt().jwt(jwt -> jwt.subject(customerId.toString()));
    }
}
