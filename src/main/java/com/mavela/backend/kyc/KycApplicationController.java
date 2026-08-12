package com.mavela.backend.kyc;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
            description = "Creates the first KYC application after the authenticated customer completes their profile."
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
                    description = "The customer profile is incomplete or KYC cannot be started."
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
