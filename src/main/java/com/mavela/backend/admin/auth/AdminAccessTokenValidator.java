package com.mavela.backend.admin.auth;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Cognito access tokens carry their app client identifier in {@code client_id}
 * and their kind in {@code token_use}. Both are required for the staff API.
 */
final class AdminAccessTokenValidator
        implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_ADMIN_TOKEN = new OAuth2Error(
            "invalid_token",
            "The token is not a valid administrator access token.",
            null
    );

    private final String expectedClientId;

    AdminAccessTokenValidator(String expectedClientId) {
        this.expectedClientId = expectedClientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!"access".equals(token.getClaimAsString("token_use"))
                || !expectedClientId.equals(
                token.getClaimAsString("client_id"))
                || token.getSubject() == null
                || token.getSubject().isBlank()) {
            return OAuth2TokenValidatorResult.failure(INVALID_ADMIN_TOKEN);
        }

        return OAuth2TokenValidatorResult.success();
    }
}
