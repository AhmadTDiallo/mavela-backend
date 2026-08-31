package com.mavela.backend.admin.services;

import com.mavela.backend.services.catalogue.ServiceAvailability;

import java.time.Instant;

/** Safe staff audit projection. Staff-only notes and idempotency values remain server-only. */
public record AdminServiceCatalogueAuditEventResponse(
        ServiceCatalogueAuditAction action,
        ServiceAvailability previousAvailability,
        ServiceAvailability newAvailability,
        boolean previousCustomerVisible,
        boolean newCustomerVisible,
        short previousDisplayOrder,
        short newDisplayOrder,
        ServiceCatalogueChangeReasonCode reasonCode,
        String staffDisplayName,
        Instant createdAt
) {
    static AdminServiceCatalogueAuditEventResponse from(
            ServiceCatalogueAdminAuditEvent event
    ) {
        return new AdminServiceCatalogueAuditEventResponse(
                event.getAction(),
                event.getPreviousAvailability(),
                event.getNewAvailability(),
                event.isPreviousCustomerVisible(),
                event.isNewCustomerVisible(),
                event.getPreviousDisplayOrder(),
                event.getNewDisplayOrder(),
                event.getReasonCode(),
                event.getStaffUser().getDisplayName(),
                event.getCreatedAt()
        );
    }
}
