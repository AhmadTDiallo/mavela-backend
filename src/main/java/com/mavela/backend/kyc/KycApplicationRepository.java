package com.mavela.backend.kyc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface KycApplicationRepository
        extends JpaRepository<KycApplication, UUID>,
        JpaSpecificationExecutor<KycApplication> {

    Optional<KycApplication> findFirstByCustomer_IdOrderByAttemptNumberDesc(
            UUID customerId
    );

    boolean existsByCustomer_Id(UUID customerId);

    Optional<KycApplication> findFirstByCustomer_IdAndStatusOrderByAttemptNumberDesc(
            UUID customerId,
            com.mavela.backend.customer.KycStatus status
    );
}
