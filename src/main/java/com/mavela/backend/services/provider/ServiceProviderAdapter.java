package com.mavela.backend.services.provider;

import com.mavela.backend.services.catalogue.ServiceCategory;

/**
 * Future boundary for approved external providers. Implementations must use
 * server-side configuration and must never expose provider credentials to a
 * customer API or the catalogue response.
 */
public interface ServiceProviderAdapter {

    String providerKey();

    boolean supports(ServiceCategory category);

    boolean isOperationallyReady();
}
