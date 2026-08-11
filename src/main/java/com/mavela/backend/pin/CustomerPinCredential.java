package com.mavela.backend.pin;

import com.mavela.backend.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_pin_credentials")
public class CustomerPinCredential {

    @Id
    @Column(name = "customer_id")
    private UUID customerId;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @MapsId
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(
            name = "pin_hash",
            nullable = false,
            length = 255
    )
    private String pinHash;

    @Column(
            name = "failed_attempt_count",
            nullable = false
    )
    private int failedAttemptCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failed_attempt_at")
    private Instant lastFailedAttemptAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    protected CustomerPinCredential() {
        // Required by JPA
    }

    public CustomerPinCredential(
            Customer customer,
            String pinHash,
            Instant createdAt
    ) {
        this.customer = customer;
        this.pinHash = pinHash;
        this.failedAttemptCount = 0;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null
                && now.isBefore(lockedUntil);
    }

    public long getRemainingLockSeconds(Instant now) {
        if (!isLocked(now)) {
            return 0;
        }

        long remainingMillis = Duration
                .between(now, lockedUntil)
                .toMillis();

        return Math.max(
                1,
                (remainingMillis + 999) / 1000
        );
    }

    public void recordFailedAttempt(
            Instant now,
            int maximumAttempts,
            Duration lockDuration
    ) {
        /*
         * A completed lockout starts a new attempt window.
         */
        if (lockedUntil != null
                && !now.isBefore(lockedUntil)) {
            failedAttemptCount = 0;
            lockedUntil = null;
        }

        failedAttemptCount++;
        lastFailedAttemptAt = now;
        updatedAt = now;

        if (failedAttemptCount >= maximumAttempts) {
            lockedUntil = now.plus(lockDuration);
        }
    }

    public void clearFailedAttempts(Instant now) {
        failedAttemptCount = 0;
        lockedUntil = null;
        lastFailedAttemptAt = null;
        updatedAt = now;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getPinHash() {
        return pinHash;
    }

    public int getFailedAttemptCount() {
        return failedAttemptCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastFailedAttemptAt() {
        return lastFailedAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}