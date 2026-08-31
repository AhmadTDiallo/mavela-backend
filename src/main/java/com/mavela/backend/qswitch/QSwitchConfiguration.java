package com.mavela.backend.qswitch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(QSwitchProperties.class)
public class QSwitchConfiguration {

    @Bean
    Clock qSwitchClock() {
        return Clock.systemUTC();
    }

    @Bean
    QSwitchTokenTransport qSwitchTokenTransport(Clock qSwitchClock) {
        /*
         * The current Web MVC dependency set does not guarantee that a shared
         * ObjectMapper bean exists. This small transport-local mapper keeps
         * disabled/mock local startup independent of a live QSwitch client.
         */
        return new HttpQSwitchTokenTransport(
                new ObjectMapper(),
                qSwitchClock
        );
    }

    @Bean
    QSwitchStagingTokenManager qSwitchStagingTokenManager(
            QSwitchProperties properties,
            QSwitchTokenTransport transport,
            Clock qSwitchClock
    ) {
        return new QSwitchStagingTokenManager(properties, transport, qSwitchClock);
    }

    @Bean
    QSwitchAuthenticatedClient qSwitchAuthenticatedClient(
            QSwitchProperties properties,
            QSwitchStagingTokenManager tokenManager
    ) {
        return new QSwitchAuthenticatedClient(properties, tokenManager);
    }

    @Bean
    QSwitchAuthenticationDiagnostic qSwitchAuthenticationDiagnostic(
            QSwitchStagingTokenManager tokenManager
    ) {
        return new QSwitchAuthenticationDiagnostic(tokenManager);
    }

    @Bean
    QSwitchReadRetryPolicy qSwitchReadRetryPolicy(QSwitchProperties properties) {
        return new QSwitchReadRetryPolicy(properties);
    }

    @Bean
    QSwitchReadExecutor qSwitchReadExecutor(
            QSwitchStagingTokenManager tokenClient,
            QSwitchReadRetryPolicy retryPolicy
    ) {
        return new QSwitchReadExecutor(tokenClient, retryPolicy);
    }

    @Bean
    ExternalAccountProvider externalAccountProvider(QSwitchProperties properties) {
        if (properties.isMockEnabled()) {
            return new MockQSwitchAccountProvider();
        }
        if (properties.isStagingAuthenticationConfigured()) {
            return new QSwitchAccountProviderAdapter();
        }
        return new UnavailableQSwitchAccountProvider();
    }
}
