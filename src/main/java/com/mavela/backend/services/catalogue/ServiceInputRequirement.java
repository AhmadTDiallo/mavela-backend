package com.mavela.backend.services.catalogue;

/** Future customer input shapes. They do not perform provider validation. */
public enum ServiceInputRequirement {
    PHONE_NUMBER,
    METER_NUMBER,
    SMART_CARD_NUMBER,
    CUSTOMER_REFERENCE
}
