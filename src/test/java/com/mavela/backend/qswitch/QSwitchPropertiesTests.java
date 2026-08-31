package com.mavela.backend.qswitch;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class QSwitchPropertiesTests {

    @Test
    void disabledConfigurationFailsClosedEvenWhenAllStagingValuesArePresent() {
        var properties = completeStagingProperties();
        properties.setEnabled(false);

        assertThat(properties.isStagingAuthenticationConfigured()).isFalse();
        assertThat(properties.isMockEnabled()).isFalse();
    }

    @Test
    void enabledStagingConfigurationRequiresTheSeparatelySuppliedSecret() {
        var properties = completeStagingProperties();
        properties.setAppSecret(" ");

        assertThat(properties.isStagingAuthenticationConfigured()).isFalse();
    }

    @Test
    void stagingConfigurationRequiresHttps() {
        var properties = completeStagingProperties();
        properties.setBaseUrl(URI.create("http://localhost:8080"));

        assertThat(properties.isStagingAuthenticationConfigured()).isFalse();
    }

    @Test
    void stagingConfigurationIsRestrictedToTheProvisionedDrcRegion() {
        var properties = completeStagingProperties();
        properties.setCountryCode("KEN");

        assertThat(properties.isStagingAuthenticationConfigured()).isFalse();
    }

    @Test
    void mockModeNeedsExplicitEnablementButNoLiveCredentials() {
        var properties = new QSwitchProperties();
        properties.setEnabled(true);
        properties.setMode(QSwitchMode.MOCK);

        assertThat(properties.isMockEnabled()).isTrue();
        assertThat(properties.isStagingAuthenticationConfigured()).isFalse();
    }

    static QSwitchProperties completeStagingProperties() {
        var properties = new QSwitchProperties();
        properties.setEnabled(true);
        properties.setMode(QSwitchMode.QSWITCH);
        properties.setBaseUrl(URI.create("https://qswitch.test"));
        properties.setAppId("test-app-id");
        properties.setAppToken("test-app-token");
        properties.setAppSecret("test-app-secret");
        properties.setFintechId("test-fintech-id");
        properties.setCountryCode("DRC");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
        properties.setTokenRefreshSafetyWindow(Duration.ofSeconds(30));
        properties.setRetryInitialBackoff(Duration.ofMillis(50));
        properties.setRetryMaxBackoff(Duration.ofSeconds(1));
        return properties;
    }
}
