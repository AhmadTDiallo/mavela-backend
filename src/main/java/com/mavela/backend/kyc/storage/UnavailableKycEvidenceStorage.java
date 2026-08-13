package com.mavela.backend.kyc.storage;

/**
 * Safe default when private storage has not been configured. It prevents an
 * upload rather than emulating a successful production storage operation.
 */
final class UnavailableKycEvidenceStorage implements KycEvidenceStorage {

    @Override
    public UploadSession requestUpload(UploadRequest request) {
        throw new KycEvidenceStorageException();
    }

    @Override
    public VerifiedObject verifyUpload(VerificationRequest request) {
        throw new KycEvidenceStorageException();
    }

    @Override
    public EvidenceStream openRead(ReadRequest request) {
        throw new KycEvidenceStorageException();
    }

    @Override
    public void delete(String storageKey) {
        throw new KycEvidenceStorageException();
    }
}
