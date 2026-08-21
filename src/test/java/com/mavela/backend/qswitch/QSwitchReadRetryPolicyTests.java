package com.mavela.backend.qswitch;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class QSwitchReadRetryPolicyTests {

    private final QSwitchReadRetryPolicy policy = new QSwitchReadRetryPolicy(QSwitchPropertiesTests.completeLiveProperties());

    @Test
    void retriesOnlyTransientReadFailuresWithinTheConfiguredBound() {
        assertThat(policy.shouldRetry(QSwitchIntegrationErrorCode.TIMEOUT, 0)).isTrue();
        assertThat(policy.shouldRetry(QSwitchIntegrationErrorCode.RATE_LIMITED, 0)).isTrue();
        assertThat(policy.shouldRetry(QSwitchIntegrationErrorCode.PROVIDER_UNAVAILABLE, 0)).isTrue();
        assertThat(policy.shouldRetry(QSwitchIntegrationErrorCode.AUTHENTICATION_FAILED, 0)).isFalse();
        assertThat(policy.shouldRetry(QSwitchIntegrationErrorCode.TIMEOUT, 1)).isFalse();
    }

    @Test
    void capsRetryAfterAndBackoffToAvoidRetryStorms() {
        assertThat(policy.backoffFor(1, Duration.ofSeconds(10))).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.backoffFor(1, null)).isEqualTo(Duration.ofMillis(50));
        assertThat(policy.backoffFor(10, null)).isEqualTo(Duration.ofSeconds(1));
    }
}
