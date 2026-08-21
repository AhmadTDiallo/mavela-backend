package com.mavela.backend.qswitch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * QSwitch configuration is intentionally conservative: mock mode is opt-in,
 * and live OAuth cannot be considered ready without an explicitly confirmed
 * request contract. Credentials are supplied only by environment variables or
 * deployment secret management.
 */
@ConfigurationProperties(prefix = "mavela.qswitch")
public class QSwitchProperties {

    private boolean enabled;
    private QSwitchMode mode = QSwitchMode.MOCK;
    private URI baseUrl;
    private String tokenPath = "/api/oauth/token";
    private String clientId;
    private String clientSecret;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);
    private Duration tokenRefreshSafetyWindow = Duration.ofSeconds(30);
    private int maxReadRetries = 1;
    private Duration initialRetryDelay = Duration.ofMillis(250);
    private Duration maxRetryDelay = Duration.ofSeconds(2);
    private int rateLimitPerMinute = 100;
    private int rateLimitBurst = 20;
    private QSwitchTokenRequestEncoding tokenRequestEncoding =
            QSwitchTokenRequestEncoding.UNCONFIRMED;
    private String tokenGrantTypeField;
    private String tokenGrantTypeValue;
    private String tokenClientIdField;
    private String tokenClientSecretField;
    private String tokenScopeField;
    private String scopes;
    private String tokenAccessTokenField;
    private String tokenExpiresInField;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public QSwitchMode getMode() {
        return mode;
    }

    public void setMode(QSwitchMode mode) {
        this.mode = mode == null ? QSwitchMode.MOCK : mode;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTokenPath() {
        return tokenPath;
    }

    public void setTokenPath(String tokenPath) {
        this.tokenPath = tokenPath;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getTokenRefreshSafetyWindow() {
        return tokenRefreshSafetyWindow;
    }

    public void setTokenRefreshSafetyWindow(
            Duration tokenRefreshSafetyWindow
    ) {
        this.tokenRefreshSafetyWindow = tokenRefreshSafetyWindow;
    }

    public int getMaxReadRetries() {
        return maxReadRetries;
    }

    public void setMaxReadRetries(int maxReadRetries) {
        this.maxReadRetries = maxReadRetries;
    }

    public Duration getInitialRetryDelay() {
        return initialRetryDelay;
    }

    public Duration getRetryInitialBackoff() {
        return initialRetryDelay;
    }

    public void setInitialRetryDelay(Duration initialRetryDelay) {
        this.initialRetryDelay = initialRetryDelay;
    }

    /** Spring binding alias for mavela.qswitch.retry-initial-backoff. */
    public void setRetryInitialBackoff(Duration retryInitialBackoff) {
        this.initialRetryDelay = retryInitialBackoff;
    }

    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    public Duration getRetryMaxBackoff() {
        return maxRetryDelay;
    }

    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay;
    }

    /** Spring binding alias for mavela.qswitch.retry-max-backoff. */
    public void setRetryMaxBackoff(Duration retryMaxBackoff) {
        this.maxRetryDelay = retryMaxBackoff;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public int getRateLimitBurst() {
        return rateLimitBurst;
    }

    public void setRateLimitBurst(int rateLimitBurst) {
        this.rateLimitBurst = rateLimitBurst;
    }

    public QSwitchTokenRequestEncoding getTokenRequestEncoding() {
        return tokenRequestEncoding;
    }

    public void setTokenRequestEncoding(
            QSwitchTokenRequestEncoding tokenRequestEncoding
    ) {
        this.tokenRequestEncoding = tokenRequestEncoding == null
                ? QSwitchTokenRequestEncoding.UNCONFIRMED
                : tokenRequestEncoding;
    }

    public String getTokenGrantTypeField() {
        return tokenGrantTypeField;
    }

    public void setTokenGrantTypeField(String tokenGrantTypeField) {
        this.tokenGrantTypeField = tokenGrantTypeField;
    }

    public String getTokenGrantTypeValue() {
        return tokenGrantTypeValue;
    }

    /** @deprecated Use {@link #getTokenGrantTypeValue()}. */
    @Deprecated
    public String getTokenGrantType() {
        return tokenGrantTypeValue;
    }

    public void setTokenGrantTypeValue(String tokenGrantTypeValue) {
        this.tokenGrantTypeValue = tokenGrantTypeValue;
    }

    public String getTokenClientIdField() {
        return tokenClientIdField;
    }

    public void setTokenClientIdField(String tokenClientIdField) {
        this.tokenClientIdField = tokenClientIdField;
    }

    public String getTokenClientSecretField() {
        return tokenClientSecretField;
    }

    public void setTokenClientSecretField(String tokenClientSecretField) {
        this.tokenClientSecretField = tokenClientSecretField;
    }

    public String getTokenScopeField() {
        return tokenScopeField;
    }

    public void setTokenScopeField(String tokenScopeField) {
        this.tokenScopeField = tokenScopeField;
    }

    public String getScopes() {
        return scopes;
    }

    /** @deprecated Use {@link #getScopes()}. */
    @Deprecated
    public String getTokenScope() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getTokenAccessTokenField() {
        return tokenAccessTokenField;
    }

    public void setTokenAccessTokenField(String tokenAccessTokenField) {
        this.tokenAccessTokenField = tokenAccessTokenField;
    }

    public String getTokenExpiresInField() {
        return tokenExpiresInField;
    }

    public void setTokenExpiresInField(String tokenExpiresInField) {
        this.tokenExpiresInField = tokenExpiresInField;
    }

    public boolean isMockModeEnabled() {
        return enabled && mode == QSwitchMode.MOCK;
    }

    public boolean isMockEnabled() {
        return isMockModeEnabled();
    }

    /**
     * Validates only the known OAuth configuration. The authoritative QSwitch
     * account, balance, and history endpoint contract remains intentionally
     * unimplemented until it is supplied by QSwitch.
     */
    public boolean isLiveOAuthConfigurationComplete() {
        return enabled
                && mode == QSwitchMode.QSWITCH
                && isValidHttpsBaseUrl(baseUrl)
                && isTokenPathValid()
                && hasText(clientId)
                && hasText(clientSecret)
                && isSupportedTokenRequestEncoding()
                && hasText(tokenGrantTypeField)
                && hasText(tokenGrantTypeValue)
                && hasText(tokenClientIdField)
                && hasText(tokenClientSecretField)
                && (!hasText(scopes) || hasText(tokenScopeField))
                && hasText(tokenAccessTokenField)
                && hasText(tokenExpiresInField)
                && connectTimeout != null
                && !connectTimeout.isNegative()
                && !connectTimeout.isZero()
                && readTimeout != null
                && !readTimeout.isNegative()
                && !readTimeout.isZero()
                && tokenRefreshSafetyWindow != null
                && !tokenRefreshSafetyWindow.isNegative()
                && maxReadRetries >= 0
                && initialRetryDelay != null
                && !initialRetryDelay.isNegative()
                && maxRetryDelay != null
                && !maxRetryDelay.isNegative()
                && !maxRetryDelay.isZero()
                && rateLimitPerMinute > 0
                && rateLimitBurst > 0;
    }

    public boolean isLiveModeConfigured() {
        return isLiveOAuthConfigurationComplete();
    }

    URI tokenEndpoint() {
        if (!isLiveOAuthConfigurationComplete()) {
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
            );
        }

        return baseUrl.resolve(tokenPath);
    }

    private boolean isTokenPathValid() {
        return hasText(tokenPath)
                && tokenPath.startsWith("/")
                && !tokenPath.startsWith("//")
                && !tokenPath.contains("://")
                && !tokenPath.contains("?")
                && !tokenPath.contains("#");
    }

    private boolean isSupportedTokenRequestEncoding() {
        return tokenRequestEncoding == QSwitchTokenRequestEncoding.FORM_URLENCODED_CLIENT_CREDENTIALS
                || tokenRequestEncoding == QSwitchTokenRequestEncoding.JSON_CLIENT_CREDENTIALS;
    }

    private boolean isValidHttpsBaseUrl(URI value) {
        return value != null
                && value.isAbsolute()
                && "https".equalsIgnoreCase(value.getScheme())
                && value.getHost() != null
                && !value.getHost().isBlank()
                && value.getUserInfo() == null
                && value.getQuery() == null
                && value.getFragment() == null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
