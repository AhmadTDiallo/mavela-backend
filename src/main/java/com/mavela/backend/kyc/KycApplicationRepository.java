package com.mavela.backend.kyc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KycApplicationRepository
        extends JpaRepository<KycApplication, UUID> {

    Optional<KycApplication> findFirstByCustomer_IdOrderByAttemptNumberDesc(
            UUID customerId
    );

    boolean existsByCustomer_Id(UUID customerId);
}
