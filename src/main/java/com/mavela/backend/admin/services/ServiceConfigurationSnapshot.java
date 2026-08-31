package com.mavela.backend.admin.services;

import com.mavela.backend.services.catalogue.ServiceAvailability;
import com.mavela.backend.services.catalogue.ServiceCatalogueEntry;

/** Small private value type used to create an immutable audit event. */
record ServiceConfigurationSnapshot(
        ServiceAvailability availability,
        boolean customerVisible,
        short displayOrder
) {
    static ServiceConfigurationSnapshot from(ServiceCatalogueEntry entry) {
        return new ServiceConfigurationSnapshot(
                entry.getAvailability(),
                entry.isCustomerVisible(),
                entry.getDisplayOrder()
        );
    }
}
