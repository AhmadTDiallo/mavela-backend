package com.mavela.backend.kyc.review;

import com.mavela.backend.kyc.KycDocument;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "kyc_review_event_evidence")
public class KycReviewEventEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_event_id", nullable = false)
    private KycReviewEvent reviewEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false)
    private KycDocument evidence;

    protected KycReviewEventEvidence() {
        // Required by JPA.
    }

    KycReviewEventEvidence(KycReviewEvent reviewEvent, KycDocument evidence) {
        this.reviewEvent = reviewEvent;
        this.evidence = evidence;
    }

    public UUID getId() { return id; }
    public KycReviewEvent getReviewEvent() { return reviewEvent; }
    public KycDocument getEvidence() { return evidence; }
}
