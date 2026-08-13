package com.mavela.backend.kyc.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "mavela.kyc.storage")
public class KycEvidenceStorageProperties {

    private boolean enabled;
    private String bucket;
    private String region = "af-south-1";
    private URI endpoint;
    /**
     * Optional client-reachable endpoint used only when generating a
     * pre-signed URL. This is useful for local Android development where the
     * backend reaches MinIO through 127.0.0.1 but the emulator reaches the
     * host through 10.0.2.2. It must point at the same private bucket.
     */
    private URI presignEndpoint;
    private String accessKeyId;
    private String secretAccessKey;
    private boolean forcePathStyle;
    private boolean allowInsecureEndpoint;
    private Duration uploadUrlTtl = Duration.ofMinutes(10);
    private long maxImageSizeBytes = 10_485_760;
    /**
     * Required for production storage. The explicit NONE value is permitted
     * only for the insecure local-development endpoint when that MinIO setup
     * has no KMS configured.
     */
    private String serverSideEncryption = "AES256";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public URI getEndpoint() { return endpoint; }
    public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
    public URI getPresignEndpoint() { return presignEndpoint; }
    public void setPresignEndpoint(URI presignEndpoint) { this.presignEndpoint = presignEndpoint; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }
    public boolean isForcePathStyle() { return forcePathStyle; }
    public void setForcePathStyle(boolean forcePathStyle) { this.forcePathStyle = forcePathStyle; }
    public boolean isAllowInsecureEndpoint() { return allowInsecureEndpoint; }
    public void setAllowInsecureEndpoint(boolean allowInsecureEndpoint) { this.allowInsecureEndpoint = allowInsecureEndpoint; }
    public Duration getUploadUrlTtl() { return uploadUrlTtl; }
    public void setUploadUrlTtl(Duration uploadUrlTtl) { this.uploadUrlTtl = uploadUrlTtl; }
    public long getMaxImageSizeBytes() { return maxImageSizeBytes; }
    public void setMaxImageSizeBytes(long maxImageSizeBytes) { this.maxImageSizeBytes = maxImageSizeBytes; }
    public String getServerSideEncryption() { return serverSideEncryption; }
    public void setServerSideEncryption(String serverSideEncryption) { this.serverSideEncryption = serverSideEncryption; }
}
