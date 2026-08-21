package com.mavela.backend.qswitch;

import java.time.Duration;

/**
 * Reusable execution policy for future typed, idempotent QSwitch reads only.
 * There is intentionally no corresponding executor for transfers, reversals,
 * account provisioning, or any other state-changing operation.
 */
public final class QSwitchReadExecutor {

    private final QSwitchOAuthTokenClient tokenClient;
    private final QSwitchReadRetryPolicy retryPolicy;
    private final Sleeper sleeper;

    public QSwitchReadExecutor(
            QSwitchOAuthTokenClient tokenClient,
            QSwitchReadRetryPolicy retryPolicy
    ) {
        this(tokenClient, retryPolicy, duration -> Thread.sleep(duration.toMillis()));
    }

    QSwitchReadExecutor(
            QSwitchOAuthTokenClient tokenClient,
            QSwitchReadRetryPolicy retryPolicy,
            Sleeper sleeper
    ) {
        this.tokenClient = tokenClient;
        this.retryPolicy = retryPolicy;
        this.sleeper = sleeper;
    }

    public <T> T execute(IdempotentRead<T> read) {
        int completedRetries = 0;
        boolean authenticationRetryUsed = false;

        while (true) {
            String accessToken = tokenClient.accessToken();
            try {
                return read.execute(accessToken);
            } catch (QSwitchIntegrationException exception) {
                if (exception.getErrorCode() == QSwitchIntegrationErrorCode.AUTHENTICATION_FAILED
                        && !authenticationRetryUsed) {
                    tokenClient.invalidate();
                    authenticationRetryUsed = true;
                    continue;
                }
                if (!retryPolicy.shouldRetry(exception.getErrorCode(), completedRetries)) {
                    throw exception;
                }
                sleep(retryPolicy.backoffFor(completedRetries + 1, exception.getRetryAfter()));
                completedRetries++;
            }
        }
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.PROVIDER_UNAVAILABLE,
                    exception
            );
        }
    }

    @FunctionalInterface
    public interface IdempotentRead<T> {
        T execute(String accessToken);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
