package com.mavela.backend.kyc;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc/applications")
@Tag(
        name = "KYC Applications",
        description = "Authenticated customer KYC application workflow."
)
public class KycApplicationController {

    private final KycApplicationService applicationService;

    public KycApplicationController(
            KycApplicationService applicationService
    ) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @Operation(
            summary = "Start the current customer's KYC application",
            description = "Creates an editable KYC draft for the authenticated customer. A complete profile and evidence are required only for final submission."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "KYC application created successfully."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "The bearer token is missing, invalid, or does not identify a customer."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A KYC application already exists or KYC cannot be started."
            )
    })
    public ResponseEntity<KycApplicationResponse> startApplication(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        KycApplicationResponse response = applicationService.startApplication(
                extractCustomerId(jwt)
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/current")
    @Operation(
            summary = "Get the current customer's latest KYC application",
            description = "Returns only the newest KYC application belonging to the authenticated customer."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "KYC application returned successfully."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "The bearer token is missing, invalid, or does not identify a customer."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No KYC application exists for the authenticated customer."
            )
    })
    public ResponseEntity<KycApplicationResponse> getCurrentApplication(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        KycApplicationResponse response = applicationService
                .getCurrentApplication(extractCustomerId(jwt));

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/current")
    @Operation(
            summary = "Save KYC draft progress",
            description = "Stores the current customer-owned KYC step and selected document type without submitting the application."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft progress saved."),
            @ApiResponse(responseCode = "400", description = "The draft update is invalid."),
            @ApiResponse(responseCode = "401", description = "The bearer token is missing, invalid, or does not identify a customer."),
            @ApiResponse(responseCode = "404", description = "No KYC application exists for the authenticated customer."),
            @ApiResponse(responseCode = "409", description = "The KYC application is not editable.")
    })
    public ResponseEntity<KycApplicationResponse> updateCurrentApplication(
            @Valid @RequestBody UpdateKycApplicationDraftRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        KycApplicationResponse response = applicationService.updateDraft(
                extractCustomerId(jwt),
                request
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/current/evidence/upload-requests")
    @Operation(
            summary = "Request private KYC evidence upload instructions",
            description = "Creates an owned evidence slot and returns a short-lived direct-upload URL. The image itself is never sent to this API."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Upload instructions created."),
            @ApiResponse(responseCode = "400", description = "The evidence metadata is invalid."),
            @ApiResponse(responseCode = "401", description = "The bearer token is missing, invalid, or does not identify a customer."),
            @ApiResponse(responseCode = "404", description = "No KYC application exists for the authenticated customer."),
            @ApiResponse(responseCode = "409", description = "The KYC application is not editable or the requested evidence slot is invalid.")
    })
    public ResponseEntity<KycEvidenceUploadSessionResponse> requestEvidenceUpload(
            @Valid @RequestBody RequestKycEvidenceUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        KycEvidenceUploadSessionResponse response = applicationService
                .requestEvidenceUpload(extractCustomerId(jwt), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/current/evidence/{evidenceId}/complete")
    @Operation(
            summary = "Complete a KYC evidence upload",
            description = "Verifies a direct upload in private object storage before making the evidence available to the KYC draft."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evidence upload verified."),
            @ApiResponse(responseCode = "400", description = "The upload checksum or evidence is invalid."),
            @ApiResponse(responseCode = "401", description = "The bearer token is missing, invalid, or does not identify a customer."),
            @ApiResponse(responseCode = "404", description = "The evidence does not belong to the authenticated customer."),
            @ApiResponse(responseCode = "409", description = "The KYC application is not editable.")
    })
    public ResponseEntity<KycApplicationResponse> completeEvidenceUpload(
            @PathVariable UUID evidenceId,
            @Valid @RequestBody CompleteKycEvidenceUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        KycApplicationResponse response = applicationService
                .completeEvidenceUpload(
                        extractCustomerId(jwt),
                        evidenceId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/current/evidence/{evidenceId}")
    @Operation(
            summary = "Remove customer-owned draft KYC evidence",
            description = "Removes an editable evidence item from the draft and private object storage."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Evidence removed."),
            @ApiResponse(responseCode = "401", description = "The bearer token is missing, invalid, or does not identify a customer."),
            @ApiResponse(responseCode = "404", description = "The evidence does not belong to the authenticated customer."),
            @ApiResponse(responseCode = "409", description = "The KYC application is not editable.")
    })
    public ResponseEntity<Void> deleteEvidence(
            @PathVariable UUID evidenceId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        applicationService.deleteEvidence(
                extractCustomerId(jwt),
                evidenceId
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/current/submit")
    @Operation(
            summary = "Submit the current KYC application for review",
            description = "Validates the completed profile and required evidence, then transitions the application to SUBMITTED. Repeating a successful request is idempotent."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application submitted or already submitted."),
            @ApiResponse(responseCode = "401", description = "The bearer token is missing, invalid, or does not identify a customer."),
            @ApiResponse(responseCode = "404", description = "No KYC application exists for the authenticated customer."),
            @ApiResponse(responseCode = "409", description = "The profile or evidence requirements are incomplete, or the application cannot be submitted.")
    })
    public ResponseEntity<KycApplicationResponse> submitCurrentApplication(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        KycApplicationResponse response = applicationService
                .submitCurrentApplication(extractCustomerId(jwt));

        return ResponseEntity.ok(response);
    }

    private UUID extractCustomerId(Jwt jwt) {
        String subject = jwt.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_ACCESS_TOKEN_SUBJECT"
            );
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_ACCESS_TOKEN_SUBJECT"
            );
        }
    }
}
