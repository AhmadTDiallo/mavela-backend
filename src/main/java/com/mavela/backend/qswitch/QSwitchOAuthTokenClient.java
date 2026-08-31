package com.mavela.backend.qswitch;

import java.time.Clock;
/**
 * @deprecated Use {@link QSwitchStagingTokenManager}. Retained as a source
 * compatibility wrapper so callers cannot create a second token cache.
 */
@Deprecated
public class QSwitchOAuthTokenClient {

    private final QSwitchStagingTokenManager tokenManager;

    public QSwitchOAuthTokenClient(
            QSwitchProperties properties,
            QSwitchTokenTransport tokenTransport,
            Clock clock
    ) {
        this.tokenManager = new QSwitchStagingTokenManager(
                properties,
                tokenTransport,
                clock
        );
    }

    public String accessToken() {
        return tokenManager.accessToken();
    }

    /** Clears only volatile memory after an authenticated provider read fails. */
    public void invalidate() {
        tokenManager.invalidate();
    }
}
