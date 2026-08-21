package com.mavela.backend.qswitch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Deterministic local-only fixture. It contains no customer or provider data
 * and is selected only when QSwitch mock mode is explicitly enabled.
 */
public class MockQSwitchAccountProvider implements ExternalAccountProvider {

    private static final ExternalAccount CDF_ACCOUNT = new ExternalAccount(
            new ExternalAccountReference("local-cdf-account"),
            ExternalAccountCurrency.CDF,
            "Local CDF account"
    );
    private static final ExternalAccount USD_ACCOUNT = new ExternalAccount(
            new ExternalAccountReference("local-usd-account"),
            ExternalAccountCurrency.USD,
            "Local USD account"
    );
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public List<ExternalAccount> listAccounts(ExternalCustomerReference customer) {
        return List.of(CDF_ACCOUNT, USD_ACCOUNT);
    }

    @Override
    public ExternalAccountBalance retrieveBalance(
            ExternalCustomerReference customer,
            ExternalAccountReference account
    ) {
        if (CDF_ACCOUNT.reference().equals(account)) {
            return new ExternalAccountBalance(CDF_ACCOUNT, new BigDecimal("125000.00"), OBSERVED_AT);
        }
        if (USD_ACCOUNT.reference().equals(account)) {
            return new ExternalAccountBalance(USD_ACCOUNT, new BigDecimal("250.00"), OBSERVED_AT);
        }
        throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Override
    public ExternalTransactionHistory retrieveTransactionHistory(
            ExternalCustomerReference customer,
            ExternalAccountReference account,
            int limit
    ) {
        var selectedAccount = retrieveBalance(customer, account).account();
        var entries = List.of(
                new ExternalTransaction(
                        "local-credit-001",
                        Instant.parse("2025-12-30T09:00:00Z"),
                        ExternalTransactionDirection.CREDIT,
                        new BigDecimal("50.00"),
                        selectedAccount.currency(),
                        "Mock account funding"
                ),
                new ExternalTransaction(
                        "local-debit-001",
                        Instant.parse("2025-12-29T15:30:00Z"),
                        ExternalTransactionDirection.DEBIT,
                        new BigDecimal("12.50"),
                        selectedAccount.currency(),
                        "Mock balance adjustment"
                )
        );
        int boundedLimit = Math.max(0, Math.min(limit, entries.size()));
        return new ExternalTransactionHistory(entries.stream().limit(boundedLimit).toList(), boundedLimit < entries.size());
    }
}
