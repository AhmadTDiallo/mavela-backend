package com.mavela.backend.qswitch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Configuration for the QSwitch staging authentication boundary.
 *
 * <p>The binding intentionally contains no default credentials. A partially
 * configured enabled integration remains unavailable and makes no outbound
 * request. This lets ordinary local development start safely while preserving
 * a useful internal diagnostic state.</p>
 */
@ConfigurationProperties(prefix = "mavela.qswitch")
public class QSwitchProperties {

    private static final Duration STAGING_TOKEN_LIFETIME = Duration.ofHours(240);

    private boolean enabled;
    private QSwitchMode mode = QSwitchMode.QSWITCH;
    private URI baseUrl;
    private String appId;
    private String appToken;
    private String appSecret;
    private String fintechId;
    private String countryCode = "DRC";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);
    private Duration tokenRefreshSafetyWindow = Duration.ofMinutes(5);
    private int maxReadRetries = 1;
    private Duration initialRetryDelay = Duration.ofMillis(250);
    private Duration maxRetryDelay = Duration.ofSeconds(2);
    private int rateLimitPerMinute = 100;
    private int rateLimitBurst = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Retained only for the existing local synthetic account-provider tests.
     * It does not alter staging token authentication.
     */
    public QSwitchMode getMode() {
        return mode;
    }

    public void setMode(QSwitchMode mode) {
        this.mode = mode == null ? QSwitchMode.QSWITCH : mode;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppToken() {
        return appToken;
    }

    public void setAppToken(String appToken) {
        this.appToken = appToken;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getFintechId() {
        return fintechId;
    }

    public void setFintechId(String fintechId) {
        this.fintechId = fintechId;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
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

    public void setTokenRefreshSafetyWindow(Duration tokenRefreshSafetyWindow) {
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

    public Duration getStagingTokenLifetime() {
        return STAGING_TOKEN_LIFETIME;
    }

    public boolean isMockModeEnabled() {
        return enabled && mode == QSwitchMode.MOCK;
    }

    public boolean isMockEnabled() {
        return isMockModeEnabled();
    }

    /**
     * Returns whether an enabled QSwitch integration is safe to contact.
     * Validation remains local and intentionally does not reveal which
     * sensitive credential is missing.
     */
    public boolean isStagingAuthenticationConfigured() {
        return enabled
                && isValidHttpsBaseUrl(baseUrl)
                && hasText(appId)
                && hasText(appToken)
                && hasText(appSecret)
                && hasText(fintechId)
                && isValidStagingCountryCode(countryCode)
                && isPositive(connectTimeout)
                && isPositive(readTimeout)
                && isNonNegative(tokenRefreshSafetyWindow)
                && maxReadRetries >= 0
                && isNonNegative(initialRetryDelay)
                && isPositive(maxRetryDelay)
                && rateLimitPerMinute > 0
                && rateLimitBurst > 0;
    }

    /** @deprecated Use {@link #isStagingAuthenticationConfigured()}. */
    @Deprecated
    public boolean isLiveModeConfigured() {
        return isStagingAuthenticationConfigured();
    }

    URI initialTokenEndpoint() {
        requireStagingAuthenticationConfiguration();
        return baseUrl.resolve("/epp2/fintech/auth/token");
    }

    URI apiEndpoint(String path) {
        requireStagingAuthenticationConfiguration();
        if (!isSafeAbsolutePath(path)) {
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
            );
        }
        return baseUrl.resolve(path);
    }

    private void requireStagingAuthenticationConfiguration() {
        if (!isStagingAuthenticationConfigured()) {
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
            );
        }
    }

    private boolean isSafeAbsolutePath(String path) {
        return hasText(path)
                && path.startsWith("/")
                && !path.startsWith("//")
                && !path.contains("://")
                && !path.contains("?")
                && !path.contains("#");
    }

    private boolean isValidHttpsBaseUrl(URI value) {
        return value != null
                && value.isAbsolute()
                && "https".equalsIgnoreCase(value.getScheme())
                && hasText(value.getHost())
                && value.getUserInfo() == null
                && value.getQuery() == null
                && value.getFragment() == null;
    }

    private boolean isValidStagingCountryCode(String value) {
        return "DRC".equals(value);
    }

    private boolean isPositive(Duration value) {
        return value != null && !value.isNegative() && !value.isZero();
    }

    private boolean isNonNegative(Duration value) {
        return value != null && !value.isNegative();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
