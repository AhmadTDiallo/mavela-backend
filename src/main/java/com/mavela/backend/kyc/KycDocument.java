package com.mavela.backend.kyc;

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

import java.time.Instant;
import java.util.UUID;

/**
 * Private object-storage metadata only. Evidence bytes never enter JPA or the
 * relational database.
 */
@Entity
@Table(name = "kyc_documents")
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kyc_application_id", nullable = false)
    private KycApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 24)
    private KycEvidenceType evidenceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 24)
    private KycDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_side", nullable = false, length = 24)
    private KycDocumentSide documentSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_method", nullable = false, length = 24)
    private KycCaptureMethod captureMethod;

    @Column(name = "storage_key", nullable = false, unique = true, length = 512)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "sha256_checksum", nullable = false, length = 64)
    private String sha256Checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 24)
    private KycEvidenceUploadStatus uploadStatus = KycEvidenceUploadStatus.REQUESTED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected KycDocument() {
        // Required by JPA.
    }

    public KycDocument(
            KycApplication application,
            KycEvidenceType evidenceType,
            KycDocumentType documentType,
            KycDocumentSide documentSide,
            KycCaptureMethod captureMethod,
            String storageKey,
            String mimeType,
            long fileSize,
            String sha256Checksum
    ) {
        this.application = application;
        this.evidenceType = evidenceType;
        this.documentType = documentType;
        this.documentSide = documentSide;
        this.captureMethod = captureMethod;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.sha256Checksum = sha256Checksum;
        this.uploadStatus = KycEvidenceUploadStatus.REQUESTED;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public void markUploadVerified(Instant uploadedAt) {
        if (uploadStatus != KycEvidenceUploadStatus.REQUESTED) {
            throw new IllegalStateException("Evidence upload is not pending.");
        }

        uploadStatus = KycEvidenceUploadStatus.VALIDATED;
        this.uploadedAt = uploadedAt;
    }

    public void markUploadFailed() {
        if (uploadStatus == KycEvidenceUploadStatus.REQUESTED) {
            uploadStatus = KycEvidenceUploadStatus.FAILED;
        }
    }

    public void markRemoved(Instant deletedAt) {
        if (this.deletedAt == null) {
            this.deletedAt = deletedAt;
        }
        uploadStatus = KycEvidenceUploadStatus.REMOVED;
    }

    public boolean isActive() {
        return deletedAt == null && uploadStatus != KycEvidenceUploadStatus.REMOVED;
    }

    public UUID getId() { return id; }
    public KycApplication getApplication() { return application; }
    public KycEvidenceType getEvidenceType() { return evidenceType; }
    public KycDocumentType getDocumentType() { return documentType; }
    public KycDocumentSide getDocumentSide() { return documentSide; }
    public KycCaptureMethod getCaptureMethod() { return captureMethod; }
    public String getStorageKey() { return storageKey; }
    public String getMimeType() { return mimeType; }
    public long getFileSize() { return fileSize; }
    public String getSha256Checksum() { return sha256Checksum; }
    public KycEvidenceUploadStatus getUploadStatus() { return uploadStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
