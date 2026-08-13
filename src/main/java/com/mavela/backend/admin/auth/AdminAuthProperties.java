package com.mavela.backend.admin.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Configuration for the separate staff identity provider. These settings are
 * deliberately independent from the customer JWT issuer.
 */
@ConfigurationProperties(prefix = "mavela.admin.auth")
public class AdminAuthProperties {

    private boolean enabled;
    private String issuerUri;
    private String clientId;
    private String groupsClaim = "cognito:groups";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getGroupsClaim() {
        return groupsClaim;
    }

    public void setGroupsClaim(String groupsClaim) {
        this.groupsClaim = hasText(groupsClaim)
                ? groupsClaim.trim()
                : "cognito:groups";
    }

    /**
     * Admin routes are enabled only when every required OIDC setting is
     * present. A partially configured or insecure deployment remains
     * fail-closed. Cognito issuer discovery must never be initiated from an
     * arbitrary relative or clear-text endpoint.
     */
    public boolean isEnabledAndConfigured() {
        return enabled && hasText(clientId) && isValidHttpsIssuer(issuerUri);
    }

    private boolean isValidHttpsIssuer(String value) {
        if (!hasText(value)) {
            return false;
        }

        try {
            URI issuer = new URI(value.trim());
            return issuer.isAbsolute()
                    && "https".equalsIgnoreCase(issuer.getScheme())
                    && issuer.getHost() != null
                    && !issuer.getHost().isBlank()
                    && issuer.getUserInfo() == null
                    && issuer.getQuery() == null
                    && issuer.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
