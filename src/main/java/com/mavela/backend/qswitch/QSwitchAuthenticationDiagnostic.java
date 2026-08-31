package com.mavela.backend.qswitch;

/** Internal-only safe diagnostic with no HTTP endpoint. */
public final class QSwitchAuthenticationDiagnostic {

    private final QSwitchStagingTokenManager tokenManager;

    QSwitchAuthenticationDiagnostic(QSwitchStagingTokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    public QSwitchAuthenticationStatus status() {
        return tokenManager.authenticationStatus();
    }
}
