package com.mavela.backend.services.catalogue;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceCatalogueRepository
        extends JpaRepository<ServiceCatalogueEntry, String> {

    @EntityGraph(attributePaths = {"inputRequirements", "supportedCurrencies"})
    List<ServiceCatalogueEntry> findAllByOrderByDisplayOrderAsc();

    @EntityGraph(attributePaths = {"inputRequirements", "supportedCurrencies"})
    Optional<ServiceCatalogueEntry> findByServiceCode(String serviceCode);
}
