package com.mavela.backend.qswitch;

/**
 * Opaque provider-side account reference. This value must never be forwarded
 * to Flutter as a raw provider identifier.
 */
public record ExternalAccountReference(String value) {

    public ExternalAccountReference {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "External account reference is required."
            );
        }
    }
}
