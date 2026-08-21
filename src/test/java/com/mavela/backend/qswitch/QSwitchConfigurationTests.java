package com.mavela.backend.qswitch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QSwitchConfigurationTests {

    private final QSwitchConfiguration configuration = new QSwitchConfiguration();

    @Test
    void defaultConfigurationSelectsTheFailClosedProvider() {
        var provider = configuration.externalAccountProvider(new QSwitchProperties());

        assertThatThrownBy(() -> provider.listAccounts(
                new ExternalCustomerReference("local-test-customer")
        ))
                .isInstanceOf(QSwitchIntegrationException.class)
                .satisfies(exception -> assertThat(
                        ((QSwitchIntegrationException) exception).getErrorCode()
                ).isEqualTo(QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE));
    }

    @Test
    void explicitMockModeSelectsOnlyTheSyntheticProvider() {
        var properties = new QSwitchProperties();
        properties.setEnabled(true);
        properties.setMode(QSwitchMode.MOCK);

        var provider = configuration.externalAccountProvider(properties);

        assertThat(provider).isInstanceOf(MockQSwitchAccountProvider.class);
    }
}
