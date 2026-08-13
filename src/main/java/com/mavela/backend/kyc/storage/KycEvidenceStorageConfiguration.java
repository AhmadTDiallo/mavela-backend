package com.mavela.backend.kyc.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KycEvidenceStorageProperties.class)
public class KycEvidenceStorageConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "mavela.kyc.storage",
            name = "enabled",
            havingValue = "true"
    )
    KycEvidenceStorage s3KycEvidenceStorage(
            KycEvidenceStorageProperties properties
    ) {
        return new S3KycEvidenceStorage(properties);
    }

    @Bean
    @ConditionalOnMissingBean(KycEvidenceStorage.class)
    KycEvidenceStorage unavailableKycEvidenceStorage() {
        return new UnavailableKycEvidenceStorage();
    }
}
