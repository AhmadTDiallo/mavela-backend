package com.mavela.backend.otp;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.error.ApiErrorCode;
import com.mavela.backend.pin.PinSetupTokenManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PhoneOtpService {

    private static final Duration OTP_LIFETIME =
            Duration.ofMinutes(5);

    private static final Duration RESEND_COOLDOWN =
            Duration.ofSeconds(60);

    private static final int MAX_ATTEMPTS = 5;

    private final CustomerRepository customerRepository;
    private final PhoneOtpChallengeRepository challengeRepository;
    private final PhoneOtpGenerator otpGenerator;
    private final PhoneOtpHasher otpHasher;
    private final PhoneOtpSender otpSender;
    private final PinSetupTokenManager pinSetupTokenManager;

    public PhoneOtpService(
            CustomerRepository customerRepository,
            PhoneOtpChallengeRepository challengeRepository,
            PhoneOtpGenerator otpGenerator,
            PhoneOtpHasher otpHasher,
            PhoneOtpSender otpSender,
            PinSetupTokenManager pinSetupTokenManager
    ) {
        this.customerRepository = customerRepository;
        this.challengeRepository = challengeRepository;
        this.otpGenerator = otpGenerator;
        this.otpHasher = otpHasher;
        this.otpSender = otpSender;
        this.pinSetupTokenManager = pinSetupTokenManager;
    }

    @Transactional
    public PhoneOtpIssuedResponse requestOtp(UUID customerId) {
        Instant now = Instant.now();

        Customer customer = customerRepository
                .findByIdForUpdate(customerId)
                .orElseThrow(() -> new PhoneOtpException(
                        ApiErrorCode.CUSTOMER_NOT_FOUND,
                        HttpStatus.NOT_FOUND
                ));

        if (customer.isPhoneVerified()) {
            throw new PhoneOtpException(
                    ApiErrorCode.PHONE_ALREADY_VERIFIED,
                    HttpStatus.CONFLICT
            );
        }

        challengeRepository
                .findFirstByCustomer_IdOrderByCreatedAtDesc(customerId)
                .ifPresent(latestChallenge ->
                        enforceResendCooldown(latestChallenge, now)
                );

        challengeRepository
                .findFirstByCustomer_IdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                        customerId
                )
                .ifPresent(activeChallenge ->
                        activeChallenge.invalidate(now)
                );

        challengeRepository.flush();

        UUID challengeId = UUID.randomUUID();
        String code = otpGenerator.generate();
        String codeHash = otpHasher.hash(challengeId, code);
        Instant expiresAt = now.plus(OTP_LIFETIME);

        PhoneOtpChallenge challenge = new PhoneOtpChallenge(
                challengeId,
                customer,
                codeHash,
                expiresAt,
                MAX_ATTEMPTS,
                now
        );

        challengeRepository.saveAndFlush(challenge);

        otpSender.send(
                customer.getPhoneNumber(),
                code,
                customer.getPreferredLocale()
        );

        return new PhoneOtpIssuedResponse(
                challengeId,
                maskPhoneNumber(customer.getPhoneNumber()),
                expiresAt,
                now.plus(RESEND_COOLDOWN)
        );
    }

    @Transactional(noRollbackFor = PhoneOtpException.class)
    public PhoneOtpVerificationResponse verifyOtp(
            UUID customerId,
            VerifyPhoneOtpRequest request
    ) {
        Instant now = Instant.now();

        Customer customer = customerRepository
                .findByIdForUpdate(customerId)
                .orElseThrow(() -> new PhoneOtpException(
                        ApiErrorCode.CUSTOMER_NOT_FOUND,
                        HttpStatus.NOT_FOUND
                ));

        /*
         * If verification previously succeeded but the response was lost,
         * the same valid challenge and OTP can issue a replacement
         * short-lived PIN setup token.
         */
        if (customer.isPhoneVerified()) {
            return retryVerifiedOtp(
                    customer,
                    request,
                    now
            );
        }

        PhoneOtpChallenge challenge = challengeRepository
                .findFirstByCustomer_IdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                        customerId
                )
                .orElseThrow(() -> new PhoneOtpException(
                        ApiErrorCode.OTP_CHALLENGE_NOT_FOUND,
                        HttpStatus.NOT_FOUND
                ));

        if (!challenge.getId().equals(request.challengeId())) {
            throw new PhoneOtpException(
                    ApiErrorCode.OTP_CHALLENGE_NOT_ACTIVE,
                    HttpStatus.CONFLICT
            );
        }

        validateChallengeLifetimeAndAttempts(
                challenge,
                now,
                true
        );

        verifyCode(challenge, request.code());

        challenge.consume(now);
        customer.markPhoneVerified(now);

        PinSetupTokenManager.IssuedPinSetupToken issuedToken =
                pinSetupTokenManager.issue(customer, now);

        return createVerificationResponse(
                customer,
                issuedToken
        );
    }

    private PhoneOtpVerificationResponse retryVerifiedOtp(
            Customer customer,
            VerifyPhoneOtpRequest request,
            Instant now
    ) {
        PhoneOtpChallenge challenge = challengeRepository
                .findByIdAndCustomer_Id(
                        request.challengeId(),
                        customer.getId()
                )
                .orElseThrow(() -> new PhoneOtpException(
                        ApiErrorCode.OTP_CHALLENGE_NOT_FOUND,
                        HttpStatus.NOT_FOUND
                ));

        if (challenge.getConsumedAt() == null
                || challenge.getInvalidatedAt() != null) {
            throw new PhoneOtpException(
                    ApiErrorCode.OTP_CHALLENGE_NOT_ACTIVE,
                    HttpStatus.CONFLICT
            );
        }

        validateChallengeLifetimeAndAttempts(
                challenge,
                now,
                false
        );

        verifyCode(challenge, request.code());

        PinSetupTokenManager.IssuedPinSetupToken issuedToken =
                pinSetupTokenManager.issue(customer, now);

        return createVerificationResponse(
                customer,
                issuedToken
        );
    }

    private void validateChallengeLifetimeAndAttempts(
            PhoneOtpChallenge challenge,
            Instant now,
            boolean invalidateWhenExpired
    ) {
        if (challenge.isExpired(now)) {
            if (invalidateWhenExpired) {
                challenge.invalidate(now);
            }

            throw new PhoneOtpException(
                    ApiErrorCode.OTP_EXPIRED,
                    HttpStatus.GONE
            );
        }

        if (!challenge.hasAttemptsRemaining()) {
            throw new PhoneOtpException(
                    ApiErrorCode.OTP_ATTEMPTS_EXCEEDED,
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    private void verifyCode(
            PhoneOtpChallenge challenge,
            String providedCode
    ) {
        boolean matches = otpHasher.matches(
                challenge.getId(),
                providedCode,
                challenge.getCodeHash()
        );

        if (matches) {
            return;
        }

        challenge.recordFailedAttempt();

        int remainingAttempts =
                challenge.getRemainingAttempts();

        if (remainingAttempts == 0) {
            throw new PhoneOtpException(
                    ApiErrorCode.OTP_ATTEMPTS_EXCEEDED,
                    HttpStatus.TOO_MANY_REQUESTS,
                    Map.of("remainingAttempts", 0)
            );
        }

        throw new PhoneOtpException(
                ApiErrorCode.OTP_INVALID_CODE,
                HttpStatus.BAD_REQUEST,
                Map.of(
                        "remainingAttempts",
                        remainingAttempts
                )
        );
    }

    private void enforceResendCooldown(
            PhoneOtpChallenge latestChallenge,
            Instant now
    ) {
        Instant resendAvailableAt = latestChallenge
                .getCreatedAt()
                .plus(RESEND_COOLDOWN);

        if (!now.isBefore(resendAvailableAt)) {
            return;
        }

        Duration remaining = Duration.between(
                now,
                resendAvailableAt
        );

        long retryAfterSeconds = Math.max(
                1,
                (remaining.toMillis() + 999) / 1000
        );

        throw new PhoneOtpException(
                ApiErrorCode.OTP_RESEND_TOO_SOON,
                HttpStatus.TOO_MANY_REQUESTS,
                Map.of(
                        "retryAfterSeconds",
                        retryAfterSeconds,
                        "resendAvailableAt",
                        resendAvailableAt
                )
        );
    }

    private PhoneOtpVerificationResponse
    createVerificationResponse(
            Customer customer,
            PinSetupTokenManager.IssuedPinSetupToken issuedToken
    ) {
        return new PhoneOtpVerificationResponse(
                customer.getId(),
                customer.getPhoneVerifiedAt(),
                customer.getStatus(),
                issuedToken.token(),
                issuedToken.expiresAt()
        );
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() <= 4) {
            return "****";
        }

        int visibleStart = phoneNumber.length() - 4;

        return "*".repeat(visibleStart)
                + phoneNumber.substring(visibleStart);
    }
}