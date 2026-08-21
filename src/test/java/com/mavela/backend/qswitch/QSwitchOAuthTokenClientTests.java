package com.mavela.backend.qswitch;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QSwitchOAuthTokenClientTests {

    @Test
    void cachesTokenUntilTheConfiguredSafetyWindow() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var calls = new AtomicInteger();
        var client = new QSwitchOAuthTokenClient(
                QSwitchPropertiesTests.completeLiveProperties(),
                ignored -> new QSwitchAccessToken("token-" + calls.incrementAndGet(), clock.instant().plusSeconds(120)),
                clock
        );

        assertThat(client.accessToken()).isEqualTo("token-1");
        assertThat(client.accessToken()).isEqualTo("token-1");
        assertThat(calls).hasValue(1);
    }

    @Test
    void refreshesATokenInsideTheSafetyWindow() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var calls = new AtomicInteger();
        var client = new QSwitchOAuthTokenClient(
                QSwitchPropertiesTests.completeLiveProperties(),
                ignored -> new QSwitchAccessToken("token-" + calls.incrementAndGet(), clock.instant().plusSeconds(60)),
                clock
        );

        assertThat(client.accessToken()).isEqualTo("token-1");
        clock.advanceSeconds(31);

        assertThat(client.accessToken()).isEqualTo("token-2");
        assertThat(calls).hasValue(2);
    }

    @Test
    void concurrentRefreshesUseOneTokenExchange() throws Exception {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var calls = new AtomicInteger();
        CountDownLatch exchangeStarted = new CountDownLatch(1);
        var client = new QSwitchOAuthTokenClient(
                QSwitchPropertiesTests.completeLiveProperties(),
                ignored -> {
                    calls.incrementAndGet();
                    exchangeStarted.countDown();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(exception);
                    }
                    return new QSwitchAccessToken("shared-token", clock.instant().plusSeconds(120));
                },
                clock
        );

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(client::accessToken);
            assertThat(exchangeStarted.await(1, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(client::accessToken);

            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("shared-token");
            assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo("shared-token");
        }
        assertThat(calls).hasValue(1);
    }

    @Test
    void incompleteConfigurationReturnsASafeErrorWithoutLeakingCredentials() {
        var properties = new QSwitchProperties();
        properties.setEnabled(true);
        properties.setMode(QSwitchMode.QSWITCH);
        properties.setClientSecret("super-secret-value");
        var client = new QSwitchOAuthTokenClient(properties, ignored -> {
            throw new AssertionError("token transport must not be called");
        }, Clock.systemUTC());

        assertThatThrownBy(client::accessToken)
                .isInstanceOf(QSwitchIntegrationException.class)
                .hasMessageContaining("unavailable")
                .hasMessageNotContaining("super-secret-value");
    }

    @Test
    void tokenDiagnosticRepresentationIsAlwaysRedacted() {
        assertThat(new QSwitchAccessToken(
                "access-token-that-must-not-appear",
                Instant.parse("2026-01-01T00:10:00Z")
        ).toString()).isEqualTo("QSwitchAccessToken[redacted]");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
