package com.mavela.backend;

import com.mavela.backend.admin.staff.StaffUser;
import com.mavela.backend.admin.staff.StaffUserRepository;
import com.mavela.backend.admin.staff.StaffUserStatus;
import com.mavela.backend.auth.AccessTokenService;
import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.customer.Gender;
import com.mavela.backend.kyc.CompleteKycEvidenceUploadRequest;
import com.mavela.backend.kyc.KycApplicationRepository;
import com.mavela.backend.kyc.KycApplicationService;
import com.mavela.backend.kyc.KycCaptureMethod;
import com.mavela.backend.kyc.KycDocument;
import com.mavela.backend.kyc.KycDocumentRepository;
import com.mavela.backend.kyc.KycDocumentSide;
import com.mavela.backend.kyc.KycDocumentType;
import com.mavela.backend.kyc.KycDraftStep;
import com.mavela.backend.kyc.KycEvidenceType;
import com.mavela.backend.kyc.RequestKycEvidenceUploadRequest;
import com.mavela.backend.kyc.UpdateKycApplicationDraftRequest;
import com.mavela.backend.kyc.review.KycReviewEventRepository;
import com.mavela.backend.kyc.storage.KycEvidenceStorage;
import com.mavela.backend.kyc.support.InMemoryKycEvidenceStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses a decoder override rather than a real Cognito issuer. The production
 * chain itself remains configured with issuer, token-use and client-id rules.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "mavela.admin.auth.enabled=true",
        "mavela.admin.auth.issuer-uri=https://cognito.example.test/staff-pool",
        "mavela.admin.auth.client-id=staff-client-id"
})
@AutoConfigureMockMvc
class AdminAuthenticationEnabledIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private KycApplicationRepository applicationRepository;

    @Autowired
    private KycDocumentRepository documentRepository;

    @Autowired
    private KycReviewEventRepository reviewEventRepository;

    @Autowired
    private KycApplicationService kycApplicationService;

    @Autowired
    private KycEvidenceStorage evidenceStorage;

    @Autowired
    private AccessTokenService accessTokenService;

    @MockitoBean(name = "adminJwtDecoder")
    private JwtDecoder adminJwtDecoder;

    @AfterEach
    void cleanStaffUsers() {
        reviewEventRepository.deleteAll();
        documentRepository.deleteAll();
        applicationRepository.deleteAll();
        staffUserRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void unauthenticatedAdminRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/kyc/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(
                        "ADMIN_AUTHENTICATION_REQUIRED"
                ));
    }

    @Test
    void customerBearerTokenCannotAccessAdminRoute() throws Exception {
        Customer customer = customerRepository.save(new Customer(
                "+243810000091",
                "customer.authz@example.test",
                "Customer",
                "Token",
                "en"
        ));
        String customerAccessToken = accessTokenService.issue(
                customer,
                Instant.now()
        ).value();
        when(adminJwtDecoder.decode(customerAccessToken))
                .thenThrow(new BadJwtException("Unknown administrator token"));

        mockMvc.perform(get("/api/v1/admin/kyc/applications")
                        .header("Authorization",
                                "Bearer " + customerAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(
                        "ADMIN_AUTHENTICATION_REQUIRED"
                ));
    }

    @Test
    void tokenWithoutTrustedKycGroupIsForbidden() throws Exception {
        when(adminJwtDecoder.decode("platform-admin-token"))
                .thenReturn(adminJwt(
                        "platform-admin-subject",
                        List.of("PLATFORM_ADMIN")
                ));

        mockMvc.perform(get("/api/v1/admin/kyc/applications")
                        .header("Authorization",
                                "Bearer platform-admin-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(
                        "ADMIN_PERMISSION_DENIED"
                ));
    }

    @Test
    void tokenWithOnlyUnknownGroupsIsForbidden() throws Exception {
        when(adminJwtDecoder.decode("unknown-group-token"))
                .thenReturn(adminJwt(
                        "unknown-group-subject",
                        List.of("UNTRUSTED_GROUP")
                ));

        mockMvc.perform(get("/api/v1/admin/kyc/applications")
                        .header("Authorization", "Bearer unknown-group-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(
                        "ADMIN_PERMISSION_DENIED"
                ));
    }

    @Test
    void platformAdminWithoutKycGroupCannotViewEvidence() throws Exception {
        when(adminJwtDecoder.decode("platform-admin-evidence-token"))
                .thenReturn(adminJwt(
                        "platform-admin-evidence-subject",
                        List.of("PLATFORM_ADMIN")
                ));

        mockMvc.perform(get(
                        "/api/v1/admin/kyc/applications/{applicationId}/evidence/{evidenceId}",
                        UUID.randomUUID(),
                        UUID.randomUUID()
                ).header("Authorization", "Bearer platform-admin-evidence-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(
                        "ADMIN_PERMISSION_DENIED"
                ));
    }

    @Test
    void unprovisionedTrustedReviewerIsForbidden() throws Exception {
        when(adminJwtDecoder.decode("unprovisioned-reviewer-token"))
                .thenReturn(adminJwt(
                        "unprovisioned-reviewer-subject",
                        List.of("KYC_REVIEWER")
                ));

        mockMvc.perform(get("/api/v1/admin/kyc/applications")
                        .header("Authorization",
                                "Bearer unprovisioned-reviewer-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(
                        "ADMIN_STAFF_NOT_PROVISIONED"
                ));
    }

    @Test
    void disabledStaffReviewerIsForbidden() throws Exception {
        staffUserRepository.save(new StaffUser(
                "disabled-reviewer-subject",
                "disabled.reviewer@example.test",
                "Disabled Reviewer",
                StaffUserStatus.DISABLED
        ));
        when(adminJwtDecoder.decode("disabled-reviewer-token"))
                .thenReturn(adminJwt(
                        "disabled-reviewer-subject",
                        List.of("KYC_REVIEWER")
                ));

        mockMvc.perform(get("/api/v1/admin/kyc/applications")
                        .header("Authorization",
                                "Bearer disabled-reviewer-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(
                        "ADMIN_STAFF_ACCOUNT_INACTIVE"
                ));
    }

    @Test
    void activeTrustedReviewerCanReadEmptyQueue() throws Exception {
        staffUserRepository.save(new StaffUser(
                "active-reviewer-subject",
                "active.reviewer@example.test",
                "Active Reviewer",
                StaffUserStatus.ACTIVE
        ));
        when(adminJwtDecoder.decode("active-reviewer-token"))
                .thenReturn(adminJwt(
                        "active-reviewer-subject",
                        List.of("KYC_REVIEWER")
                ));

        mockMvc.perform(get("/api/v1/admin/kyc/applications")
                        .header("Authorization",
                                "Bearer active-reviewer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void authorizedReviewerStreamsEvidenceWithSafePrivateHeaders()
            throws Exception {
        SubmittedEvidence submitted = submittedEvidence();
        StaffUser reviewer = staffUserRepository.saveAndFlush(new StaffUser(
                "evidence-viewer-subject",
                "evidence.viewer@example.test",
                "Evidence Viewer",
                StaffUserStatus.ACTIVE
        ));
        when(adminJwtDecoder.decode("evidence-viewer-token"))
                .thenReturn(adminJwt(
                        reviewer.getExternalSubject(),
                        List.of("KYC_REVIEWER")
                ));

        mockMvc.perform(post(
                        "/api/v1/admin/kyc/applications/{applicationId}/claim",
                        submitted.applicationId()
                ).header("Authorization", "Bearer evidence-viewer-token"))
                .andExpect(status().isOk());

        MvcResult initial = mockMvc.perform(get(
                        "/api/v1/admin/kyc/applications/{applicationId}/evidence/{evidenceId}",
                        submitted.applicationId(),
                        submitted.evidence().getId()
                ).header("Authorization", "Bearer evidence-viewer-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.IMAGE_PNG_VALUE
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        allOf(containsString("no-store"),
                                containsString("private"))
                ))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(
                        "X-Content-Type-Options",
                        "nosniff"
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        allOf(
                                containsString("inline"),
                                containsString(
                                        "evidence-" + submitted.evidence().getId()
                                                + ".png"
                                ),
                                not(containsString(
                                        submitted.evidence().getStorageKey()
                                ))
                        )
                ))
                .andReturn();

        MvcResult completed = initial.getRequest().isAsyncStarted()
                ? mockMvc.perform(asyncDispatch(initial))
                        .andExpect(status().isOk())
                        .andReturn()
                : initial;
        byte[] body = completed.getResponse().getContentAsByteArray();
        assertArrayEquals(submitted.image(), body);
        assertFalse(new String(body, StandardCharsets.ISO_8859_1)
                .contains(submitted.evidence().getStorageKey()));
    }

    private SubmittedEvidence submittedEvidence() throws IOException {
        Customer customer = new Customer(
                "+2438" + String.format(
                        "%08d",
                        Math.abs(UUID.randomUUID().hashCode()) % 100_000_000L
                ),
                null,
                "Ada",
                "Lovelace",
                "en"
        );
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
        customerRepository.saveAndFlush(customer);
        kycApplicationService.startApplication(customer.getId());
        kycApplicationService.updateDraft(
                customer.getId(),
                new UpdateKycApplicationDraftRequest(
                        KycDraftStep.SELECT_DOCUMENT,
                        KycDocumentType.NATIONAL_ID
                )
        );

        byte[] image = syntheticPng();
        completeEvidence(
                customer.getId(),
                KycEvidenceType.DOCUMENT,
                KycDocumentType.NATIONAL_ID,
                KycDocumentSide.FRONT,
                KycCaptureMethod.CAMERA_CAPTURE,
                image
        );
        completeEvidence(
                customer.getId(),
                KycEvidenceType.DOCUMENT,
                KycDocumentType.NATIONAL_ID,
                KycDocumentSide.BACK,
                KycCaptureMethod.GALLERY_UPLOAD,
                image
        );
        KycDocument selfie = completeEvidence(
                customer.getId(),
                KycEvidenceType.SELFIE,
                null,
                KycDocumentSide.NOT_APPLICABLE,
                KycCaptureMethod.CAMERA_CAPTURE,
                image
        );
        kycApplicationService.submitCurrentApplication(customer.getId());
        UUID applicationId = applicationRepository
                .findFirstByCustomer_IdOrderByAttemptNumberDesc(customer.getId())
                .orElseThrow()
                .getId();
        return new SubmittedEvidence(applicationId, selfie, image);
    }

    private KycDocument completeEvidence(
            UUID customerId,
            KycEvidenceType evidenceType,
            KycDocumentType documentType,
            KycDocumentSide documentSide,
            KycCaptureMethod captureMethod,
            byte[] image
    ) {
        var session = kycApplicationService.requestEvidenceUpload(
                customerId,
                new RequestKycEvidenceUploadRequest(
                        evidenceType,
                        documentType,
                        documentSide,
                        captureMethod,
                        "image/png",
                        image.length,
                        sha256(image)
                )
        );
        KycDocument evidence = documentRepository.findById(session.evidenceId())
                .orElseThrow();
        testStorage().put(evidence.getStorageKey(), "image/png", image);
        kycApplicationService.completeEvidenceUpload(
                customerId,
                evidence.getId(),
                new CompleteKycEvidenceUploadRequest(sha256(image))
        );
        return evidence;
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
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record SubmittedEvidence(
            UUID applicationId,
            KycDocument evidence,
            byte[] image
    ) {
    }

    private Jwt adminJwt(String subject, List<String> groups) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer("https://cognito.example.test/staff-pool")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("token_use", "access")
                .claim("client_id", "staff-client-id")
                .claim("cognito:groups", groups)
                .build();
    }
}
