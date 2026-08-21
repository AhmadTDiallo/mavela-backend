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
    QSwitchOAuthTokenClient qSwitchOAuthTokenClient(
            QSwitchProperties properties,
            QSwitchTokenTransport transport,
            Clock qSwitchClock
    ) {
        return new QSwitchOAuthTokenClient(properties, transport, qSwitchClock);
    }

    @Bean
    QSwitchReadRetryPolicy qSwitchReadRetryPolicy(QSwitchProperties properties) {
        return new QSwitchReadRetryPolicy(properties);
    }

    @Bean
    QSwitchReadExecutor qSwitchReadExecutor(
            QSwitchOAuthTokenClient tokenClient,
            QSwitchReadRetryPolicy retryPolicy
    ) {
        return new QSwitchReadExecutor(tokenClient, retryPolicy);
    }

    @Bean
    ExternalAccountProvider externalAccountProvider(QSwitchProperties properties) {
        if (properties.isMockEnabled()) {
            return new MockQSwitchAccountProvider();
        }
        if (properties.isLiveModeConfigured()) {
            return new QSwitchAccountProviderAdapter();
        }
        return new UnavailableQSwitchAccountProvider();
    }
}
