package com.mavela.backend.qswitch;

/**
 * Opaque provider-side customer reference. It is intentionally separate from
 * Mavela's customer UUID until a verified customer-to-QSwitch mapping exists.
 */
public record ExternalCustomerReference(String value) {

    public ExternalCustomerReference {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "External customer reference is required."
            );
        }
    }
}
