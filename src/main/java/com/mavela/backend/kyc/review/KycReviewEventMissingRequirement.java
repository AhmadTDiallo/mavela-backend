package com.mavela.backend.kyc.review;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "kyc_review_event_missing_requirements")
public class KycReviewEventMissingRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_event_id", nullable = false)
    private KycReviewEvent reviewEvent;

    @Enumerated(EnumType.STRING)
    private KycMissingRequirement requirement;

    protected KycReviewEventMissingRequirement() {
        // Required by JPA.
    }

    KycReviewEventMissingRequirement(
            KycReviewEvent reviewEvent,
            KycMissingRequirement requirement
    ) {
        this.reviewEvent = reviewEvent;
        this.requirement = requirement;
    }

    public UUID getId() { return id; }
    public KycReviewEvent getReviewEvent() { return reviewEvent; }
    public KycMissingRequirement getRequirement() { return requirement; }
}
