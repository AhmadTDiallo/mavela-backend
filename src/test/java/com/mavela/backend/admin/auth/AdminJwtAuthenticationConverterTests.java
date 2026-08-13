package com.mavela.backend.admin.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminJwtAuthenticationConverterTests {

    @Test
    void mapsOnlyTrustedCognitoGroupsToPermissions() {
        AdminAuthProperties properties = new AdminAuthProperties();
        AdminJwtAuthenticationConverter converter =
                new AdminJwtAuthenticationConverter(properties);

        var authentication = converter.convert(jwt(List.of(
                "KYC_REVIEWER",
                "UNTRUSTED_GROUP"
        )));

        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder(
                        "kyc:read",
                        "kyc:claim",
                        "kyc:decide"
                );
    }

    @Test
    void platformAdminDoesNotReceiveEvidencePermissionWithoutKycGroup() {
        AdminAuthProperties properties = new AdminAuthProperties();
        AdminJwtAuthenticationConverter converter =
                new AdminJwtAuthenticationConverter(properties);

        var authentication = converter.convert(jwt(List.of("PLATFORM_ADMIN")));

        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("staff:manage");
    }

    private Jwt jwt(List<String> groups) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer("https://issuer.example.test")
                .subject("cognito-staff-subject")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("cognito:groups", groups)
                .build();
    }
}
