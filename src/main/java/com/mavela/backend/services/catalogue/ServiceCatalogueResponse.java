package com.mavela.backend.services.catalogue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Safe customer response. It intentionally contains no database identifiers. */
public record ServiceCatalogueResponse(
        String serviceCode,
        ServiceCategory category,
        ServiceAvailability availability,
        String providerDisplayName,
        List<String> supportedCurrencies,
        List<ServiceInputRequirement> inputRequirements,
        BigDecimal minimumAmount,
        BigDecimal maximumAmount,
        Instant updatedAt
) {
    public static ServiceCatalogueResponse from(ServiceCatalogueEntry entry) {
        boolean isAvailable = entry.getAvailability() == ServiceAvailability.AVAILABLE;

        return new ServiceCatalogueResponse(
                entry.getServiceCode(),
                entry.getCategory(),
                entry.getAvailability(),
                isAvailable ? entry.getProviderDisplayName() : null,
                entry.getSupportedCurrencies(),
                entry.getInputRequirements(),
                isAvailable ? entry.getMinimumAmount() : null,
                isAvailable ? entry.getMaximumAmount() : null,
                entry.getUpdatedAt()
        );
    }
}
