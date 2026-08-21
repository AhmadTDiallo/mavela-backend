package com.mavela.backend.qswitch;

/**
 * The QSwitch token request contract must be explicitly confirmed before a
 * live integration is enabled. No request format is assumed by default.
 */
public enum QSwitchTokenRequestEncoding {
    UNCONFIRMED,
    FORM_URLENCODED_CLIENT_CREDENTIALS,
    JSON_CLIENT_CREDENTIALS,
    /** @deprecated Use FORM_URLENCODED_CLIENT_CREDENTIALS for new configuration. */
    @Deprecated
    FORM_CLIENT_CREDENTIALS,
    /** @deprecated Retained only for source compatibility; not enabled by default. */
    @Deprecated
    BASIC_CLIENT_CREDENTIALS
}
