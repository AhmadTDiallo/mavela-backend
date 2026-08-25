# Mavela staff Gems management API

## Scope and permissions

This is a staff-only, non-monetary rewards operations API. It is served only
under `/api/v1/admin/rewards` by the existing separate Cognito security chain.
Every request needs a valid staff access token, a matching active
`staff_users` allowlist record, and the relevant server-side permission.

| Cognito group | Granted permissions |
| --- | --- |
| `REWARDS_MANAGER` | `rewards:read`, `rewards:adjust` |
| `KYC_REVIEWER` | No rewards permissions |
| `KYC_SUPERVISOR` | No rewards permissions unless also in `REWARDS_MANAGER` |
| `PLATFORM_ADMIN` | No rewards permissions unless also in `REWARDS_MANAGER` |

Provision staff in Cognito first, then insert their stable Cognito `sub` into
`staff_users` with `ACTIVE` status using the existing controlled staff
provisioning procedure. No account is created automatically when a user signs
in.

## API

All route identifiers are opaque public UUIDs; database primary keys are not
returned by the API.

| Method | Route | Permission | Purpose |
| --- | --- | --- | --- |
| GET | `/api/v1/admin/rewards/customers?query=&page=0&size=25` | `rewards:read` | Capped lookup by username, name, or phone; returns masked triage data. |
| GET | `/api/v1/admin/rewards/customers/{customerId}/summary` | `rewards:read` | Safe Gems balance, streak dates, recent activity, and eligibility. |
| GET | `/api/v1/admin/rewards/customers/{customerId}/activity?page=0&size=25` | `rewards:read` | Capped immutable activity page. |
| GET | `/api/v1/admin/rewards/customers/{customerId}/audit?page=0&size=25` | `rewards:read` | Safe, capped staff adjustment/reversal audit history. |
| GET | `/api/v1/admin/rewards/customers/{customerId}/audit?page=0&size=25` | `rewards:read` | Capped safe staff adjustment/reversal audit history. |
| POST | `/api/v1/admin/rewards/customers/{customerId}/adjustments` | `rewards:adjust` | Add one immutable signed `MANUAL_ADJUSTMENT`. |
| POST | `/api/v1/admin/rewards/customers/{customerId}/activity/{activityId}/reversals` | `rewards:adjust` | Add one compensating immutable `REVERSAL`. |

The adjustment body requires a signed non-zero integer amount, a controlled
reason, a 1–500-character internal note, and a validated idempotency key.
The reversal body requires a controlled reason, note, and idempotency key.

## Immutable ledger and audit rules

`gems_ledger_entries` remains the only balance source of truth. Staff commands
append an `AVAILABLE` row and calculate the returned balance from the ledger;
they never change a prior entry or a stored balance. Reversals are allowed only
for eligible available award/adjustment entries and use the exact opposite
amount. PostgreSQL blocks more than one reversal for an original entry.

`rewards_admin_audit_events` is append-only application-managed audit data. It
records the staff allowlist user, customer, action, controlled reason, internal
note, amount, idempotency key, original entry where applicable, and time.
Internal notes and idempotency keys are never mapped into customer rewards
responses.

The caller-supplied idempotency key is unique per staff member, customer, and
action. The ledger receives a server namespaced key too, providing an
additional database guard. Duplicate or racing requests return a stable
conflict instead of crediting/debiting Gems twice.

## Operational limits

Development defaults are intentionally conservative:

* maximum absolute adjustment: `10,000` Gems;
* lookup minimum: two characters;
* maximum page size: 50 (hard bounded to 100 by configuration validation).

Deployment environment variables:

```text
MAVELA_ADMIN_REWARDS_MAX_ADJUSTMENT_ABSOLUTE_AMOUNT=10000
MAVELA_ADMIN_REWARDS_MAX_PAGE_SIZE=50
MAVELA_ADMIN_REWARDS_MINIMUM_QUERY_LENGTH=2
```

Gems are rewards, not cash. These operations do not alter money accounts,
cards, QSwitch records, or customer funds.
