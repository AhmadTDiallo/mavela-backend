package com.mavela.backend.kyc.review;

import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.kyc.storage.KycEvidenceStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Separate, staff-only KYC review API. Authentication is provided by the
 * isolated `/api/v1/admin/**` Cognito security chain; customer bearer tokens
 * never reach these handlers.
 */
@RestController
@RequestMapping("/api/v1/admin/kyc")
@Tag(
        name = "Administrator KYC Review",
        description = "Staff-only KYC review operations authenticated with a Cognito access token. All commands are version protected."
)
@SecurityRequirement(name = "adminBearerAuth")
public class KycAdminReviewController {

    private final KycAdminReviewService reviewService;

    public KycAdminReviewController(KycAdminReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/applications")
    @Operation(
            summary = "List the staff KYC review queue",
            description = "Returns a capped page of minimal triage data, ordered by oldest submission first unless submittedAtDesc is selected. PII lookup values are deliberately not placed in URLs."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review queue returned."),
            @ApiResponse(responseCode = "401", description = "A valid staff access token is required."),
            @ApiResponse(responseCode = "403", description = "The staff identity is inactive, unprovisioned, or lacks permission.")
    })
    public ResponseEntity<AdminKycQueueResponse> findQueue(
            @RequestParam(required = false) KycStatus status,
            @RequestParam(required = false) UUID assignedReviewerId,
            @RequestParam(required = false) LocalDate submittedFrom,
            @RequestParam(required = false) LocalDate submittedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "submittedAtAsc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(reviewService.findQueue(
                staffSubject(jwt),
                status,
                assignedReviewerId,
                submittedFrom,
                submittedTo,
                page,
                size,
                sort
        ));
    }

    @GetMapping("/applications/{applicationId}")
    @Operation(
            summary = "Get staff KYC application details",
            description = "Returns profile snapshot, evidence metadata, assigned reviewer and immutable staff history. Reading the application is audited."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application details returned."),
            @ApiResponse(responseCode = "404", description = "Application is absent or inaccessible."),
            @ApiResponse(responseCode = "409", description = "Application review state changed.")
    })
    public ResponseEntity<AdminKycApplicationDetailResponse> getApplication(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Request-Id", required = false)
            @Parameter(in = ParameterIn.HEADER, description = "Optional client correlation UUID. Invalid values are replaced server-side.")
            String requestId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(reviewService.getApplicationDetail(
                staffSubject(jwt),
                applicationId,
                correlationId(requestId)
        ));
    }

    @PostMapping("/applications/{applicationId}/claim")
    @Operation(
            summary = "Claim a submitted KYC application",
            description = "Atomically assigns the submitted case to the authenticated staff reviewer and moves it to UNDER_REVIEW."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application claimed."),
            @ApiResponse(responseCode = "409", description = "The application is no longer claimable or another reviewer won the race.")
    })
    public ResponseEntity<AdminKycApplicationDetailResponse> claim(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Request-Id", required = false)
            String requestId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(reviewService.claim(
                staffSubject(jwt), applicationId, correlationId(requestId)
        ));
    }

    @PostMapping("/applications/{applicationId}/release")
    @Operation(
            summary = "Release a claimed application",
            description = "Supervisor-only command. Releases the assignment and returns the case to SUBMITTED so another reviewer may explicitly claim it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application released."),
            @ApiResponse(responseCode = "409", description = "Version is stale or the application is not assigned under review.")
    })
    public ResponseEntity<AdminKycApplicationDetailResponse> release(
            @PathVariable UUID applicationId,
            @Valid @RequestBody ReleaseKycApplicationRequest request,
            @RequestHeader(value = "X-Request-Id", required = false)
            String requestId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(reviewService.release(
                staffSubject(jwt), applicationId, request, correlationId(requestId)
        ));
    }

    @GetMapping("/applications/{applicationId}/evidence/{evidenceId}")
    @Operation(
            summary = "Stream one authorized private evidence image",
            description = "Streams an already validated JPEG or PNG through the authenticated backend. The response never exposes storage keys or URLs and is explicitly non-cacheable. Viewing is audited."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Private image stream returned.",
                    content = {
                            @Content(mediaType = "image/jpeg"),
                            @Content(mediaType = "image/png")
                    }
            ),
            @ApiResponse(responseCode = "404", description = "Evidence is absent or inaccessible.")
    })
    public ResponseEntity<StreamingResponseBody> streamEvidence(
            @PathVariable UUID applicationId,
            @PathVariable UUID evidenceId,
            @RequestHeader(value = "X-Request-Id", required = false)
            String requestId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        KycEvidenceStorage.EvidenceStream evidence = reviewService.openEvidence(
                staffSubject(jwt),
                applicationId,
                evidenceId,
                correlationId(requestId)
        );
        StreamingResponseBody body = outputStream -> {
            try (evidence) {
                evidence.inputStream().transferTo(outputStream);
            } catch (IOException exception) {
                throw exception;
            }
        };
        String extension = "image/png".equals(evidence.mimeType()) ? "png" : "jpg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(evidence.mimeType()))
                .contentLength(evidence.byteSize())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(
                                        "evidence-" + evidenceId + "." + extension,
                                        StandardCharsets.US_ASCII
                                ).build().toString()
                )
                .body(body);
    }

    @PostMapping("/applications/{applicationId}/approve")
    @Operation(
            summary = "Approve an assigned KYC application",
            description = "Requires the application version and revalidates customer profile, required evidence, camera selfie and private storage before atomically approving application and customer KYC status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application approved."),
            @ApiResponse(responseCode = "409", description = "Version is stale, reviewer assignment differs, state changed, or readiness validation fails."),
            @ApiResponse(responseCode = "422", description = "Command payload is invalid.")
    })
    public ResponseEntity<AdminKycApplicationDetailResponse> approve(
            @PathVariable UUID applicationId,
            @Valid @RequestBody ApproveKycApplicationRequest request,
            @RequestHeader(value = "X-Request-Id", required = false)
            String requestId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(reviewService.approve(
                staffSubject(jwt), applicationId, request, correlationId(requestId)
        ));
    }

    @PostMapping("/applications/{applicationId}/request-resubmission")
    @Operation(
            summary = "Request customer KYC resubmission",
            description = "Requires a non-terminal structured reason, customer-safe message, version, and at least one explicit customer correction requirement. Internal notes and evidence references remain staff-only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resubmission requested."),
            @ApiResponse(responseCode = "409", description = "Version is stale, reviewer assignment differs, or state changed."),
            @ApiResponse(responseCode = "422", description = "A structured reason, safe customer message, and one or more correction requirements are required.")
    })
    public ResponseEntity<AdminKycApplicationDetailResponse> requestResubmission(
            @PathVariable UUID applicationId,
            @Valid @RequestBody RequestKycResubmissionRequest request,
            @RequestHeader(value = "X-Request-Id", required = false)
            String requestId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(reviewService.requestResubmission(
                staffSubject(jwt), applicationId, request, correlationId(requestId)
        ));
    }

    @PostMapping("/applications/{applicationId}/reject")
    @Operation(
            summary = "Terminally reject an assigned KYC application",
            description = "Accepts only a terminal structured reason. Correctable evidence problems must use request-resubmission instead."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application rejected."),
            @ApiResponse(responseCode = "409", description = "Version is stale, reviewer assignment differs, or state changed."),
            @ApiResponse(responseCode = "422", description = "A terminal reason and customer-safe message are required.")
    })
    public ResponseEntity<AdminKycApplicationDetailResponse> reject(
            @PathVariable UUID applicationId,
            @Valid @RequestBody RejectKycApplicationRequest request,
            @RequestHeader(value = "X-Request-Id", required = false)
            String requestId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(reviewService.reject(
                staffSubject(jwt), applicationId, request, correlationId(requestId)
        ));
    }

    @GetMapping("/applications/{applicationId}/history")
    @Operation(
            summary = "Get immutable staff review history",
            description = "Returns server-recorded audit actions only; no endpoint exists to update or delete history."
    )
    public ResponseEntity<List<AdminKycReviewEventResponse>> history(
            @PathVariable UUID applicationId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(reviewService.history(
                staffSubject(jwt), applicationId
        ));
    }

    private String staffSubject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            // Security normally rejects this before the controller. This avoids
            // an accidental null actor if it is invoked by a test harness.
            throw new KycAdminReviewException(
                    com.mavela.backend.error.ApiErrorCode.ADMIN_AUTHENTICATION_REQUIRED,
                    HttpStatus.UNAUTHORIZED
            );
        }
        return jwt.getSubject();
    }

    private UUID correlationId(String value) {
        if (value != null && !value.isBlank()) {
            try {
                return UUID.fromString(value.trim());
            } catch (IllegalArgumentException ignored) {
                // A caller-controlled malformed request ID is never persisted.
            }
        }
        return UUID.randomUUID();
    }
}
