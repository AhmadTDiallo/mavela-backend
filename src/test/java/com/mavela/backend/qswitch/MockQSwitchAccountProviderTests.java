package com.mavela.backend.qswitch;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockQSwitchAccountProviderTests {

    private final MockQSwitchAccountProvider provider = new MockQSwitchAccountProvider();

    @Test
    void exposesDeterministicTypedCdfAndUsdAccounts() {
        var accounts = provider.listAccounts(
                new ExternalCustomerReference("local-test-customer")
        );

        assertThat(accounts)
                .extracting(ExternalAccount::currency)
                .containsExactlyInAnyOrder(
                        ExternalAccountCurrency.CDF,
                        ExternalAccountCurrency.USD
                );
        assertThat(accounts)
                .allSatisfy(account -> assertThat(account.displayName()).startsWith("Local"));
    }

    @Test
    void returnsAStableBalanceAndBoundedHistory() {
        var customer = new ExternalCustomerReference("local-test-customer");
        var cdf = provider.listAccounts(customer)
                .stream()
                .filter(account -> account.currency() == ExternalAccountCurrency.CDF)
                .findFirst()
                .orElseThrow();

        var balance = provider.retrieveBalance(customer, cdf.reference());
        var history = provider.retrieveTransactionHistory(
                customer,
                cdf.reference(),
                1
        );

        assertThat(balance.account().currency())
                .isEqualTo(ExternalAccountCurrency.CDF);
        assertThat(balance.availableAmount()).isPositive();
        assertThat(history.transactions()).hasSize(1);
        assertThat(history.transactions()).allSatisfy(entry -> {
            assertThat(entry.currency()).isEqualTo(ExternalAccountCurrency.CDF);
            assertThat(Set.of(
                    ExternalTransactionDirection.CREDIT,
                    ExternalTransactionDirection.DEBIT
            )).contains(entry.direction());
        });
    }

    @Test
    void mapsAnUnknownAccountToTheStableSafeErrorCode() {
        assertThatThrownBy(() -> provider.retrieveBalance(
                new ExternalCustomerReference("local-test-customer"),
                new ExternalAccountReference("unknown-local-account")
        )).isInstanceOfSatisfying(QSwitchIntegrationException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(QSwitchIntegrationErrorCode.ACCOUNT_NOT_FOUND)
        );
    }
}
