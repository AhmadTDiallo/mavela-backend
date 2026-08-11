package com.mavela.backend.pin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PinSetupTokenRepository
        extends JpaRepository<PinSetupToken, UUID> {

    Optional<PinSetupToken>
    findFirstByCustomer_IdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
            UUID customerId
    );
}