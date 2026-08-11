package com.mavela.backend.otp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/v1/customers/{customerId}/phone-verification"
)
@Tag(
        name = "Phone Verification",
        description = "Phone verification challenges for customer onboarding."
)
public class PhoneOtpController {

    private final PhoneOtpService phoneOtpService;

    public PhoneOtpController(
            PhoneOtpService phoneOtpService
    ) {
        this.phoneOtpService = phoneOtpService;
    }

    @PostMapping("/otp")
    @Operation(
            summary = "Request a phone verification code",
            description = "Issues a time-limited verification code to the customer's phone number."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Verification challenge created."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The customer does not exist."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The customer's phone number has already been verified."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "A verification code was requested too recently."
            )
    })
    public ResponseEntity<PhoneOtpIssuedResponse> requestOtp(
            @Parameter(
                    description = "Identifier of the customer being verified."
            ) @PathVariable UUID customerId
    ) {
        PhoneOtpIssuedResponse response =
                phoneOtpService.requestOtp(customerId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/otp/verify")
    @Operation(
            summary = "Verify a phone verification code",
            description = "Verifies the active challenge and returns a short-lived token for the remaining onboarding steps."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Phone verified successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The verification request or code is invalid."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The customer or active verification challenge does not exist."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The submitted challenge is not active."
            ),
            @ApiResponse(
                    responseCode = "410",
                    description = "The verification code has expired."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "The maximum number of verification attempts has been reached."
            )
    })
    public ResponseEntity<PhoneOtpVerificationResponse> verifyOtp(
            @Parameter(
                    description = "Identifier of the customer being verified."
            ) @PathVariable UUID customerId,
            @Valid @RequestBody VerifyPhoneOtpRequest request
    ) {
        PhoneOtpVerificationResponse response =
                phoneOtpService.verifyOtp(customerId, request);

        return ResponseEntity.ok(response);
    }
}
