package com.mavela.backend.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record VerifyPhoneOtpRequest(

        @NotNull(message = "OTP_CHALLENGE_ID_REQUIRED")
        UUID challengeId,

        @NotBlank(message = "OTP_CODE_REQUIRED")
        @Pattern(
                regexp = "^[0-9]{6}$",
                message = "OTP_CODE_INVALID_FORMAT"
        )
        String code
) {
}