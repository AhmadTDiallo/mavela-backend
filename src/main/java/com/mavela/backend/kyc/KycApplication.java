package com.mavela.backend.kyc;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.admin.staff.StaffUser;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "kyc_applications")
public class KycApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private KycStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "review_started_at")
    private Instant reviewStartedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_reviewer_id")
    private StaffUser assignedReviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 40)
    private KycDraftStep currentStep = KycDraftStep.CONFIRM_INFORMATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 24)
    private KycDocumentType documentType;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "last_saved_at", nullable = false)
    private Instant lastSavedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "profile_first_name", length = 100)
    private String profileFirstName;

    @Column(name = "profile_last_name", length = 100)
    private String profileLastName;

    @Column(name = "profile_preferred_locale", length = 10)
    private String profilePreferredLocale;

    @Column(name = "profile_date_of_birth")
    private LocalDate profileDateOfBirth;

    @Column(name = "profile_nationality", length = 2)
    private String profileNationality;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_gender", length = 24)
    private com.mavela.backend.customer.Gender profileGender;

    @Column(name = "profile_address_line", length = 200)
    private String profileAddressLine;

    @Column(name = "profile_city", length = 100)
    private String profileCity;

    @Column(name = "profile_province", length = 100)
    private String profileProvince;

    @OneToMany(
            mappedBy = "application",
            cascade = CascadeType.ALL,
            orphanRemoval = false
    )
    private List<KycDocument> documents = new ArrayList<>();

    protected KycApplication() {
        // Required by JPA
    }

    public KycApplication(Customer customer, int attemptNumber) {
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException(
                    "KYC attempt number must be greater than zero."
            );
        }

        this.customer = customer;
        this.attemptNumber = attemptNumber;
        this.status = KycStatus.IN_PROGRESS;
        this.currentStep = KycDraftStep.CONFIRM_INFORMATION;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        startedAt = now;
        lastSavedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isEditable() {
        return status == KycStatus.IN_PROGRESS
                || status == KycStatus.RESUBMISSION_REQUIRED;
    }

    public void updateDraft(
            KycDraftStep currentStep,
            KycDocumentType documentType
    ) {
        if (!isEditable()) {
            throw new IllegalStateException(
                    "The KYC application is not editable."
            );
        }

        if (currentStep == null) {
            throw new IllegalArgumentException(
                    "A KYC draft step is required."
            );
        }

        this.currentStep = currentStep;
        if (documentType != null) {
            this.documentType = documentType;
        }
        lastSavedAt = Instant.now();
    }

    public void submit(Instant submittedAt) {
        if (!isEditable()) {
            throw new IllegalStateException(
                    "The KYC application cannot be submitted."
            );
        }

        if (status == KycStatus.RESUBMISSION_REQUIRED) {
            // A new staff member must explicitly claim the resubmitted case.
            assignedReviewer = null;
            rejectionReason = null;
            decidedAt = null;
            reviewedAt = null;
            reviewStartedAt = null;
        }
        status = KycStatus.SUBMITTED;
        if (this.submittedAt == null) {
            this.submittedAt = submittedAt;
        }
        lastSavedAt = submittedAt;
    }

    public void recordEvidenceChange(Instant savedAt) {
        if (!isEditable()) {
            throw new IllegalStateException(
                    "The KYC application is not editable."
            );
        }

        lastSavedAt = savedAt;
    }

    public void captureProfileSnapshot(Customer customer) {
        profileFirstName = customer.getFirstName();
        profileLastName = customer.getLastName();
        profilePreferredLocale = customer.getPreferredLocale();
        profileDateOfBirth = customer.getDateOfBirth();
        profileNationality = customer.getNationality();
        profileGender = customer.getGender();
        profileAddressLine = customer.getAddressLine();
        profileCity = customer.getCity();
        profileProvince = customer.getProvince();
    }

    public void claim(StaffUser reviewer, Instant reviewStartedAt) {
        if (status != KycStatus.SUBMITTED || assignedReviewer != null) {
            throw new IllegalStateException(
                    "Only an unassigned submitted KYC application can be claimed."
            );
        }

        assignedReviewer = reviewer;
        status = KycStatus.UNDER_REVIEW;
        this.reviewStartedAt = reviewStartedAt;
    }

    public void release(Instant releasedAt) {
        requireAssignedUnderReview();
        assignedReviewer = null;
        status = KycStatus.SUBMITTED;
        lastSavedAt = releasedAt;
    }

    public void approve(Instant decidedAt) {
        requireAssignedUnderReview();
        status = KycStatus.APPROVED;
        this.decidedAt = decidedAt;
        reviewedAt = decidedAt;
        lastSavedAt = decidedAt;
    }

    public void requestResubmission(
            String customerMessage,
            Instant decidedAt
    ) {
        requireAssignedUnderReview();
        status = KycStatus.RESUBMISSION_REQUIRED;
        rejectionReason = customerMessage;
        this.decidedAt = decidedAt;
        reviewedAt = decidedAt;
        lastSavedAt = decidedAt;
    }

    public void reject(String customerMessage, Instant decidedAt) {
        requireAssignedUnderReview();
        status = KycStatus.REJECTED;
        rejectionReason = customerMessage;
        this.decidedAt = decidedAt;
        reviewedAt = decidedAt;
        lastSavedAt = decidedAt;
    }

    private void requireAssignedUnderReview() {
        if (status != KycStatus.UNDER_REVIEW || assignedReviewer == null) {
            throw new IllegalStateException(
                    "This KYC application is not under assigned review."
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public KycStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewStartedAt() {
        return reviewStartedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public StaffUser getAssignedReviewer() {
        return assignedReviewer;
    }

    public boolean isAssignedTo(UUID staffUserId) {
        return assignedReviewer != null
                && assignedReviewer.getId().equals(staffUserId);
    }

    public KycDraftStep getCurrentStep() {
        return currentStep;
    }

    public KycDocumentType getDocumentType() {
        return documentType;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastSavedAt() {
        return lastSavedAt;
    }

    public long getVersion() {
        return version;
    }

    public String getProfileFirstName() {
        return profileFirstName;
    }

    public String getProfileLastName() {
        return profileLastName;
    }

    public String getProfilePreferredLocale() {
        return profilePreferredLocale;
    }

    public LocalDate getProfileDateOfBirth() {
        return profileDateOfBirth;
    }

    public String getProfileNationality() {
        return profileNationality;
    }

    public com.mavela.backend.customer.Gender getProfileGender() {
        return profileGender;
    }

    public String getProfileAddressLine() {
        return profileAddressLine;
    }

    public String getProfileCity() {
        return profileCity;
    }

    public String getProfileProvince() {
        return profileProvince;
    }

    public boolean hasCompleteProfileSnapshot() {
        return profileFirstName != null
                && profileLastName != null
                && profilePreferredLocale != null
                && profileDateOfBirth != null
                && profileNationality != null
                && profileGender != null
                && profileAddressLine != null
                && profileCity != null
                && profileProvince != null;
    }

    public List<KycDocument> getDocuments() {
        return Collections.unmodifiableList(documents);
    }
}
