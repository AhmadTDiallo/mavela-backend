package com.mavela.backend.admin.services;

import com.mavela.backend.admin.staff.StaffUser;
import com.mavela.backend.services.catalogue.ServiceAvailability;
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
 * Append-only staff audit data. Notes and idempotency material never appear
 * in customer endpoints or the safe admin audit projection.
 */
@Entity
@Immutable
@Table(name = "service_catalogue_admin_audit_events")
public class ServiceCatalogueAdminAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_user_id", nullable = false)
    private StaffUser staffUser;

    @Column(name = "service_code", nullable = false, length = 64,
            updatable = false)
    private String serviceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private ServiceCatalogueAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_availability", nullable = false, length = 16,
            updatable = false)
    private ServiceAvailability previousAvailability;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_availability", nullable = false, length = 16,
            updatable = false)
    private ServiceAvailability newAvailability;

    @Column(name = "previous_customer_visible", nullable = false,
            updatable = false)
    private boolean previousCustomerVisible;

    @Column(name = "new_customer_visible", nullable = false,
            updatable = false)
    private boolean newCustomerVisible;

    @Column(name = "previous_display_order", nullable = false,
            updatable = false)
    private short previousDisplayOrder;

    @Column(name = "new_display_order", nullable = false, updatable = false)
    private short newDisplayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 64,
            updatable = false)
    private ServiceCatalogueChangeReasonCode reasonCode;

    @Column(name = "internal_note", nullable = false, length = 500,
            updatable = false)
    private String internalNote;

    @Column(name = "idempotency_key", nullable = false, length = 160,
            updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64,
            updatable = false)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ServiceCatalogueAdminAuditEvent() {
        // Required by JPA.
    }

    public ServiceCatalogueAdminAuditEvent(
            StaffUser staffUser,
            String serviceCode,
            ServiceCatalogueAuditAction action,
            ServiceConfigurationSnapshot previous,
            ServiceConfigurationSnapshot current,
            ServiceCatalogueChangeReasonCode reasonCode,
            String internalNote,
            String idempotencyKey,
            String requestFingerprint
    ) {
        this.staffUser = staffUser;
        this.serviceCode = serviceCode;
        this.action = action;
        previousAvailability = previous.availability();
        newAvailability = current.availability();
        previousCustomerVisible = previous.customerVisible();
        newCustomerVisible = current.customerVisible();
        previousDisplayOrder = previous.displayOrder();
        newDisplayOrder = current.displayOrder();
        this.reasonCode = reasonCode;
        this.internalNote = internalNote;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public StaffUser getStaffUser() { return staffUser; }
    public String getServiceCode() { return serviceCode; }
    public ServiceCatalogueAuditAction getAction() { return action; }
    public ServiceAvailability getPreviousAvailability() { return previousAvailability; }
    public ServiceAvailability getNewAvailability() { return newAvailability; }
    public boolean isPreviousCustomerVisible() { return previousCustomerVisible; }
    public boolean isNewCustomerVisible() { return newCustomerVisible; }
    public short getPreviousDisplayOrder() { return previousDisplayOrder; }
    public short getNewDisplayOrder() { return newDisplayOrder; }
    public ServiceCatalogueChangeReasonCode getReasonCode() { return reasonCode; }
    public String getInternalNote() { return internalNote; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
}
