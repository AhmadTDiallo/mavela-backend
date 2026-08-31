package com.mavela.backend.qswitch;

/** Low-level boundary for the supported QSwitch application-token exchange. */
public interface QSwitchTokenTransport {

    QSwitchAccessToken requestToken(QSwitchProperties properties);

    default QSwitchTokenPair acquireInitialToken(QSwitchProperties properties) {
        throw new QSwitchIntegrationException(
                QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
        );
    }
}
