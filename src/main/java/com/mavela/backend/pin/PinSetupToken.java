package com.mavela.backend.pin;

import com.mavela.backend.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_pin_setup_tokens")
public class PinSetupToken {

    @Id
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

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    protected PinSetupToken() {
        // Required by JPA
    }

    public PinSetupToken(
            UUID id,
            Customer customer,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.id = id;
        this.customer = customer;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        if (consumedAt == null && invalidatedAt == null) {
            consumedAt = now;
        }
    }

    public void invalidate(Instant now) {
        if (consumedAt == null && invalidatedAt == null) {
            invalidatedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
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

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}