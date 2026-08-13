/*
 * V12 was applied to the persistent development database before Hibernate
 * validation exposed PostgreSQL CHAR(64) as bpchar rather than VARCHAR.
 * Keep V12 immutable and preserve every checksum while aligning the column
 * with the JPA String mapping.
 */
ALTER TABLE kyc_documents
    ALTER COLUMN sha256_checksum TYPE VARCHAR(64)
    USING sha256_checksum::VARCHAR(64);
