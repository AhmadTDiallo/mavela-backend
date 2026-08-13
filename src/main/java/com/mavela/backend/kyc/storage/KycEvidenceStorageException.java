package com.mavela.backend.kyc.storage;

/**
 * Deliberately opaque storage failure. Provider details and object paths must
 * not be exposed in an API response or application log.
 */
public class KycEvidenceStorageException extends RuntimeException {

    public KycEvidenceStorageException() {
        super("KYC evidence storage operation failed.");
    }
}
