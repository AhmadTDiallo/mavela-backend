package com.mavela.backend.qswitch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpQSwitchTokenTransportTests {

    private final HttpQSwitchTokenTransport transport = new HttpQSwitchTokenTransport(
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void mapsAuthenticationFailureWithoutExposingTheProviderBody() {
        var response = response(401, "{\"error_description\":\"client-secret-value\"}", Map.of());

        assertThatThrownBy(() -> transport.parseSuccessfulResponse(
                response,
                QSwitchPropertiesTests.completeLiveProperties()
        ))
                .isInstanceOf(QSwitchIntegrationException.class)
                .satisfies(exception -> {
                    var integrationException = (QSwitchIntegrationException) exception;
                    assertThat(integrationException.getErrorCode())
                            .isEqualTo(QSwitchIntegrationErrorCode.AUTHENTICATION_FAILED);
                    assertThat(integrationException.getMessage())
                            .doesNotContain("client-secret-value");
                });
    }

    @Test
    void mapsRateLimitsAndCapsTheProviderRetryAfterValueInTheReadPolicy() {
        var response = response(429, "{\"detail\":\"not for customers\"}", Map.of(
                "Retry-After", List.of("10"),
                "X-RateLimit-Limit", List.of("100"),
                "X-RateLimit-Remaining", List.of("0")
        ));

        assertThatThrownBy(() -> transport.parseSuccessfulResponse(
                response,
                QSwitchPropertiesTests.completeLiveProperties()
        ))
                .isInstanceOf(QSwitchIntegrationException.class)
                .satisfies(exception -> {
                    var integrationException = (QSwitchIntegrationException) exception;
                    assertThat(integrationException.getErrorCode())
                            .isEqualTo(QSwitchIntegrationErrorCode.RATE_LIMITED);
                    assertThat(integrationException.getRetryAfter())
                            .isEqualTo(Duration.ofSeconds(10));
                    assertThat(integrationException.getMessage())
                            .doesNotContain("not for customers");
                });

        var policy = new QSwitchReadRetryPolicy(QSwitchPropertiesTests.completeLiveProperties());
        assertThat(policy.backoffFor(1, Duration.ofSeconds(10))).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void rejectsAResponseWithoutTheConfiguredTokenFields() {
        var response = response(200, "{\"access_token\":\"sensitive-token\"}", Map.of());

        assertThatThrownBy(() -> transport.parseSuccessfulResponse(
                response,
                QSwitchPropertiesTests.completeLiveProperties()
        ))
                .isInstanceOf(QSwitchIntegrationException.class)
                .satisfies(exception -> {
                    var integrationException = (QSwitchIntegrationException) exception;
                    assertThat(integrationException.getErrorCode())
                            .isEqualTo(QSwitchIntegrationErrorCode.INVALID_RESPONSE);
                    assertThat(integrationException.getMessage())
                            .doesNotContain("sensitive-token");
                });
    }

    private HttpResponse<String> response(
            int status,
            String body,
            Map<String, List<String>> headers
    ) {
        return new StubResponse(status, body, HttpHeaders.of(headers, (key, value) -> true));
    }

    private record StubResponse(int statusCode, String body, HttpHeaders headers)
            implements HttpResponse<String> {

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(URI.create("https://qswitch.example/api/oauth/token"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://qswitch.example/api/oauth/token");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
