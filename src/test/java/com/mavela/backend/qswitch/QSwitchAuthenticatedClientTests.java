package com.mavela.backend.qswitch;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class QSwitchAuthenticatedClientTests {

    @Test
    void apiRequestsReceiveTheDrcCountryHeaderButEpp2RequestsDoNot() {
        QSwitchProperties properties = QSwitchPropertiesTests.completeStagingProperties();
        QSwitchAuthenticatedClient client = new QSwitchAuthenticatedClient(
                properties,
                new QSwitchStagingTokenManager(
                        properties,
                        new FixedTokenTransport(),
                        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
                )
        );

        QSwitchHttpRequest apiRequest = client.authenticatedRequest(
                "GET",
                "/api/fintechs/current",
                null
        );
        QSwitchHttpRequest epp2Request = client.authenticatedRequest(
                "GET",
                "/epp2/fintech/example",
                null
        );

        assertThat(apiRequest.headers())
                .containsEntry("x-country-code", "DRC")
                .containsEntry("Authorization", "Bearer test-access-token");
        assertThat(epp2Request.headers()).doesNotContainKey("x-country-code");
        assertThat(epp2Request.headers()).containsEntry(
                "Authorization",
                "Bearer test-access-token"
        );
        assertThat(apiRequest.toString()).doesNotContain("test-access-token");
    }

    @Test
    void diagnosticFailsClosedWhenAuthenticationIsDisabled() {
        QSwitchProperties properties = QSwitchPropertiesTests.completeStagingProperties();
        properties.setEnabled(false);
        QSwitchAuthenticationDiagnostic diagnostic = new QSwitchAuthenticationDiagnostic(
                new QSwitchStagingTokenManager(
                        properties,
                        new FixedTokenTransport(),
                        Clock.systemUTC()
                )
        );

        assertThat(diagnostic.status()).isEqualTo(QSwitchAuthenticationStatus.DISABLED);
    }

    private static final class FixedTokenTransport implements QSwitchTokenTransport {

        @Override
        public QSwitchAccessToken requestToken(QSwitchProperties properties) {
            throw new AssertionError("legacy token path must not be used");
        }

        @Override
        public QSwitchTokenPair acquireInitialToken(QSwitchProperties properties) {
            return new QSwitchTokenPair(
                    "test-access-token",
                    "test-refresh-token",
                    Instant.parse("2026-01-02T00:00:00Z")
            );
        }
    }
}
