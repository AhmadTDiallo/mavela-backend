# Mavela Gems and daily streaks

Mavela Gems are server-authoritative, non-financial loyalty units. Flutter
never calculates an award, eligibility, balance, or streak. The only flow is:

```text
Flutter -> authenticated Mavela backend -> PostgreSQL rewards ledger/streak state -> Flutter
```

## Customer API

All endpoints require the existing customer bearer token. The customer is read
only from its JWT subject; no endpoint accepts a customer ID.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/rewards/daily-check-in` | Complete today's check-in. |
| `GET` | `/api/v1/rewards/summary` | Available Gems, current streak, today's state, next milestone, and five recent entries. |
| `GET` | `/api/v1/rewards/activity?page=0&size=20` | Capped customer ledger activity (maximum page size: 50). |

The service determines the current business date using `Africa/Kinshasa`.
Every new day normally awards 5 Gems. Consecutive check-ins reach milestones at
day 7 (+50), day 14 (+100), and day 30 (+300). Missing more than one business
day resets the streak to day 1.

## Idempotency and integrity

`customer_reward_streaks` has exactly one row per customer and is locked while
a daily check-in is processed. `gems_ledger_entries` is append-only and uses a
partial unique PostgreSQL index to allow only one `DAILY_CHECK_IN` per customer
and business date. A retry on the same day returns the original result without
another ledger movement. Milestones are appended in the same transaction and
are consequently awarded once for that streak day.

Available balance is derived from signed `AVAILABLE` ledger entries. Future
deductions or corrections must append `REDEMPTION`, `REVERSAL`, `EXPIRATION`, or
`MANUAL_ADJUSTMENT` entries; they must not change or delete old ledger rows.
Entry type, status, business date, safe reference, optional internal
idempotency key, and a server-owned actor type/reference provide the foundation
for future audited staff adjustments. Those audit fields are not returned by
customer APIs and no staff adjustment endpoint exists yet.

## Deliberately out of scope

There is no QSwitch dependency, financial-account feature, scheduled award job,
admin rewards endpoint, transfer, conversion, redemption flow, or client-side
award calculation. Future reward triggers and staff adjustments must be added
as explicit server-side commands with their own authorization and audit rules.
