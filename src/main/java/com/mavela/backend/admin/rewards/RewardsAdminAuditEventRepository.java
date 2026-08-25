package com.mavela.backend.admin.rewards;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RewardsAdminAuditEventRepository
        extends JpaRepository<RewardsAdminAuditEvent, UUID> {

    boolean existsByStaffUser_IdAndCustomer_IdAndActionAndIdempotencyKey(
            UUID staffUserId,
            UUID customerId,
            RewardsAdminAction action,
            String idempotencyKey
    );

    boolean existsByOriginalLedgerEntry_Id(UUID originalLedgerEntryId);

    Page<RewardsAdminAuditEvent> findByCustomer_IdOrderByCreatedAtDesc(
            UUID customerId,
            Pageable pageable
    );
}
