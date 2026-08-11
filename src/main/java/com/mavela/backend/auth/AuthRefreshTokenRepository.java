package com.mavela.backend.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthRefreshTokenRepository
        extends JpaRepository<AuthRefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT refreshToken
            FROM AuthRefreshToken refreshToken
            WHERE refreshToken.tokenHash = :tokenHash
            """)
    Optional<AuthRefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE AuthRefreshToken refreshToken
            SET refreshToken.revokedAt = :revokedAt
            WHERE refreshToken.familyId = :familyId
              AND refreshToken.revokedAt IS NULL
            """)
    int revokeActiveFamily(
            @Param("familyId") UUID familyId,
            @Param("revokedAt") Instant revokedAt
    );
}