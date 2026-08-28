package com.mavela.backend.services.orchestration;

import com.mavela.backend.services.catalogue.ServiceCategory;

/**
 * Future command boundary for service purchases. Before an implementation is
 * introduced it must enforce KYC and eligibility, funding availability,
 * server-side identifier and amount validation, idempotency, a persisted
 * order state machine, provider confirmation or polling, reconciliation,
 * safe receipts, and applicable partner/compliance approvals.
 */
public interface ServiceOrderOrchestrator {

    boolean supports(ServiceCategory category);
}
