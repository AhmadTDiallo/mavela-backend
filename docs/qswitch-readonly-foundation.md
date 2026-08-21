# QSwitch read-only foundation

This foundation establishes one safe integration boundary:

```text
Flutter customer app -> Mavela Spring Boot -> QSwitch OAuth/API -> Mavela Spring Boot -> Flutter customer app
```

Flutter must never call QSwitch directly, and it must never receive a QSwitch
access token. The backend owns credentials, timeout handling, provider error
mapping, and all future customer-to-provider account mapping.

## Current scope

The implementation introduces Mavela-owned typed operations for:

- listing provider accounts;
- retrieving an account balance;
- retrieving transaction history.

There are intentionally **no HTTP endpoints** yet. The current customer model
has no approved QSwitch customer/account mapping, and QSwitch has not supplied
the exact UAT read endpoint family or response contract. Exposing an endpoint
before both are defined would risk an IDOR flaw or an incorrect financial
integration.

The QSwitch live adapter therefore fails closed without making a network call
until the confirmed read contracts are implemented. This foundation creates no
accounts and supports no transfer, debit, credit, reversal, callback, or
webhook operation.

## Local deterministic mock

Mock data is strictly opt-in and has no real customer information. Add these
values to an untracked local `.env` file, then run `./mvnw spring-boot:run`:

```properties
MAVELA_QSWITCH_ENABLED=true
MAVELA_QSWITCH_MODE=MOCK
```

The `ExternalAccountProvider` then returns fixed, synthetic CDF and USD
accounts, balances, and history entries. No mock endpoint is exposed to
customers and the mock is not enabled unless explicitly requested.

## Live OAuth configuration

Set `MAVELA_QSWITCH_MODE=QSWITCH` only after the QSwitch UAT team confirms the
exact values for every OAuth setting in `.env.example`. Live mode requires:

- an HTTPS `MAVELA_QSWITCH_BASE_URL`;
- the confirmed `MAVELA_QSWITCH_TOKEN_PATH`;
- client ID and client secret supplied only through the deployment environment;
- an explicitly confirmed request encoding;
- exact configured names and value for the grant, client ID, client secret,
  optional scope, access-token, and expiry fields.

`UNCONFIRMED` request encoding and incomplete values make the integration
unavailable. Secrets, access tokens, provider response bodies, and complete
customer/provider payloads are never logged or persisted. Access tokens stay
only in process memory and refresh before expiry under a synchronized cache,
which prevents refresh stampedes.

Provider calls use finite connection/response timeouts. The bounded retry
policy is reserved for future idempotent reads and only permits timeouts,
provider-unavailable responses, and rate limiting; it never applies to a
state-changing operation.

## Required QSwitch UAT confirmations before live reads

1. Exact OAuth body encoding, field names, grant value, scopes, response JSON,
   token TTL, authentication error format, and rate-limit headers.
2. Exact account, balance, and transaction-history endpoint paths, methods,
   request fields, pagination, date/time format, currency representation, and
   response schemas.
3. The approved customer-to-QSwitch-customer and Mavela-account-to-QSwitch-
   account mapping/provisioning model, including ownership/authorization checks.
4. CDF/USD account lifecycle and available-versus-ledger balance semantics.
5. Error semantics, retry-after behavior, idempotency expectations, SLA, and
   support/reconciliation procedures.
6. Separate future contracts for transfers, reversals, webhooks, settlement,
   reconciliation, and dispute handling. None are part of this implementation.

Once these are available, implement raw QSwitch DTOs only inside the live
adapter, map them to `ExternalAccountProvider` types, add protected Mavela
customer endpoints after ownership mapping exists, and add contract tests.
