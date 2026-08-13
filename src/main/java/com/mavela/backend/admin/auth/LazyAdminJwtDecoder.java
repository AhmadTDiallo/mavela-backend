package com.mavela.backend.admin.auth;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.util.Collection;

/**
 * Resolves the staff issuer's JWKS only when an enabled admin route receives a
 * bearer token. This keeps ordinary customer development independent of an
 * external Cognito network call while retaining issuer and signature checks.
 */
public class LazyAdminJwtDecoder implements JwtDecoder {

    private final AdminAuthProperties properties;
    private volatile JwtDecoder delegate;

    public LazyAdminJwtDecoder(AdminAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        if (!properties.isEnabledAndConfigured()) {
            throw new BadJwtException("Administrator authentication is unavailable.");
        }

        return decoder().decode(token);
    }

    private JwtDecoder decoder() {
        JwtDecoder existing = delegate;
        if (existing != null) {
            return existing;
        }

        synchronized (this) {
            if (delegate == null) {
                delegate = createDecoder();
            }
            return delegate;
        }
    }

    private JwtDecoder createDecoder() {
        JwtDecoder issuerDecoder = JwtDecoders.fromIssuerLocation(
                properties.getIssuerUri().trim()
        );

        OAuth2TokenValidator<Jwt> issuerAndTimestampValidator =
                JwtValidators.createDefaultWithIssuer(
                        properties.getIssuerUri().trim()
                );
        OAuth2TokenValidator<Jwt> accessTokenValidator =
                new AdminAccessTokenValidator(
                        properties.getClientId().trim()
                );

        return token -> {
            Jwt jwt = issuerDecoder.decode(token);
            OAuth2TokenValidatorResult result =
                    issuerAndTimestampValidator.validate(jwt);

            if (!result.hasErrors()) {
                result = accessTokenValidator.validate(jwt);
            }

            if (result.hasErrors()) {
                Collection<OAuth2Error> errors = result.getErrors();
                throw new JwtValidationException(
                        "Administrator access token validation failed.",
                        errors
                );
            }

            return jwt;
        };
    }
}
