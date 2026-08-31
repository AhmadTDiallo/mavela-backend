package com.mavela.backend.services.catalogue;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ServiceCatalogueRepository
        extends JpaRepository<ServiceCatalogueEntry, String> {

    @EntityGraph(attributePaths = {"inputRequirements", "supportedCurrencies"})
    List<ServiceCatalogueEntry> findAllByOrderByDisplayOrderAsc();

    @EntityGraph(attributePaths = {"inputRequirements", "supportedCurrencies"})
    Optional<ServiceCatalogueEntry> findByServiceCode(String serviceCode);

    /** Serializes operational reorder commands across the small fixed catalogue. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT entry FROM ServiceCatalogueEntry entry ORDER BY entry.displayOrder ASC")
    List<ServiceCatalogueEntry> findAllForOperationalUpdate();

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ServiceCatalogueEntry entry
            SET entry.displayOrder = entry.displayOrder - 1,
                entry.version = entry.version + 1,
                entry.updatedAt = CURRENT_TIMESTAMP
            WHERE entry.displayOrder > :previousOrder
              AND entry.displayOrder <= :requestedOrder
              AND entry.serviceCode <> :serviceCode
            """)
    int shiftOrdersDown(
            short previousOrder,
            short requestedOrder,
            String serviceCode
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ServiceCatalogueEntry entry
            SET entry.displayOrder = entry.displayOrder + 1,
                entry.version = entry.version + 1,
                entry.updatedAt = CURRENT_TIMESTAMP
            WHERE entry.displayOrder >= :requestedOrder
              AND entry.displayOrder < :previousOrder
              AND entry.serviceCode <> :serviceCode
            """)
    int shiftOrdersUp(
            short previousOrder,
            short requestedOrder,
            String serviceCode
    );
}
