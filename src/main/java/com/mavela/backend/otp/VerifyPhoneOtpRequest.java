package com.mavela.backend.otp;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record VerifyPhoneOtpRequest(

        @Schema(
                description = "Identifier of the active verification challenge."
        )
        @NotNull(message = "OTP_CHALLENGE_ID_REQUIRED")
        UUID challengeId,

        @Schema(
                description = "Six-digit verification code delivered to the customer's phone.",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @NotBlank(message = "OTP_CODE_REQUIRED")
        @Pattern(
                regexp = "^[0-9]{6}$",
                message = "OTP_CODE_INVALID_FORMAT"
        )
        String code
) {
}
