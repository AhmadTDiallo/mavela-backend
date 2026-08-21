package com.mavela.backend.qswitch;

import java.util.List;

public record ExternalTransactionHistory(
        List<ExternalTransaction> transactions,
        boolean hasMore
) {

    public ExternalTransactionHistory {
        transactions = List.copyOf(transactions);
    }
}
