package com.mavela.backend.qswitch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;

/**
 * @deprecated The confirmed QSwitch staging contract is not OAuth client
 * credentials. Use {@link QSwitchStagingTokenManager}; this compatibility
 * type deliberately fails closed rather than issuing a request with the old,
 * unconfirmed contract.
 */
@Deprecated
public final class QSwitchOAuthClient {

    QSwitchOAuthClient(
            QSwitchProperties properties,
            QSwitchHttpTransport transport,
            ObjectMapper objectMapper,
            Clock clock
    ) { }

    public String getAccessToken() {
        throw unavailable();
    }

    public void invalidate() { }

    private QSwitchIntegrationException unavailable() {
        return new QSwitchIntegrationException(
                QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
        );
    }
}
