package com.mavela.backend.admin.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthPropertiesTests {

    @Test
    void enablesAdminAuthenticationOnlyForACompleteHttpsIssuerConfiguration() {
        AdminAuthProperties properties = configuredProperties(
                "https://cognito-idp.af-south-1.amazonaws.com/staff-pool"
        );

        assertThat(properties.isEnabledAndConfigured()).isTrue();
    }

    @Test
    void failsClosedForNonHttpsRelativeOrMalformedIssuerConfiguration() {
        assertThat(configuredProperties("http://issuer.example.test")
                .isEnabledAndConfigured()).isFalse();
        assertThat(configuredProperties("/staff-pool")
                .isEnabledAndConfigured()).isFalse();
        assertThat(configuredProperties("https:///missing-host")
                .isEnabledAndConfigured()).isFalse();
        assertThat(configuredProperties("https://issuer.example.test/path?x=1")
                .isEnabledAndConfigured()).isFalse();
    }

    private AdminAuthProperties configuredProperties(String issuerUri) {
        AdminAuthProperties properties = new AdminAuthProperties();
        properties.setEnabled(true);
        properties.setIssuerUri(issuerUri);
        properties.setClientId("staff-client-id");
        return properties;
    }
}
