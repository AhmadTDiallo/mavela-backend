package com.mavela.backend.admin.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAccessTokenValidatorTests {

    private final AdminAccessTokenValidator validator =
            new AdminAccessTokenValidator("staff-client-id");

    @Test
    void acceptsCognitoStyleAccessTokenForExpectedClient() {
        assertThat(validator.validate(token("access", "staff-client-id"))
                .hasErrors()).isFalse();
    }

    @Test
    void rejectsIdTokensAndWrongClientIds() {
        assertThat(validator.validate(token("id", "staff-client-id"))
                .hasErrors()).isTrue();
        assertThat(validator.validate(token("access", "other-client"))
                .hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingRequiredAccessTokenClaimsIndependently() {
        assertThat(validator.validate(token(null, "staff-client-id", "staff-sub"))
                .hasErrors()).isTrue();
        assertThat(validator.validate(token("access", null, "staff-sub"))
                .hasErrors()).isTrue();
        assertThat(validator.validate(token("access", "staff-client-id", null))
                .hasErrors()).isTrue();
    }

    private Jwt token(String tokenUse, String clientId) {
        return token(tokenUse, clientId, "cognito-staff-subject");
    }

    private Jwt token(String tokenUse, String clientId, String subject) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer("https://issuer.example.test")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        if (tokenUse != null) {
            builder.claim("token_use", tokenUse);
        }
        if (clientId != null) {
            builder.claim("client_id", clientId);
        }
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
