package com.mavela.backend.rewards;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GemsLedgerEntryRepository
        extends JpaRepository<GemsLedgerEntry, UUID> {

    Optional<GemsLedgerEntry> findByCustomer_IdAndEntryTypeAndBusinessDate(
            UUID customerId,
            GemsLedgerEntryType entryType,
            LocalDate businessDate
    );

    org.springframework.data.domain.Page<GemsLedgerEntry>
    findByCustomer_IdOrderByCreatedAtDesc(
            UUID customerId,
            org.springframework.data.domain.Pageable pageable
    );

    long countByCustomer_IdAndEntryTypeAndBusinessDate(
            UUID customerId,
            GemsLedgerEntryType entryType,
            LocalDate businessDate
    );

    List<GemsLedgerEntry> findByCustomer_IdAndBusinessDateAndEntryTypeIn(
            UUID customerId,
            LocalDate businessDate,
            List<GemsLedgerEntryType> entryTypes
    );

    Optional<GemsLedgerEntry> findByIdAndCustomer_Id(
            UUID id,
            UUID customerId
    );

    Optional<GemsLedgerEntry> findByCustomer_IdAndPublicId(
            UUID customerId,
            UUID publicId
    );

    boolean existsByReversalOfEntry_Id(UUID originalEntryId);

    @Query("""
            SELECT reversal.reversalOfEntry.id
            FROM GemsLedgerEntry reversal
            WHERE reversal.customer.id = :customerId
              AND reversal.reversalOfEntry IS NOT NULL
            """)
    Set<UUID> findReversedOriginalEntryIdsByCustomerId(
            @Param("customerId") UUID customerId
    );

    @Query("""
            SELECT COALESCE(SUM(entry.amount), 0)
            FROM GemsLedgerEntry entry
            WHERE entry.customer.id = :customerId
              AND entry.status = :status
            """)
    long availableBalanceForCustomer(
            @Param("customerId") UUID customerId,
            @Param("status") GemsLedgerEntryStatus status
    );
}
