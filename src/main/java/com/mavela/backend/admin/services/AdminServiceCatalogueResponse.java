package com.mavela.backend.admin.services;

import com.mavela.backend.services.catalogue.ServiceCatalogueEntry;
import com.mavela.backend.services.catalogue.ServiceAvailability;
import com.mavela.backend.services.catalogue.ServiceCategory;
import com.mavela.backend.services.catalogue.ServiceInputRequirement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Safe staff configuration view; internal notes and database IDs stay private. */
public record AdminServiceCatalogueResponse(
        String serviceCode,
        ServiceCategory category,
        ServiceAvailability availability,
        boolean customerVisible,
        short displayOrder,
        String providerDisplayName,
        List<String> supportedCurrencies,
        List<ServiceInputRequirement> inputRequirements,
        BigDecimal minimumAmount,
        BigDecimal maximumAmount,
        Instant updatedAt
) {
    static AdminServiceCatalogueResponse from(ServiceCatalogueEntry entry) {
        return new AdminServiceCatalogueResponse(
                entry.getServiceCode(),
                entry.getCategory(),
                entry.getAvailability(),
                entry.isCustomerVisible(),
                entry.getDisplayOrder(),
                entry.getProviderDisplayName(),
                entry.getSupportedCurrencies(),
                entry.getInputRequirements(),
                entry.getMinimumAmount(),
                entry.getMaximumAmount(),
                entry.getUpdatedAt()
        );
    }
}
