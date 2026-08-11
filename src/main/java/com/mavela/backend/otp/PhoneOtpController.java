package com.mavela.backend.otp;

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
public class PhoneOtpController {

    private final PhoneOtpService phoneOtpService;

    public PhoneOtpController(
            PhoneOtpService phoneOtpService
    ) {
        this.phoneOtpService = phoneOtpService;
    }

    @PostMapping("/otp")
    public ResponseEntity<PhoneOtpIssuedResponse> requestOtp(
            @PathVariable UUID customerId
    ) {
        PhoneOtpIssuedResponse response =
                phoneOtpService.requestOtp(customerId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<PhoneOtpVerificationResponse> verifyOtp(
            @PathVariable UUID customerId,
            @Valid @RequestBody VerifyPhoneOtpRequest request
    ) {
        PhoneOtpVerificationResponse response =
                phoneOtpService.verifyOtp(customerId, request);

        return ResponseEntity.ok(response);
    }
}