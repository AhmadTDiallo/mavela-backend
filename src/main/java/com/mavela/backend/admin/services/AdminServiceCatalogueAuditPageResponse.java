package com.mavela.backend.admin.services;

import java.util.List;

/** Capped immutable audit-history page. */
public record AdminServiceCatalogueAuditPageResponse(
        List<AdminServiceCatalogueAuditEventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
