package com.mavela.backend.qswitch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * OAuth client-credentials cache. It is not a Spring Security login flow and
 * never persists or logs QSwitch bearer tokens.
 */
/**
 * @deprecated Superseded by {@link QSwitchOAuthTokenClient}. Retained only to
 * avoid deleting uncommitted local source during foundation consolidation.
 */
@Deprecated
public final class QSwitchOAuthClient {

    private final QSwitchProperties properties;
    private final QSwitchHttpTransport transport;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ReentrantLock refreshLock = new ReentrantLock();

    private volatile QSwitchOAuthAccessToken cachedToken;

    QSwitchOAuthClient(
            QSwitchProperties properties,
            QSwitchHttpTransport transport,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.properties = properties;
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String getAccessToken() {
        Instant now = clock.instant();
        QSwitchOAuthAccessToken existing = cachedToken;
        if (existing != null && existing.isUsableAt(now)) {
            return existing.value();
        }

        refreshLock.lock();
        try {
            now = clock.instant();
            existing = cachedToken;
            if (existing != null && existing.isUsableAt(now)) {
                return existing.value();
            }

            QSwitchOAuthAccessToken refreshed = requestToken(now);
            cachedToken = refreshed;
            return refreshed.value();
        } finally {
            refreshLock.unlock();
        }
    }

    public void invalidate() {
        cachedToken = null;
    }

    private QSwitchOAuthAccessToken requestToken(Instant now) {
        QSwitchHttpResponse response;
        try {
            response = transport.execute(buildTokenRequest());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.TIMEOUT,
                    exception
            );
        } catch (IOException exception) {
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.PROVIDER_UNAVAILABLE,
                    exception
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new QSwitchIntegrationException(
                    response.statusCode() == 429
                            ? QSwitchIntegrationErrorCode.RATE_LIMITED
                            : QSwitchIntegrationErrorCode.PROVIDER_UNAVAILABLE
            );
        }

        try {
            JsonNode tokenPayload = objectMapper.readTree(response.body());
            String accessToken = text(tokenPayload, properties.getTokenAccessTokenField());
            long expiresInSeconds = tokenPayload
                    .path(properties.getTokenExpiresInField())
                    .asLong(-1);

            if (accessToken == null || accessToken.isBlank()
                    || expiresInSeconds <= 0) {
                throw invalidTokenResponse();
            }

            Instant expiresAt = now.plusSeconds(expiresInSeconds);
            Instant refreshAt = expiresAt.minus(
                    properties.getTokenRefreshSafetyWindow()
            );
            if (!refreshAt.isAfter(now)) {
                refreshAt = now;
            }

            return new QSwitchOAuthAccessToken(accessToken, refreshAt);
        } catch (JsonProcessingException exception) {
            throw invalidTokenResponse(exception);
        }
    }

    private QSwitchHttpRequest buildTokenRequest() {
        if (!properties.isLiveOAuthConfigurationComplete()) {
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
            );
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put(
                properties.getTokenGrantTypeField(),
                properties.getTokenGrantTypeValue().trim()
        );
        if (hasText(properties.getScopes())) {
            form.put(properties.getTokenScopeField(), properties.getScopes().trim());
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/x-www-form-urlencoded");

        if (properties.getTokenRequestEncoding()
                == QSwitchTokenRequestEncoding.FORM_URLENCODED_CLIENT_CREDENTIALS
                || properties.getTokenRequestEncoding()
                == QSwitchTokenRequestEncoding.FORM_CLIENT_CREDENTIALS) {
            form.put(properties.getTokenClientIdField(), properties.getClientId().trim());
            form.put(properties.getTokenClientSecretField(), properties.getClientSecret());
        } else if (properties.getTokenRequestEncoding()
                == QSwitchTokenRequestEncoding.BASIC_CLIENT_CREDENTIALS) {
            String credentials = properties.getClientId().trim()
                    + ":" + properties.getClientSecret();
            headers.put(
                    "Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(
                            credentials.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } else {
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
            );
        }

        return new QSwitchHttpRequest(
                "POST",
                properties.tokenEndpoint(),
                headers,
                formEncode(form)
        );
    }

    private String formEncode(Map<String, String> form) {
        return form.entrySet()
                .stream()
                .map(entry -> URLEncoder.encode(
                        entry.getKey(),
                        StandardCharsets.UTF_8
                ) + "=" + URLEncoder.encode(
                        entry.getValue(),
                        StandardCharsets.UTF_8
                ))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String text(JsonNode root, String fieldName) {
        return root.path(fieldName).isTextual()
                ? root.path(fieldName).asText()
                : null;
    }

    private QSwitchIntegrationException invalidTokenResponse() {
        return new QSwitchIntegrationException(
                QSwitchIntegrationErrorCode.INVALID_RESPONSE
        );
    }

    private QSwitchIntegrationException invalidTokenResponse(Throwable cause) {
        return new QSwitchIntegrationException(
                QSwitchIntegrationErrorCode.INVALID_RESPONSE,
                cause
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
