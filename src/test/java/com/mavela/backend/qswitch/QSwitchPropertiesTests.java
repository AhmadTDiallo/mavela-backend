package com.mavela.backend.qswitch;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class QSwitchPropertiesTests {

    @Test
    void disabledConfigurationFailsClosedEvenWhenAllLiveValuesArePresent() {
        var properties = completeLiveProperties();
        properties.setEnabled(false);

        assertThat(properties.isLiveModeConfigured()).isFalse();
        assertThat(properties.isMockEnabled()).isFalse();
    }

    @Test
    void incompleteLiveConfigurationFailsClosed() {
        var properties = completeLiveProperties();
        properties.setTokenRequestEncoding(QSwitchTokenRequestEncoding.UNCONFIRMED);

        assertThat(properties.isLiveModeConfigured()).isFalse();
    }

    @Test
    void liveConfigurationRequiresHttps() {
        var properties = completeLiveProperties();
        properties.setBaseUrl(URI.create("http://localhost:8080"));

        assertThat(properties.isLiveModeConfigured()).isFalse();
    }

    @Test
    void mockModeNeedsExplicitEnablementButNoLiveCredentials() {
        var properties = new QSwitchProperties();
        properties.setEnabled(true);
        properties.setMode(QSwitchMode.MOCK);

        assertThat(properties.isMockEnabled()).isTrue();
        assertThat(properties.isLiveModeConfigured()).isFalse();
    }

    static QSwitchProperties completeLiveProperties() {
        var properties = new QSwitchProperties();
        properties.setEnabled(true);
        properties.setMode(QSwitchMode.QSWITCH);
        properties.setBaseUrl(URI.create("https://qswitch.test"));
        properties.setTokenPath("/api/oauth/token");
        properties.setClientId("test-client");
        properties.setClientSecret("test-secret");
        properties.setTokenRequestEncoding(QSwitchTokenRequestEncoding.FORM_URLENCODED_CLIENT_CREDENTIALS);
        properties.setTokenGrantTypeField("grant_type");
        properties.setTokenGrantTypeValue("client_credentials");
        properties.setTokenClientIdField("client_id");
        properties.setTokenClientSecretField("client_secret");
        properties.setTokenAccessTokenField("access_token");
        properties.setTokenExpiresInField("expires_in");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
        properties.setTokenRefreshSafetyWindow(Duration.ofSeconds(30));
        properties.setRetryInitialBackoff(Duration.ofMillis(50));
        properties.setRetryMaxBackoff(Duration.ofSeconds(1));
        return properties;
    }
}
