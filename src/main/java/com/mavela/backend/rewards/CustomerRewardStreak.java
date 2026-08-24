package com.mavela.backend.rewards;

import com.mavela.backend.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One server-owned streak state per customer. The immutable ledger remains
 * the source of truth for the Gems balance itself.
 */
@Entity
@Table(name = "customer_reward_streaks")
public class CustomerRewardStreak {

    @Id
    @Column(name = "customer_id")
    private UUID customerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "current_streak_days", nullable = false)
    private int currentStreakDays;

    @Column(name = "last_qualified_business_date")
    private LocalDate lastQualifiedBusinessDate;

    @Column(name = "last_check_in_business_date")
    private LocalDate lastCheckInBusinessDate;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerRewardStreak() {
        // Required by JPA.
    }

    public CustomerRewardStreak(Customer customer) {
        this.customer = customer;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Records an eligible daily check-in. Same-day idempotency is enforced by
     * the service and database ledger constraint before this method is used.
     */
    public int recordQualifiedCheckIn(LocalDate businessDate) {
        if (lastQualifiedBusinessDate == null
                || !businessDate.equals(lastQualifiedBusinessDate.plusDays(1))) {
            currentStreakDays = 1;
        } else {
            currentStreakDays++;
        }

        lastQualifiedBusinessDate = businessDate;
        lastCheckInBusinessDate = businessDate;
        return currentStreakDays;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public int getCurrentStreakDays() {
        return currentStreakDays;
    }

    public LocalDate getLastQualifiedBusinessDate() {
        return lastQualifiedBusinessDate;
    }

    public LocalDate getLastCheckInBusinessDate() {
        return lastCheckInBusinessDate;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
