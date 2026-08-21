package com.mavela.backend.qswitch;

import java.util.List;

/**
 * Placeholder for a typed live QSwitch read adapter. It intentionally makes no
 * HTTP calls until QSwitch confirms its authoritative account, balance, and
 * transaction-history endpoint/request/response contract for Mavela UAT.
 */
final class QSwitchAccountProviderAdapter implements ExternalAccountProvider {

    @Override
    public List<ExternalAccount> listAccounts(ExternalCustomerReference customer) {
        throw unavailable();
    }

    @Override
    public ExternalAccountBalance retrieveBalance(
            ExternalCustomerReference customer,
            ExternalAccountReference account
    ) {
        throw unavailable();
    }

    @Override
    public ExternalTransactionHistory retrieveTransactionHistory(
            ExternalCustomerReference customer,
            ExternalAccountReference account,
            int limit
    ) {
        throw unavailable();
    }

    private QSwitchIntegrationException unavailable() {
        return new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE);
    }
}
