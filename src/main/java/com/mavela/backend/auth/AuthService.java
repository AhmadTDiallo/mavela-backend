package com.mavela.backend.auth;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.customer.CustomerStatus;
import com.mavela.backend.error.ApiErrorCode;
import com.mavela.backend.pin.CustomerPinCredential;
import com.mavela.backend.pin.CustomerPinCredentialRepository;
import com.mavela.backend.pin.CustomerPinHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAXIMUM_PIN_ATTEMPTS = 5;

    private static final Duration PIN_LOCK_DURATION =
            Duration.ofMinutes(15);

    private static final UUID DUMMY_CUSTOMER_ID =
            UUID.fromString(
                    "00000000-0000-0000-0000-000000000001"
            );

    private final CustomerRepository customerRepository;
    private final CustomerPinCredentialRepository credentialRepository;
    private final CustomerPinHasher pinHasher;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    /*
     * Used to make an unknown phone number perform an Argon2
     * verification too, reducing phone-number enumeration through
     * response timing.
     */
    private final String dummyPinHash;

    public AuthService(
            CustomerRepository customerRepository,
            CustomerPinCredentialRepository credentialRepository,
            CustomerPinHasher pinHasher,
            AccessTokenService accessTokenService,
            RefreshTokenService refreshTokenService
    ) {
        this.customerRepository = customerRepository;
        this.credentialRepository = credentialRepository;
        this.pinHasher = pinHasher;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;

        this.dummyPinHash = pinHasher.hash(
                DUMMY_CUSTOMER_ID,
                "0000"
        );
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthLoginResponse login(
            AuthLoginRequest request
    ) {
        Instant now = Instant.now();

        Customer customer = customerRepository
                .findByPhoneNumberForUpdate(
                        request.phoneNumber()
                )
                .orElse(null);

        if (customer == null) {
            performDummyVerification(request.pin());
            throw invalidCredentials();
        }

        CustomerPinCredential credential =
                credentialRepository
                        .findByCustomerIdForUpdate(
                                customer.getId()
                        )
                        .orElse(null);

        if (credential == null) {
            performDummyVerification(request.pin());
            throw invalidCredentials();
        }

        if (credential.isLocked(now)) {
            throw lockedCredential(credential, now);
        }

        boolean pinMatches = pinHasher.matches(
                customer.getId(),
                request.pin(),
                credential.getPinHash()
        );

        if (!pinMatches) {
            credential.recordFailedAttempt(
                    now,
                    MAXIMUM_PIN_ATTEMPTS,
                    PIN_LOCK_DURATION
            );

            if (credential.isLocked(now)) {
                throw lockedCredential(credential, now);
            }

            throw invalidCredentials();
        }

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new AuthException(
                    ApiErrorCode.CUSTOMER_AUTHENTICATION_NOT_ALLOWED,
                    HttpStatus.FORBIDDEN
            );
        }

        credential.clearFailedAttempts(now);

        AccessTokenService.IssuedAccessToken accessToken =
                accessTokenService.issue(customer, now);

        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.issue(customer, now);

        return new AuthLoginResponse(
                customer.getId(),
                accessToken.value(),
                refreshToken.value(),
                "Bearer",
                accessToken.expiresInSeconds()
        );
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthRefreshResponse refresh(
            AuthRefreshRequest request
    ) {
        Instant now = Instant.now();

        AuthRefreshToken presentedToken =
                refreshTokenService
                        .findForUpdate(request.refreshToken())
                        .orElseThrow(
                                this::invalidRefreshToken
                        );

        /*
         * A consumed token has already been exchanged.
         * Seeing it again indicates replay or accidental reuse.
         */
        if (presentedToken.isConsumed()) {
            refreshTokenService.revokeFamily(
                    presentedToken.getFamilyId(),
                    now
            );

            throw refreshTokenReuseDetected();
        }

        if (presentedToken.isRevoked()) {
            throw invalidRefreshToken();
        }

        if (presentedToken.isExpired(now)) {
            presentedToken.revoke(now);
            throw invalidRefreshToken();
        }

        Customer customer = presentedToken.getCustomer();

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            refreshTokenService.revokeFamily(
                    presentedToken.getFamilyId(),
                    now
            );

            throw new AuthException(
                    ApiErrorCode.CUSTOMER_AUTHENTICATION_NOT_ALLOWED,
                    HttpStatus.FORBIDDEN
            );
        }

        AccessTokenService.IssuedAccessToken accessToken =
                accessTokenService.issue(customer, now);

        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.rotate(
                        presentedToken,
                        now
                );

        return new AuthRefreshResponse(
                customer.getId(),
                accessToken.value(),
                refreshToken.value(),
                "Bearer",
                accessToken.expiresInSeconds()
        );
    }

    @Transactional
    public void logout(AuthLogoutRequest request) {
        Instant now = Instant.now();

        /*
         * Always complete successfully, even if the token is unknown.
         * This avoids revealing whether a refresh token exists.
         */
        refreshTokenService
                .findForUpdate(request.refreshToken())
                .ifPresent(refreshToken ->
                        refreshTokenService.revokeFamily(
                                refreshToken.getFamilyId(),
                                now
                        )
                );
    }

    private void performDummyVerification(String providedPin) {
        pinHasher.matches(
                DUMMY_CUSTOMER_ID,
                providedPin,
                dummyPinHash
        );
    }

    private AuthException invalidCredentials() {
        return new AuthException(
                ApiErrorCode.INVALID_CREDENTIALS,
                HttpStatus.UNAUTHORIZED
        );
    }

    private AuthException invalidRefreshToken() {
        return new AuthException(
                ApiErrorCode.INVALID_REFRESH_TOKEN,
                HttpStatus.UNAUTHORIZED
        );
    }

    private AuthException refreshTokenReuseDetected() {
        return new AuthException(
                ApiErrorCode.REFRESH_TOKEN_REUSE_DETECTED,
                HttpStatus.UNAUTHORIZED
        );
    }

    private AuthException lockedCredential(
            CustomerPinCredential credential,
            Instant now
    ) {
        long retryAfterSeconds =
                credential.getRemainingLockSeconds(now);

        return new AuthException(
                ApiErrorCode.PIN_AUTHENTICATION_LOCKED,
                HttpStatus.LOCKED,
                Map.of(
                        "retryAfterSeconds",
                        retryAfterSeconds
                )
        );
    }
}