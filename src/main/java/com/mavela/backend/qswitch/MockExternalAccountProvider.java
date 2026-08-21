package com.mavela.backend.qswitch;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Deterministic development and test provider. It is enabled only when the
 * QSwitch integration itself is explicitly enabled in MOCK mode.
 */
/**
 * @deprecated Superseded by {@link MockQSwitchAccountProvider}. Retained as a
 * source-compatible local fixture while the QSwitch boundary is introduced.
 */
@Deprecated
final class MockExternalAccountProvider {

    private static final ExternalAccount CDF_ACCOUNT = new ExternalAccount(
            new ExternalAccountReference("mock-cdf-account"),
            ExternalAccountCurrency.CDF,
            "Mavela test CDF"
    );
    private static final ExternalAccount USD_ACCOUNT = new ExternalAccount(
            new ExternalAccountReference("mock-usd-account"),
            ExternalAccountCurrency.USD,
            "Mavela test USD"
    );

    public List<ExternalAccount> listAccounts(
            ExternalCustomerReference customer
    ) {
        return List.of(CDF_ACCOUNT, USD_ACCOUNT);
    }

    public ExternalAccountBalance retrieveBalance(
            ExternalCustomerReference customer,
            ExternalAccountReference account
    ) {
        ExternalAccount selected = findAccount(account);
        BigDecimal amount = selected.currency() == ExternalAccountCurrency.CDF
                ? new BigDecimal("250000.00")
                : new BigDecimal("540.75");

        return new ExternalAccountBalance(selected, amount, Instant.EPOCH);
    }

    public ExternalTransactionHistory retrieveTransactionHistory(
            ExternalCustomerReference customer,
            ExternalAccountReference account,
            int limit
    ) {
        ExternalAccount selected = findAccount(account);
        int cappedLimit = Math.max(0, Math.min(limit, 50));
        List<ExternalTransaction> transactions = List.of(
                new ExternalTransaction(
                        "mock-credit-001",
                        Instant.EPOCH.plus(2, ChronoUnit.DAYS),
                        ExternalTransactionDirection.CREDIT,
                        selected.currency() == ExternalAccountCurrency.CDF
                                ? new BigDecimal("12500.00")
                                : new BigDecimal("25.00"),
                        selected.currency(),
                        "Synthetic test credit"
                ),
                new ExternalTransaction(
                        "mock-debit-001",
                        Instant.EPOCH.plus(1, ChronoUnit.DAYS),
                        ExternalTransactionDirection.DEBIT,
                        selected.currency() == ExternalAccountCurrency.CDF
                                ? new BigDecimal("3200.00")
                                : new BigDecimal("6.50"),
                        selected.currency(),
                        "Synthetic test debit"
                )
        );

        return new ExternalTransactionHistory(
                transactions.stream().limit(cappedLimit).toList(),
                cappedLimit < transactions.size()
        );
    }

    private ExternalAccount findAccount(ExternalAccountReference reference) {
        return List.of(CDF_ACCOUNT, USD_ACCOUNT)
                .stream()
                .filter(account -> account.reference().equals(reference))
                .findFirst()
                .orElseThrow(() -> new QSwitchIntegrationException(
                        QSwitchIntegrationErrorCode.ACCOUNT_NOT_FOUND
                ));
    }
}
