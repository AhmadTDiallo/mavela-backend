package com.mavela.backend.admin.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ServiceCatalogueAdminAuditEventRepository
        extends JpaRepository<ServiceCatalogueAdminAuditEvent, UUID> {

    Optional<ServiceCatalogueAdminAuditEvent>
    findByStaffUser_IdAndServiceCodeAndActionAndIdempotencyKey(
            UUID staffUserId,
            String serviceCode,
            ServiceCatalogueAuditAction action,
            String idempotencyKey
    );

    Page<ServiceCatalogueAdminAuditEvent> findByServiceCodeOrderByCreatedAtDesc(
            String serviceCode,
            Pageable pageable
    );
}
