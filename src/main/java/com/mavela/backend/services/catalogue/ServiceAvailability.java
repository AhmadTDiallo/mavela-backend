package com.mavela.backend.services.catalogue;

/**
 * Customer-safe availability. AVAILABLE is not a purchase confirmation and
 * requires explicit provider and funding readiness before it can be exposed.
 */
public enum ServiceAvailability {
    COMING_SOON,
    UNAVAILABLE,
    AVAILABLE
}
