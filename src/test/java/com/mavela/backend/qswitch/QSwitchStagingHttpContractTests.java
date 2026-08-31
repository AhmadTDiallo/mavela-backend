package com.mavela.backend.qswitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QSwitchStagingHttpContractTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"),
            ZoneOffset.UTC
    );

    private MockWebServer server;
    private HttpQSwitchTokenTransport transport;
    private QSwitchProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        HeldCertificate certificate = new HeldCertificate.Builder()
                .addSubjectAlternativeName("localhost")
                .build();
        HandshakeCertificates certificates = new HandshakeCertificates.Builder()
                .heldCertificate(certificate)
                .addTrustedCertificate(certificate.certificate())
                .build();
        server = new MockWebServer();
        server.useHttps(certificates.sslSocketFactory(), false);
        server.start();

        transport = new HttpQSwitchTokenTransport(
                objectMapper,
                clock,
                HttpClient.newBuilder().sslContext(certificates.sslContext()).build()
        );
        properties = QSwitchPropertiesTests.completeStagingProperties();
        properties.setBaseUrl(java.net.URI.create(
                "https://localhost:" + server.getPort()
        ));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void initialRequestUsesOnlyTheConfirmedJsonFields() throws Exception {
        server.enqueue(successfulTokenResponse());

        QSwitchTokenPair token = transport.acquireInitialToken(properties);
        RecordedRequest request = takeRequest();
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());

        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/epp2/fintech/auth/token");
        assertThat(request.getHeader("Content-Type")).startsWith("application/json");
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.has("app_token")).isTrue();
        assertThat(body.has("app_secret")).isTrue();
        assertThat(body.has("grant_type")).isFalse();
        assertThat(token.accessToken()).isEqualTo("test-access-token");
    }

    @Test
    void refreshRequestIncludesTheConfirmedRefreshGrant() throws Exception {
        server.enqueue(successfulTokenResponse());

        transport.refreshToken(
                properties,
                new QSwitchTokenPair(
                        "old-access-token",
                        "old-refresh-token",
                        clock.instant().plusSeconds(60)
                )
        );
        RecordedRequest request = takeRequest();
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());

        assertThat(request.getPath()).isEqualTo("/epp2/fintech/auth/token/refresh");
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.has("grant_type")).isTrue();
        assertThat(body.has("app_token")).isTrue();
        assertThat(body.has("refresh_token")).isTrue();
        assertThat(body.path("grant_type").asText()).isEqualTo("refresh_token");
    }

    @Test
    void authenticationFailuresAndProviderBodiesAreMappedSafely() {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"detail\":\"test-app-secret\"}"));

        assertThatThrownBy(() -> transport.acquireInitialToken(properties))
                .isInstanceOf(QSwitchIntegrationException.class)
                .satisfies(exception -> {
                    var qSwitchException = (QSwitchIntegrationException) exception;
                    assertThat(qSwitchException.getErrorCode())
                            .isEqualTo(QSwitchIntegrationErrorCode.AUTHENTICATION_FAILED);
                    assertThat(qSwitchException.getMessage())
                            .doesNotContain("test-app-secret");
                });
    }

    @Test
    void timeoutIsMappedWithoutExposingRequestDetails() {
        properties.setReadTimeout(Duration.ofMillis(100));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        assertThatThrownBy(() -> transport.acquireInitialToken(properties))
                .isInstanceOf(QSwitchIntegrationException.class)
                .satisfies(exception -> assertThat(
                        ((QSwitchIntegrationException) exception).getErrorCode()
                ).isEqualTo(QSwitchIntegrationErrorCode.TIMEOUT));
    }

    private MockResponse successfulTokenResponse() {
        return new MockResponse().setResponseCode(200).setBody(
                "{\"access_token\":\"test-access-token\","
                        + "\"refresh_token\":\"test-refresh-token\"}"
        );
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        return request;
    }
}
