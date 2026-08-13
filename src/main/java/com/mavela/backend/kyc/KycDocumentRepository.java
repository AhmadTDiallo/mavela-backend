package com.mavela.backend.kyc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycDocumentRepository
        extends JpaRepository<KycDocument, UUID> {

    Optional<KycDocument> findByIdAndApplication_Customer_IdAndDeletedAtIsNull(
            UUID evidenceId,
            UUID customerId
    );

    /**
     * Application-scoped lookup for staff review operations. The caller must
     * authorize access to the application before opening its evidence.
     */
    Optional<KycDocument> findByIdAndApplication_IdAndDeletedAtIsNull(
            UUID evidenceId,
            UUID applicationId
    );

    List<KycDocument> findAllByApplication_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID applicationId
    );

    List<KycDocument> findAllByApplication_IdAndEvidenceTypeAndDocumentSideAndDeletedAtIsNull(
            UUID applicationId,
            KycEvidenceType evidenceType,
            KycDocumentSide documentSide
    );
}
