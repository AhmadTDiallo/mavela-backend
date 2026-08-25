package com.mavela.backend.admin.rewards;

import com.mavela.backend.admin.staff.StaffUser;
import com.mavela.backend.customer.Customer;
import com.mavela.backend.rewards.GemsLedgerEntry;
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
import java.util.UUID;

/**
 * Append-only staff audit data. Internal notes are intentionally kept outside
 * the customer-facing rewards model and no controller exposes this entity.
 */
@Entity
@Immutable
@Table(name = "rewards_admin_audit_events")
public class RewardsAdminAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_user_id", nullable = false)
    private StaffUser staffUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resulting_ledger_entry_id", nullable = false)
    private GemsLedgerEntry resultingLedgerEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_ledger_entry_id")
    private GemsLedgerEntry originalLedgerEntry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private RewardsAdminAction action;

    @Column(name = "reason_code", nullable = false, length = 64,
            updatable = false)
    private String reasonCode;

    @Column(name = "internal_note", nullable = false, length = 500,
            updatable = false)
    private String internalNote;

    @Column(nullable = false, updatable = false)
    private int amount;

    @Column(name = "idempotency_key", nullable = false, length = 160,
            updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RewardsAdminAuditEvent() {
        // Required by JPA.
    }

    public RewardsAdminAuditEvent(
            StaffUser staffUser,
            Customer customer,
            GemsLedgerEntry resultingLedgerEntry,
            GemsLedgerEntry originalLedgerEntry,
            RewardsAdminAction action,
            String reasonCode,
            String internalNote,
            int amount,
            String idempotencyKey
    ) {
        this.staffUser = staffUser;
        this.customer = customer;
        this.resultingLedgerEntry = resultingLedgerEntry;
        this.originalLedgerEntry = originalLedgerEntry;
        this.action = action;
        this.reasonCode = reasonCode;
        this.internalNote = internalNote;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public StaffUser getStaffUser() {
        return staffUser;
    }

    public Customer getCustomer() {
        return customer;
    }

    public GemsLedgerEntry getResultingLedgerEntry() {
        return resultingLedgerEntry;
    }

    public GemsLedgerEntry getOriginalLedgerEntry() {
        return originalLedgerEntry;
    }

    public RewardsAdminAction getAction() {
        return action;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getInternalNote() {
        return internalNote;
    }

    public int getAmount() {
        return amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
