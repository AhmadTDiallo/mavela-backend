package com.mavela.backend.kyc.review;

import com.mavela.backend.admin.staff.StaffUser;
import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.kyc.KycApplication;
import com.mavela.backend.kyc.KycDocument;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Append-only review audit record. Its constructor captures server-derived
 * actor and state, and the application exposes no mutation methods.
 */
@Entity
@Table(name = "kyc_review_events")
public class KycReviewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kyc_application_id", nullable = false)
    private KycApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private StaffUser reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private KycReviewAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 24)
    private KycStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 24)
    private KycStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 64)
    private KycReviewReasonCode reasonCode;

    @Column(name = "customer_message", length = 500)
    private String customerMessage;

    @Column(name = "internal_notes", length = 2000)
    private String internalNotes;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(
            mappedBy = "reviewEvent",
            cascade = CascadeType.ALL,
            orphanRemoval = false
    )
    private List<KycReviewEventEvidence> evidenceReferences = new ArrayList<>();

    @OneToMany(
            mappedBy = "reviewEvent",
            cascade = CascadeType.ALL,
            orphanRemoval = false
    )
    private List<KycReviewEventMissingRequirement> missingRequirements =
            new ArrayList<>();

    protected KycReviewEvent() {
        // Required by JPA.
    }

    public KycReviewEvent(
            KycApplication application,
            StaffUser reviewer,
            KycReviewAction action,
            KycStatus previousStatus,
            KycStatus newStatus,
            KycReviewReasonCode reasonCode,
            String customerMessage,
            String internalNotes,
            UUID correlationId
    ) {
        this.application = application;
        this.reviewer = reviewer;
        this.action = action;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reasonCode = reasonCode;
        this.customerMessage = customerMessage;
        this.internalNotes = internalNotes;
        this.correlationId = correlationId;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public void addEvidenceReference(KycDocument evidence) {
        evidenceReferences.add(new KycReviewEventEvidence(this, evidence));
    }

    public void addMissingRequirement(KycMissingRequirement requirement) {
        missingRequirements.add(
                new KycReviewEventMissingRequirement(this, requirement)
        );
    }

    public UUID getId() { return id; }
    public KycApplication getApplication() { return application; }
    public StaffUser getReviewer() { return reviewer; }
    public KycReviewAction getAction() { return action; }
    public KycStatus getPreviousStatus() { return previousStatus; }
    public KycStatus getNewStatus() { return newStatus; }
    public KycReviewReasonCode getReasonCode() { return reasonCode; }
    public String getCustomerMessage() { return customerMessage; }
    public String getInternalNotes() { return internalNotes; }
    public UUID getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }

    public List<KycReviewEventEvidence> getEvidenceReferences() {
        return Collections.unmodifiableList(evidenceReferences);
    }

    public List<KycReviewEventMissingRequirement> getMissingRequirements() {
        return Collections.unmodifiableList(missingRequirements);
    }
}
