package com.mavela.backend.qswitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * HTTP implementation used only after explicit QSwitch OAuth contract
 * configuration. It purposefully does not log responses, credentials, or
 * access tokens.
 */
public class HttpQSwitchTokenTransport implements QSwitchTokenTransport {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public HttpQSwitchTokenTransport(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public QSwitchAccessToken requestToken(QSwitchProperties properties) {
        if (!properties.isLiveModeConfigured()) {
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE);
        }

        var fields = requestFields(properties);
        var request = HttpRequest.newBuilder(properties.getBaseUrl().resolve(properties.getTokenPath()))
                .timeout(properties.getReadTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", contentType(properties.getTokenRequestEncoding()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(properties, fields), StandardCharsets.UTF_8))
                .build();
        var client = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseSuccessfulResponse(response, properties);
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.TIMEOUT, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.PROVIDER_UNAVAILABLE, exception);
        } catch (IOException exception) {
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.PROVIDER_UNAVAILABLE, exception);
        }
    }

    QSwitchAccessToken parseSuccessfulResponse(
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
            var token = body.path(properties.getTokenAccessTokenField()).asText();
            var expiresInSeconds = body.path(properties.getTokenExpiresInField()).asLong(-1);
            if (token.isBlank() || expiresInSeconds <= 0) {
                throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INVALID_RESPONSE);
            }
            return new QSwitchAccessToken(token, clock.instant().plusSeconds(expiresInSeconds));
        } catch (IOException exception) {
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INVALID_RESPONSE, exception);
        }
    }

    private Map<String, String> requestFields(QSwitchProperties properties) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(properties.getTokenGrantTypeField(), properties.getTokenGrantTypeValue());
        fields.put(properties.getTokenClientIdField(), properties.getClientId());
        fields.put(properties.getTokenClientSecretField(), properties.getClientSecret());
        if (hasText(properties.getScopes())) {
            if (!hasText(properties.getTokenScopeField())) {
                throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE);
            }
            fields.put(properties.getTokenScopeField(), properties.getScopes());
        }
        return fields;
    }

    private String requestBody(QSwitchProperties properties, Map<String, String> fields) {
        try {
            return switch (properties.getTokenRequestEncoding()) {
                case FORM_URLENCODED_CLIENT_CREDENTIALS, FORM_CLIENT_CREDENTIALS -> formUrlEncode(fields);
                case JSON_CLIENT_CREDENTIALS -> objectMapper.writeValueAsString(fields);
                case BASIC_CLIENT_CREDENTIALS, UNCONFIRMED -> throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE);
            };
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INVALID_RESPONSE, exception);
        }
    }

    private String contentType(QSwitchTokenRequestEncoding encoding) {
        return switch (encoding) {
            case FORM_URLENCODED_CLIENT_CREDENTIALS, FORM_CLIENT_CREDENTIALS -> "application/x-www-form-urlencoded";
            case JSON_CLIENT_CREDENTIALS -> "application/json";
            case BASIC_CLIENT_CREDENTIALS, UNCONFIRMED -> throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE);
        };
    }

    private String formUrlEncode(Map<String, String> fields) {
        var joiner = new StringJoiner("&");
        fields.forEach((key, value) -> joiner.add(
                URLEncoder.encode(key, StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(value, StandardCharsets.UTF_8)
        ));
        return joiner.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
