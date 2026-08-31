package com.mavela.backend.qswitch;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed internal request factory for future QSwitch operations.
 *
 * <p>It intentionally does not execute ledger, customer, account, merchant,
 * or payment operations. Future adapters can use its authorized request after
 * their endpoint contracts and ownership rules are implemented.</p>
 */
public final class QSwitchAuthenticatedClient {

    private final QSwitchProperties properties;
    private final QSwitchStagingTokenManager tokenManager;

    QSwitchAuthenticatedClient(
            QSwitchProperties properties,
            QSwitchStagingTokenManager tokenManager
    ) {
        this.properties = properties;
        this.tokenManager = tokenManager;
    }

    public QSwitchHttpRequest authenticatedRequest(
            String method,
            String path,
            String body
    ) {
        if (method == null || method.isBlank()) {
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
            );
        }
        java.net.URI endpoint = properties.apiEndpoint(path);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + tokenManager.accessToken());
        if (path != null && path.startsWith("/api/")) {
            headers.put("x-country-code", properties.getCountryCode());
        }
        if (body != null) {
            headers.put("Content-Type", "application/json");
        }

        return new QSwitchHttpRequest(
                method.trim().toUpperCase(java.util.Locale.ROOT),
                endpoint,
                headers,
                body
        );
    }
}
