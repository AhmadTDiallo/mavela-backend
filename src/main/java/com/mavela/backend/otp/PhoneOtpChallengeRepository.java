package com.mavela.backend.otp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PhoneOtpChallengeRepository
        extends JpaRepository<PhoneOtpChallenge, UUID> {

    Optional<PhoneOtpChallenge>
    findFirstByCustomer_IdOrderByCreatedAtDesc(
            UUID customerId
    );

    Optional<PhoneOtpChallenge>
    findFirstByCustomer_IdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
            UUID customerId
    );

    Optional<PhoneOtpChallenge> findByIdAndCustomer_Id(
            UUID challengeId,
            UUID customerId
    );
}