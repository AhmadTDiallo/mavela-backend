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
    void cachesTheStagingTokenPairUntilTheSafetyWindow() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var initialCalls = new AtomicInteger();
        var manager = manager(clock, new QSwitchTokenTransport() {
            @Override
            public QSwitchAccessToken requestToken(QSwitchProperties properties) {
                throw new AssertionError("legacy token path must not be used");
            }

            @Override
            public QSwitchTokenPair acquireInitialToken(QSwitchProperties properties) {
                return pair("access-" + initialCalls.incrementAndGet(), clock, 120);
            }
        });

        assertThat(manager.accessToken()).isEqualTo("access-1");
        assertThat(manager.accessToken()).isEqualTo("access-1");
        assertThat(initialCalls).hasValue(1);
    }

    @Test
    void refreshesBeforeExpiry() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var refreshCalls = new AtomicInteger();
        var manager = manager(clock, new QSwitchTokenTransport() {
            @Override
            public QSwitchAccessToken requestToken(QSwitchProperties properties) {
                throw new AssertionError("legacy token path must not be used");
            }

            @Override
            public QSwitchTokenPair acquireInitialToken(QSwitchProperties properties) {
                return pair("initial", clock, 120);
            }

            @Override
            public QSwitchTokenPair refreshToken(
                    QSwitchProperties properties,
                    QSwitchTokenPair currentToken
            ) {
                return pair("refreshed-" + refreshCalls.incrementAndGet(), clock, 120);
            }
        });

        assertThat(manager.accessToken()).isEqualTo("initial");
        clock.advanceSeconds(91);

        assertThat(manager.accessToken()).isEqualTo("refreshed-1");
        assertThat(refreshCalls).hasValue(1);
    }

    @Test
    void concurrentRequestsProduceOneInitialAuthenticationExchange() throws Exception {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var calls = new AtomicInteger();
        CountDownLatch exchangeStarted = new CountDownLatch(1);
        var manager = manager(clock, new QSwitchTokenTransport() {
            @Override
            public QSwitchAccessToken requestToken(QSwitchProperties properties) {
                throw new AssertionError("legacy token path must not be used");
            }

            @Override
            public QSwitchTokenPair acquireInitialToken(QSwitchProperties properties) {
                calls.incrementAndGet();
                exchangeStarted.countDown();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return pair("shared", clock, 120);
            }
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(manager::accessToken);
            assertThat(exchangeStarted.await(1, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(manager::accessToken);

            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("shared");
            assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo("shared");
        }
        assertThat(calls).hasValue(1);
    }

    @Test
    void refreshFailureFallsBackToFreshAuthentication() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var initialCalls = new AtomicInteger();
        var manager = manager(clock, new QSwitchTokenTransport() {
            @Override
            public QSwitchAccessToken requestToken(QSwitchProperties properties) {
                throw new AssertionError("legacy token path must not be used");
            }

            @Override
            public QSwitchTokenPair acquireInitialToken(QSwitchProperties properties) {
                return pair("initial-" + initialCalls.incrementAndGet(), clock, 120);
            }

            @Override
            public QSwitchTokenPair refreshToken(
                    QSwitchProperties properties,
                    QSwitchTokenPair currentToken
            ) {
                throw new QSwitchIntegrationException(
                        QSwitchIntegrationErrorCode.AUTHENTICATION_FAILED
                );
            }
        });

        assertThat(manager.accessToken()).isEqualTo("initial-1");
        clock.advanceSeconds(91);

        assertThat(manager.accessToken()).isEqualTo("initial-2");
        assertThat(initialCalls).hasValue(2);
    }

    @Test
    void incompleteConfigurationFailsWithoutLeakingCredentialsOrCallingTransport() {
        var properties = new QSwitchProperties();
        properties.setEnabled(true);
        properties.setAppSecret("test-secret-that-must-not-leak");
        var manager = new QSwitchStagingTokenManager(
                properties,
                new QSwitchTokenTransport() {
                    @Override
                    public QSwitchAccessToken requestToken(QSwitchProperties ignored) {
                        throw new AssertionError("token transport must not be called");
                    }
                },
                Clock.systemUTC()
        );

        assertThatThrownBy(manager::accessToken)
                .isInstanceOf(QSwitchIntegrationException.class)
                .hasMessageContaining("unavailable")
                .hasMessageNotContaining("test-secret-that-must-not-leak");
    }

    @Test
    void disabledConfigurationMakesNoOutboundTokenCall() {
        var properties = QSwitchPropertiesTests.completeStagingProperties();
        properties.setEnabled(false);
        var calls = new AtomicInteger();
        var manager = new QSwitchStagingTokenManager(
                properties,
                new QSwitchTokenTransport() {
                    @Override
                    public QSwitchAccessToken requestToken(QSwitchProperties ignored) {
                        throw new AssertionError("legacy token path must not be used");
                    }

                    @Override
                    public QSwitchTokenPair acquireInitialToken(QSwitchProperties ignored) {
                        calls.incrementAndGet();
                        throw new AssertionError("disabled configuration must not call out");
                    }
                },
                Clock.systemUTC()
        );

        assertThat(manager.authenticationStatus())
                .isEqualTo(QSwitchAuthenticationStatus.DISABLED);
        assertThat(calls).hasValue(0);
    }

    @Test
    void tokenDiagnosticRepresentationIsAlwaysRedacted() {
        assertThat(new QSwitchTokenPair(
                "access-token-that-must-not-appear",
                "refresh-token-that-must-not-appear",
                Instant.parse("2026-01-01T00:10:00Z")
        ).toString()).isEqualTo("QSwitchTokenPair[redacted]");
    }

    private QSwitchStagingTokenManager manager(
            Clock clock,
            QSwitchTokenTransport transport
    ) {
        return new QSwitchStagingTokenManager(
                QSwitchPropertiesTests.completeStagingProperties(),
                transport,
                clock
        );
    }

    private static QSwitchTokenPair pair(
            String accessToken,
            Clock clock,
            long expiresInSeconds
    ) {
        return new QSwitchTokenPair(
                accessToken,
                "refresh-" + accessToken,
                clock.instant().plusSeconds(expiresInSeconds)
        );
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
