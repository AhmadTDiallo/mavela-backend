package com.mavela.backend.otp;

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
@Table(name = "phone_otp_challenges")
public class PhoneOtpChallenge {

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
            name = "code_hash",
            nullable = false,
            length = 64
    )
    private String codeHash;

    @Column(
            name = "expires_at",
            nullable = false
    )
    private Instant expiresAt;

    @Column(
            name = "attempt_count",
            nullable = false
    )
    private int attemptCount;

    @Column(
            name = "max_attempts",
            nullable = false
    )
    private int maxAttempts;

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

    protected PhoneOtpChallenge() {
        // Required by JPA
    }

    public PhoneOtpChallenge(
            UUID id,
            Customer customer,
            String codeHash,
            Instant expiresAt,
            int maxAttempts,
            Instant createdAt
    ) {
        this.id = id;
        this.customer = customer;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.maxAttempts = maxAttempts;
        this.createdAt = createdAt;
        this.attemptCount = 0;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean hasAttemptsRemaining() {
        return attemptCount < maxAttempts;
    }

    public int getRemainingAttempts() {
        return Math.max(0, maxAttempts - attemptCount);
    }

    public void recordFailedAttempt() {
        if (attemptCount < maxAttempts) {
            attemptCount++;
        }
    }

    public void consume(Instant now) {
        consumedAt = now;
    }

    public void invalidate(Instant now) {
        if (consumedAt == null) {
            invalidatedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
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