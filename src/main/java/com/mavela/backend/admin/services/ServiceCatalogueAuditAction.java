package com.mavela.backend.admin.services;

/** Immutable operational audit actions; neither action performs a payment. */
public enum ServiceCatalogueAuditAction {
    CONFIGURATION_UPDATED,
    DISPLAY_ORDER_REBALANCED
}
