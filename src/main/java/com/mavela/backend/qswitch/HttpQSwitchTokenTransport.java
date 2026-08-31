package com.mavela.backend.qswitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP implementation of the confirmed QSwitch staging application-token
 * contract. It purposefully does not log responses, credentials, or tokens.
 */
public class HttpQSwitchTokenTransport implements QSwitchTokenTransport {

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient httpClient;

    public HttpQSwitchTokenTransport(ObjectMapper objectMapper, Clock clock) {
        this(objectMapper, clock, null);
    }

    HttpQSwitchTokenTransport(
            ObjectMapper objectMapper,
            Clock clock,
            HttpClient httpClient
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.httpClient = httpClient;
    }

    @Override
    public QSwitchAccessToken requestToken(QSwitchProperties properties) {
        QSwitchTokenPair token = acquireInitialToken(properties);
        return new QSwitchAccessToken(token.accessToken(), token.expiresAt());
    }

    @Override
    public QSwitchTokenPair acquireInitialToken(QSwitchProperties properties) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("app_token", properties.getAppToken());
        body.put("app_secret", properties.getAppSecret());
        return exchange(
                properties,
                properties.initialTokenEndpoint(),
                body
        );
    }

    private QSwitchTokenPair exchange(
            QSwitchProperties properties,
            java.net.URI endpoint,
            Map<String, String> body
    ) {
        if (!properties.isStagingAuthenticationConfigured()) {
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
            );
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(properties.getReadTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        serialize(body),
                        StandardCharsets.UTF_8
                ))
                .build();

        try {
            HttpResponse<String> response = clientFor(properties).send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            return parseTokenResponse(response, properties);
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.TIMEOUT, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.TIMEOUT, exception);
        } catch (IOException exception) {
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.PROVIDER_UNAVAILABLE, exception);
        }
    }

    QSwitchTokenPair parseTokenResponse(
            HttpResponse<String> response,
            QSwitchProperties properties
    ) {
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.AUTHENTICATION_FAILED);
        }
        if (response.statusCode() == 429) {
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.RATE_LIMITED,
                    retryAfter(response)
            );
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.PROVIDER_UNAVAILABLE);
        }

        try {
            JsonNode body = objectMapper.readTree(response.body());
            String accessToken = text(body, "access_token");
            if (accessToken == null) {
                throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INVALID_RESPONSE);
            }
            return new QSwitchTokenPair(
                    accessToken,
                    clock.instant().plus(properties.getStagingTokenLifetime())
            );
        } catch (IOException exception) {
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INVALID_RESPONSE, exception);
        }
    }

    private HttpClient clientFor(QSwitchProperties properties) {
        if (httpClient != null) {
            return httpClient;
        }
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private String serialize(Map<String, String> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.INVALID_RESPONSE,
                    exception
            );
        }
    }

    private String text(JsonNode body, String fieldName) {
        JsonNode value = body.path(fieldName);
        return value.isTextual() && !value.asText().isBlank()
                ? value.asText()
                : null;
    }

    private java.time.Duration retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .flatMap(this::durationFromSeconds)
                .orElse(null);
    }

    private java.util.Optional<java.time.Duration> durationFromSeconds(String value) {
        try {
            long seconds = Long.parseLong(value);
            return seconds > 0
                    ? java.util.Optional.of(java.time.Duration.ofSeconds(seconds))
                    : java.util.Optional.empty();
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }
}
