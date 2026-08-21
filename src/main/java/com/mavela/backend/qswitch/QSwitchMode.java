package com.mavela.backend.qswitch;

/**
 * QSwitch is deliberately opt-in. MOCK is useful only when the integration
 * has been explicitly enabled for local development or tests.
 */
public enum QSwitchMode {
    MOCK,
    QSWITCH
}
