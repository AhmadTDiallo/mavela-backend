package com.mavela.backend.rewards;

import com.mavela.backend.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Append-only non-monetary rewards ledger. Mutations must create compensating
 * entries rather than changing an existing amount or status.
 */
@Entity
@Immutable
@Table(name = "gems_ledger_entries")
public class GemsLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32, updatable = false)
    private GemsLedgerEntryType entryType;

    @Column(nullable = false, updatable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private GemsLedgerEntryStatus status;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(nullable = false, length = 200, updatable = false)
    private String reference;

    @Column(name = "idempotency_key", length = 160, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 24, updatable = false)
    private RewardAuditActorType actorType;

    @Column(name = "actor_reference", length = 120, updatable = false)
    private String actorReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GemsLedgerEntry() {
        // Required by JPA.
    }

    public GemsLedgerEntry(
            Customer customer,
            GemsLedgerEntryType entryType,
            int amount,
            GemsLedgerEntryStatus status,
            LocalDate businessDate,
            String reference,
            String idempotencyKey,
            RewardAuditActorType actorType,
            String actorReference
    ) {
        if (amount == 0) {
            throw new IllegalArgumentException("Gems ledger amount cannot be zero.");
        }
        if (reference == null || reference.isBlank() || reference.length() > 200) {
            throw new IllegalArgumentException("Gems ledger reference is invalid.");
        }
        if (idempotencyKey != null
                && (idempotencyKey.isBlank() || idempotencyKey.length() > 160)) {
            throw new IllegalArgumentException("Gems ledger idempotency key is invalid.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Gems ledger status is required.");
        }
        if (actorType == null) {
            throw new IllegalArgumentException("Gems ledger actor type is required.");
        }
        if (actorReference != null
                && (actorReference.isBlank() || actorReference.length() > 120)) {
            throw new IllegalArgumentException("Gems ledger actor reference is invalid.");
        }

        this.customer = customer;
        this.entryType = entryType;
        this.amount = amount;
        this.status = status;
        this.businessDate = businessDate;
        this.reference = reference;
        this.idempotencyKey = idempotencyKey;
        this.actorType = actorType;
        this.actorReference = actorReference;
    }

    /**
     * Internal constructor for established non-staff reward commands. Future
     * manual adjustments must supply an explicit audit actor.
     */
    public GemsLedgerEntry(
            Customer customer,
            GemsLedgerEntryType entryType,
            int amount,
            GemsLedgerEntryStatus status,
            LocalDate businessDate,
            String reference,
            String idempotencyKey
    ) {
        this(
                customer,
                entryType,
                amount,
                status,
                businessDate,
                reference,
                idempotencyKey,
                RewardAuditActorType.SYSTEM,
                "rewards-service"
        );
    }

    public static GemsLedgerEntry dailyCheckIn(
            Customer customer,
            LocalDate businessDate,
            int amount
    ) {
        return new GemsLedgerEntry(
                customer, GemsLedgerEntryType.DAILY_CHECK_IN, amount,
                GemsLedgerEntryStatus.AVAILABLE, businessDate,
                "Daily check-in", "daily-check-in:" + businessDate,
                RewardAuditActorType.CUSTOMER, "self-service"
        );
    }

    public static GemsLedgerEntry streakMilestone(
            Customer customer,
            LocalDate businessDate,
            int streakDay,
            int amount
    ) {
        return new GemsLedgerEntry(
                customer, GemsLedgerEntryType.STREAK_MILESTONE, amount,
                GemsLedgerEntryStatus.AVAILABLE, businessDate,
                "Day " + streakDay + " streak milestone",
                "streak-milestone:" + businessDate + ":" + streakDay,
                RewardAuditActorType.SYSTEM, null
        );
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public GemsLedgerEntryType getEntryType() {
        return entryType;
    }

    public int getAmount() {
        return amount;
    }

    public GemsLedgerEntryStatus getStatus() {
        return status;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getReference() {
        return reference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public RewardAuditActorType getActorType() {
        return actorType;
    }

    public String getActorReference() {
        return actorReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
