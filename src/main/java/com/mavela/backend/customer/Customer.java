package com.mavela.backend.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 20)
    private String username;

    @Column(
            name = "phone_number",
            nullable = false,
            unique = true,
            length = 16
    )
    private String phoneNumber;

    @Column(
            unique = true,
            length = 254
    )
    private String email;

    @Column(
            name = "first_name",
            nullable = false,
            length = 100
    )
    private String firstName;

    @Column(
            name = "last_name",
            nullable = false,
            length = 100
    )
    private String lastName;

    @Column(
            name = "preferred_locale",
            nullable = false,
            length = 10
    )
    private String preferredLocale;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 2)
    private String nationality;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private Gender gender;

    @Column(name = "address_line", length = 200)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String province;

    @Column(name = "profile_completed_at")
    private Instant profileCompletedAt;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private CustomerStatus status = CustomerStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "kyc_status",
            nullable = false,
            length = 24
    )
    private KycStatus kycStatus = KycStatus.NOT_STARTED;

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

    protected Customer() {
        // Required by JPA
    }

    public Customer(
            String phoneNumber,
            String email,
            String firstName,
            String lastName,
            String preferredLocale
    ) {
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.preferredLocale = preferredLocale;
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

    public void markPhoneVerified(Instant verifiedAt) {
        if (phoneVerifiedAt == null) {
            phoneVerifiedAt = verifiedAt;
        }
    }

    public void selectUsername(String username) {
        if (status != CustomerStatus.PENDING) {
            throw new IllegalStateException(
                    "Username cannot be changed after account activation."
            );
        }

        this.username = username;
    }

    public void activateAfterPinSetup() {
        if (phoneVerifiedAt == null) {
            throw new IllegalStateException(
                    "The customer phone must be verified before activation."
            );
        }

        if (username == null) {
            throw new IllegalStateException(
                    "The customer must select a username before activation."
            );
        }

        if (status == CustomerStatus.PENDING) {
            status = CustomerStatus.ACTIVE;
        }
    }

    public void updateProfile(
            String firstName,
            String lastName,
            String preferredLocale,
            LocalDate dateOfBirth,
            String nationality,
            Gender gender,
            String addressLine,
            String city,
            String province,
            Instant completedAt
    ) {
        if (firstName != null) {
            this.firstName = firstName;
        }
        if (lastName != null) {
            this.lastName = lastName;
        }
        if (preferredLocale != null) {
            this.preferredLocale = preferredLocale;
        }
        if (dateOfBirth != null) {
            this.dateOfBirth = dateOfBirth;
        }
        if (nationality != null) {
            this.nationality = nationality;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (addressLine != null) {
            this.addressLine = addressLine;
        }
        if (city != null) {
            this.city = city;
        }
        if (province != null) {
            this.province = province;
        }

        if (profileCompletedAt == null && hasCompletedProfile()) {
            profileCompletedAt = completedAt;
        }
    }

    private boolean hasCompletedProfile() {
        return firstName != null
                && lastName != null
                && preferredLocale != null
                && dateOfBirth != null
                && nationality != null
                && gender != null
                && addressLine != null
                && city != null
                && province != null;
    }

    public boolean isPhoneVerified() {
        return phoneVerifiedAt != null;
    }

    public boolean hasUsername() {
        return username != null;
    }

    public boolean isProfileComplete() {
        return profileCompletedAt != null;
    }

    public void startKycApplication() {
        if (kycStatus != KycStatus.NOT_STARTED) {
            throw new IllegalStateException(
                    "KYC can only start when it has not been started."
            );
        }

        kycStatus = KycStatus.IN_PROGRESS;
    }

    public void submitKycApplication() {
        if (kycStatus != KycStatus.IN_PROGRESS
                && kycStatus != KycStatus.RESUBMISSION_REQUIRED) {
            throw new IllegalStateException(
                    "KYC can only be submitted from an editable state."
            );
        }

        kycStatus = KycStatus.SUBMITTED;
    }

    /**
     * Moves a customer-owned KYC lifecycle into a staff review. This is kept
     * here rather than in a controller so the customer and application states
     * cannot drift apart.
     */
    public void beginKycReview() {
        requireKycStatus(KycStatus.SUBMITTED);
        kycStatus = KycStatus.UNDER_REVIEW;
    }

    public void releaseKycReview() {
        requireKycStatus(KycStatus.UNDER_REVIEW);
        kycStatus = KycStatus.SUBMITTED;
    }

    public void approveKycApplication() {
        requireKycStatus(KycStatus.UNDER_REVIEW);
        kycStatus = KycStatus.APPROVED;
    }

    public void requireKycResubmission() {
        requireKycStatus(KycStatus.UNDER_REVIEW);
        kycStatus = KycStatus.RESUBMISSION_REQUIRED;
    }

    public void rejectKycApplication() {
        requireKycStatus(KycStatus.UNDER_REVIEW);
        kycStatus = KycStatus.REJECTED;
    }

    private void requireKycStatus(KycStatus expectedStatus) {
        if (kycStatus != expectedStatus) {
            throw new IllegalStateException(
                    "KYC transition is not allowed from the current state."
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPreferredLocale() {
        return preferredLocale;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getNationality() {
        return nationality;
    }

    public Gender getGender() {
        return gender;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public String getProvince() {
        return province;
    }

    public Instant getProfileCompletedAt() {
        return profileCompletedAt;
    }

    public Instant getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
