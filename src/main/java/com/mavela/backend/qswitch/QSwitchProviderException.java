package com.mavela.backend.qswitch;

/**
 * Carries a stable Mavela error code without retaining or exposing sensitive
 * provider response details.
 */
public class QSwitchProviderException extends RuntimeException {

    private final QSwitchIntegrationErrorCode code;

    public QSwitchProviderException(QSwitchIntegrationErrorCode code) {
        super(code.getSafeMessage());
        this.code = code;
    }

    public QSwitchProviderException(QSwitchIntegrationErrorCode code, Throwable cause) {
        super(code.getSafeMessage(), cause);
        this.code = code;
    }

    public QSwitchIntegrationErrorCode getCode() {
        return code;
    }
}
