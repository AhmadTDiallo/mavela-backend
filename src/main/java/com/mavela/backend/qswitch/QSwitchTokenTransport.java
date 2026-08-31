package com.mavela.backend.qswitch;

/** Low-level boundary for the configured QSwitch token exchanges. */
public interface QSwitchTokenTransport {

    QSwitchAccessToken requestToken(QSwitchProperties properties);

    default QSwitchTokenPair acquireInitialToken(QSwitchProperties properties) {
        throw new QSwitchIntegrationException(
                QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
        );
    }

    default QSwitchTokenPair refreshToken(
            QSwitchProperties properties,
            QSwitchTokenPair currentToken
    ) {
        throw new QSwitchIntegrationException(
                QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
        );
    }
}
