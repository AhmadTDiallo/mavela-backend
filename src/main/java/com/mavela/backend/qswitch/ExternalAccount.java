package com.mavela.backend.qswitch;

/**
 * Mavela-owned account representation. It deliberately does not model raw
 * QSwitch response payloads or account-number formats.
 */
public record ExternalAccount(
        ExternalAccountReference reference,
        ExternalAccountCurrency currency,
        String displayName
) {
}
