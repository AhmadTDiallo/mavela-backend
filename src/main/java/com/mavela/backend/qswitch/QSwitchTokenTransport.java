package com.mavela.backend.qswitch;

/** Low-level boundary for the configured OAuth token exchange. */
public interface QSwitchTokenTransport {

    QSwitchAccessToken requestToken(QSwitchProperties properties);
}
