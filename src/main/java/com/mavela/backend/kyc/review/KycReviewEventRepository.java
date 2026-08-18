package com.mavela.backend.kyc.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycReviewEventRepository
        extends JpaRepository<KycReviewEvent, UUID> {

    List<KycReviewEvent> findAllByApplication_IdOrderByCreatedAtAsc(
            UUID applicationId
    );

    Optional<KycReviewEvent>
    findFirstByApplication_IdAndActionOrderByCreatedAtDesc(
            UUID applicationId,
            KycReviewAction action
    );
}
