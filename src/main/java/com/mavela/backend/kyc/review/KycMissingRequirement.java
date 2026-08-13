package com.mavela.backend.kyc.review;

/** Customer-facing KYC requirements that can be explicitly requested again. */
public enum KycMissingRequirement {
    PROFILE_INFORMATION,
    DOCUMENT_FRONT,
    DOCUMENT_BACK,
    DOCUMENT_PHOTO_PAGE,
    SELFIE
}
