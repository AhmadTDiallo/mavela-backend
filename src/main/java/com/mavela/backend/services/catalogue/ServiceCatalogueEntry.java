package com.mavela.backend.services.catalogue;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Operator-managed product metadata. This entity deliberately has no command
 * methods: customer APIs may only read it and cannot enable a provider.
 */
@Entity
@Table(name = "service_catalogue_entries")
public class ServiceCatalogueEntry {

    @Id
    @Column(name = "service_code", nullable = false, updatable = false)
    private String serviceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ServiceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ServiceAvailability availability;

    @Column(name = "provider_display_name", length = 120)
    private String providerDisplayName;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "customer_visible", nullable = false)
    private boolean customerVisible;

    @Column(name = "minimum_amount", precision = 19, scale = 4)
    private BigDecimal minimumAmount;

    @Column(name = "maximum_amount", precision = 19, scale = 4)
    private BigDecimal maximumAmount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "service_catalogue_input_requirements",
            joinColumns = @JoinColumn(name = "service_code")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "input_requirement", nullable = false, length = 32)
    @OrderColumn(name = "display_order")
    private List<ServiceInputRequirement> inputRequirements = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "service_catalogue_supported_currencies",
            joinColumns = @JoinColumn(name = "service_code")
    )
    @Column(name = "currency_code", nullable = false, length = 3)
    private List<String> supportedCurrencies = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ServiceCatalogueEntry() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public ServiceCategory getCategory() {
        return category;
    }

    public ServiceAvailability getAvailability() {
        return availability;
    }

    public String getProviderDisplayName() {
        return providerDisplayName;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public boolean isCustomerVisible() {
        return customerVisible;
    }

    public BigDecimal getMinimumAmount() {
        return minimumAmount;
    }

    public BigDecimal getMaximumAmount() {
        return maximumAmount;
    }

    public List<ServiceInputRequirement> getInputRequirements() {
        return List.copyOf(inputRequirements);
    }

    public List<String> getSupportedCurrencies() {
        return List.copyOf(supportedCurrencies);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /**
     * The only mutable catalogue configuration exposed to staff operations.
     * This intentionally cannot configure a provider or make a service live.
     */
    public void updateOperationalConfiguration(
            ServiceAvailability availability,
            boolean customerVisible,
            short displayOrder
    ) {
        if (availability == ServiceAvailability.AVAILABLE) {
            throw new IllegalArgumentException(
                    "A service provider is not configured"
            );
        }
        if (displayOrder <= 0) {
            throw new IllegalArgumentException(
                    "Display order must be positive"
            );
        }

        this.availability = availability;
        this.customerVisible = customerVisible;
        this.displayOrder = displayOrder;
    }
}
