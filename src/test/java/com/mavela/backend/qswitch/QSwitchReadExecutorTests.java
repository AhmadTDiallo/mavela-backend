package com.mavela.backend.qswitch;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class QSwitchReadExecutorTests {

    @Test
    void retriesExactlyOnceWithANewTokenAfterAnAuthenticatedReadFails() {
        var tokenRequests = new AtomicInteger();
        var operationCalls = new AtomicInteger();
        var executor = executor(tokenRequests, new ArrayList<>());

        String result = executor.execute(token -> {
            if (operationCalls.getAndIncrement() == 0) {
                throw new QSwitchIntegrationException(
                        QSwitchIntegrationErrorCode.AUTHENTICATION_FAILED
                );
            }
            return token;
        });

        assertThat(result).isEqualTo("token-2");
        assertThat(tokenRequests).hasValue(2);
        assertThat(operationCalls).hasValue(2);
    }

    @Test
    void retriesBoundedTransientReadFailuresWithThePolicyBackoff() {
        var tokenRequests = new AtomicInteger();
        var slept = new ArrayList<java.time.Duration>();
        var executor = executor(tokenRequests, slept);
        var operationCalls = new AtomicInteger();

        String result = executor.execute(token -> {
            if (operationCalls.getAndIncrement() == 0) {
                throw new QSwitchIntegrationException(
                        QSwitchIntegrationErrorCode.RATE_LIMITED,
                        java.time.Duration.ofMillis(20)
                );
            }
            return "read-complete";
        });

        assertThat(result).isEqualTo("read-complete");
        assertThat(tokenRequests).hasValue(1);
        assertThat(slept).containsExactly(java.time.Duration.ofMillis(20));
    }

    private QSwitchReadExecutor executor(
            AtomicInteger tokenRequests,
            List<java.time.Duration> slept
    ) {
        var properties = QSwitchPropertiesTests.completeLiveProperties();
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var tokenClient = new QSwitchOAuthTokenClient(
                properties,
                ignored -> new QSwitchAccessToken(
                        "token-" + tokenRequests.incrementAndGet(),
                        clock.instant().plusSeconds(120)
                ),
                clock
        );
        return new QSwitchReadExecutor(
                tokenClient,
                new QSwitchReadRetryPolicy(properties),
                slept::add
        );
    }
}
