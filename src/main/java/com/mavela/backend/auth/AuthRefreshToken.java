package com.mavela.backend.auth;

import com.mavela.backend.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_refresh_tokens")
public class AuthRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "customer_id",
            nullable = false
    )
    private Customer customer;

    @Column(
            name = "family_id",
            nullable = false
    )
    private UUID familyId;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64
    )
    private String tokenHash;

    @Column(
            name = "expires_at",
            nullable = false
    )
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    protected AuthRefreshToken() {
        // Required by JPA
    }

    public AuthRefreshToken(
            Customer customer,
            UUID familyId,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.customer = customer;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void consume(
            Instant consumedAt,
            UUID replacedByTokenId
    ) {
        if (this.consumedAt != null) {
            throw new IllegalStateException(
                    "Refresh token has already been consumed."
            );
        }

        this.consumedAt = consumedAt;
        this.replacedByTokenId = replacedByTokenId;
    }

    public void revoke(Instant revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedByTokenId() {
        return replacedByTokenId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}