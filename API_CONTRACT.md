# ChipIn API Contract

This document describes the ChipIn backend API after the Phase 1–3 refactor. The JWT verification runs in an ingress sidecar (out of scope here); inside the app every `/api/**` endpoint expects a `Bearer` token whose subject identifies the current user. Swagger UI: `http://localhost:8080/swagger-ui/index.html`.

Conventions:
- All money is `BigDecimal`. Server enforces `> 0` for amounts.
- All UUIDs are RFC 4122. ISO 4217 codes use 3 uppercase letters.
- All timestamps are ISO 8601 (UTC by default).
- Endpoints respond with `application/json`.
- Validation failures return `400` with a JSON error body.
- Authorization failures return `403`; resource-not-found returns `404`.
- Endpoints that scope by group return `404` to non-members to avoid leaking existence.

---

## 1. Currency Resolution Model

Every expense lives in a "bucket" — a `GroupCurrency` row. A bucket has:

- `originCurrency` — the currency the bucket is *denominated* in (e.g. `JPY` for a `YEN-Day1` bucket; the base bucket has `originCurrency == masterCurrency == groupDefault`).
- `masterCurrency` — the true ISO currency the bucket converts to.
- `exchangeRate` — 1 unit of the bucket = `exchangeRate` units of `masterCurrency`.

The resolver converts amounts through a three-hop chain:

```
amount(bucket) --bucket.exchangeRate--> masterCurrency
              --FX row (origin=master, master=groupDefault)--> groupDefaultCurrency
              --FX row (origin=groupDefault, master=userDefault)--> userDefaultCurrency
```

FX rows are also `GroupCurrency` rows but with `originCurrency != masterCurrency`. A daily job is expected to UPSERT these via `PUT /api/groups/{groupId}/fx-rates`. The same UPSERT is exposed to admins so they can override a rate manually.

When any hop fails (no rate), responses surface the missing pair in a `missingRates: ["JPY->INR", ...]` array and leave the unresolved totals as `null`. Raw per-currency totals are always returned regardless.

Three views are exposed on every aggregate response:
- `rawByCurrency` — totals per ISO master currency (no conversion).
- `totalInGroupDefault` / `currencyCode` — aggregated in the group's default currency.
- `totalInUserDefault` / `userDefaultCurrencyCode` — same, but in the viewer's default currency.

---

## 1.1 Idempotent transactions

State-changing money endpoints (settlements and expenses) **require** a client-generated `Idempotency-Key` HTTP header so retries — network blips, double-taps, app crashes mid-POST — do not create duplicate ledger entries.

| Aspect | Detail |
|--------|--------|
| Header | `Idempotency-Key: <token>` |
| Token format | 8–128 chars matching `[A-Za-z0-9_.-]`. UUIDv4 is the recommended generator. |
| Scope | `(user_id, idempotency_key)` is unique. The endpoint path is also folded into the request hash; reusing one key across endpoints fails with 422. |
| TTL | 24 hours from first successful use. After TTL, the key may be reused. |
| Required on | `POST /api/settlements`, `POST /api/groups/{groupId}/expenses` |

Semantics on retry:

| Scenario | Behaviour |
|----------|-----------|
| First call | Action runs, response cached, returned to client. |
| Retry with same key + same body | Cached response is returned **without** re-executing the action. |
| Retry with same key + different body | `422 Idempotency-Key reused with a different request payload`. |
| Two concurrent requests with same key | One wins on the unique constraint and commits; the loser's transaction (action included) is rolled back and returns `409 Concurrent request with the same Idempotency-Key; please retry`. Client's next retry hits the cached response. |
| Missing/malformed header | `400 Idempotency-Key must be 8-128 characters of [A-Za-z0-9_.-]`. |
| Wrapped action throws | Nothing is cached. Transaction rolls back. Client can retry the same key safely. |

Example settlement retry sequence:
```
POST /api/settlements
Idempotency-Key: 0c3b9c5a-1f2d-4e9b-8f1a-1c7b8e8a0a01
{ "groupId": "...", "payerId": "...", "payeeId": "...", "amount": "250.00", "currencyId": "...", "notes": "Paid via UPI" }

→ 200 OK
{ "settlementId": "f0c6...", "message": "Settlement created successfully", "status": "SUCCESS" }

# network glitch — client retries
POST /api/settlements
Idempotency-Key: 0c3b9c5a-1f2d-4e9b-8f1a-1c7b8e8a0a01
{ ...same body... }

→ 200 OK (replayed, no new ledger entry written)
{ "settlementId": "f0c6...", "message": "Settlement created successfully", "status": "SUCCESS" }
```

---

## 2. Authentication (`/auth`)

### 2.1 Signup — `POST /auth/signup`
Body:
```json
{ "name": "John Doe", "email": "john@example.com", "password": "securepw1", "phone": "+1 555 123 4567" }
```
Returns `LoginResponse` (no password). `email` must be valid, `password` 8–128 chars, `phone` matches `^[+0-9 ()-]{7,32}$`.

### 2.2 Login — `POST /auth/login`
Body:
```json
{ "email": "john@example.com", "password": "securepw1" }
```
Returns:
```json
{ "token": "...", "userId": "...", "name": "John", "email": "john@example.com" }
```
Failures return generic `Invalid credentials`.

### 2.3 Logout — `POST /auth/logout`
No body. Acts on the authenticated principal (rotates their `tokenVersion`). Replaces the previous endpoint that took `email` in a body and let anyone log out anyone.

---

## 3. User Profile (`/api/users`)

| Endpoint | Description |
|----------|-------------|
| `GET /api/users/me` | Authenticated user's profile. |
| `PUT /api/users/me` | Update name, phone, profilePicUrl. Body fields are optional; validation applied when present. |
| `GET /api/users/me/default-currency` | Viewer's default currency. |
| `POST /api/users/me/disable` | Self-suspend. Replaces the previous `POST /api/users/disable?email=` which let anyone suspend anyone. Admin moderation is out of scope today. |
| `POST /api/users/me/enable` | Re-enable self. |
| `GET /api/users/friends` | Users who share a group with the viewer. |
| `GET /api/users/search?query=foo` | Returns `List<FriendResponse>` (never the user entity / password hash). |

---

## 4. Global Currencies (`/api/currencies`)

| Endpoint | Description |
|----------|-------------|
| `GET /api/currencies?groupId={uuid}` | List active globals (and legacy group-scoped rows, if any). |
| `GET /api/currencies/{id}` | One currency. |
| `POST /api/currencies` | Create a global currency. `group` must be null — group-scoped Currency rows are no longer supported; use group currencies (Section 6) instead. |
| `DELETE /api/currencies/{id}` | Soft-delete a global currency. |

---

## 5. Groups (`/api/groups`)

| Endpoint | Description |
|----------|-------------|
| `POST /api/groups` | Create a group; creator is auto-admin. Validates default currency. |
| `GET /api/groups/me` | Groups the viewer belongs to. |
| `POST /api/groups/{groupId}/members` | **Admin-only.** Add an existing user. Validated `AddMemberRequest`. |
| `GET /api/groups/{groupId}/dashboard` | **Member-only.** See Section 7. |
| `GET /api/groups/{groupId}/balances` | **Member-only.** See Section 7. |
| `GET /api/groups/users/{groupId}` | **Member-only.** Members of the group. |
| `DELETE /api/groups/{groupId}?hardDelete=true|false` | **Admin-only.** Soft-delete requires fully settled balances. `hardDelete=true` marks all expenses deleted and removes the group. |

---

## 6. Group Currencies (`/api/groups/{groupId}/currencies`)

### 6.1 List — `GET /api/groups/{groupId}/currencies`
Member-only. Returns a list of `GroupCurrencyResponse`:
```json
{
  "groupCurrencyId": "...",
  "groupId": "...",
  "name": "YEN-Day1",
  "masterCurrencyId": "...", "masterCurrencyCode": "JPY",
  "originCurrencyId": null, "originCurrencyCode": null,
  "exchangeRate": "0.74",
  "kind": "BUCKET",            // BUCKET = expense bucket, FX_RATE = resolver-only row
  "createdAt": "2026-05-16T01:00:00",
  "createdByUserId": "..."
}
```

### 6.2 Create custom bucket — `POST /api/groups/{groupId}/currencies`
Admin-only.
```json
{ "name": "YEN-Day1", "masterCurrencyId": "<JPY-uuid>", "exchangeRate": "0.74" }
```
- `name` 1–80 chars, unique within the group.
- `exchangeRate > 0`, at most 13 integer + 6 fractional digits.
- `masterCurrencyId` must be an active global currency.

### 6.3 Update bucket — `PUT /api/groups/{groupId}/currencies/{groupCurrencyId}`
Admin-only. Body fields optional:
```json
{ "name": "YEN-Day1 (updated)", "exchangeRate": "0.78" }
```
Cannot change the master currency (would corrupt historical expenses).

### 6.4 Delete bucket — `DELETE /api/groups/{groupId}/currencies/{groupCurrencyId}`
Admin-only, soft-delete. Rejected if the bucket is referenced by an expense or is the base bucket.

### 6.5 Upsert FX rate — `PUT /api/groups/{groupId}/fx-rates`
Admin-only. Idempotent (no duplicate rows). Used by daily-refresh job and manual admin overrides.
```json
{ "fromCurrencyId": "<JPY-uuid>", "toCurrencyId": "<INR-uuid>", "rate": "0.56" }
```

---

## 7. Dashboard & Balances

### 7.1 `GET /api/groups/{groupId}/dashboard`
Member-only. Returns `GroupDashboardResponse`:
```json
{
  "groupId": "...", "groupName": "Goa Trip",
  "targetCurrencyId": "<INR-uuid>",
  "currencyCode": "INR",
  "userDefaultCurrencyCode": "INR",
  "missingRates": [],
  "userBalances": [
    {
      "userId": "...", "userName": "Alice",
      "netBalance": "250.00",
      "netBalanceInUserDefault": "250.00",
      "rawByCurrency": {"INR": "250.00"}
    }
  ],
  "expenses": [
    {
      "expenseId": "...", "description": "Dinner", "date": "...",
      "category": "FOOD", "type": "EXPENSE", "createdByName": "Alice",
      "yourNetShare": "125.00", "formattedShare": "You lent",
      "bucketName": "Base INR", "masterCurrencyCode": "INR"
    }
  ],
  "settlements": [
    {"payerId": "...", "payerName": "Bob", "payeeId": "...", "payeeName": "Alice", "amount": "100.00"}
  ]
}
```

### 7.2 `GET /api/groups/{groupId}/balances`
Member-only. Returns `GroupBalancesResponse`. Each entry in `userBalances` and `transactionHistory` carries:
- `netBalance` in the group's default currency,
- `netBalanceInUserDefault` (null if FX missing),
- `rawByCurrency` map per ISO code.

The top-level `rawByCurrency` is the viewer's net by ISO currency, and `missingRates` lists any unresolved FX pairs.

---

## 8. Expenses (`/api/groups/{groupId}/expenses`)

### 8.1 `POST /api/groups/{groupId}/expenses`
Member-only. Body:
```json
{
  "description": "Dinner",
  "amount": "500.00",
  "currencyId": "<bucket-or-global-uuid>",
  "category": "FOOD",
  "splitType": "EQUAL",                  // EQUAL | EXACT | PERCENTAGE | SHARES
  "receiptImgUrl": null,
  "payers": [{"userId": "...", "paidAmount": "500.00"}],
  "splits": [
    {"userId": "...", "amountOwed": null, "rawValue": null},
    {"userId": "...", "amountOwed": null, "rawValue": null}
  ]
}
```

Headers:
- `Idempotency-Key: <token>` (required — see §1.1).

Server-side rules (P1 fix):
- All `payers` and `splits` users must be members of the group.
- `sum(payers.paidAmount) == amount` (±0.01 tolerance).
- For `EQUAL`: `amountOwed` is recomputed from `amount / N`, last person absorbs rounding.
- For `PERCENTAGE`: `sum(rawValue) == 100` (±0.01); `amountOwed` is recomputed.
- For `SHARES`: `sum(rawValue) > 0`; `amountOwed` is recomputed pro-rata.
- For `EXACT`: caller's `amountOwed` is preserved; `sum(splits) == amount` required.
- `currencyId` may be either a `GroupCurrency` UUID *in this group* or a global `Currency` UUID (server auto-creates a base bucket for it).

Returns the new expense UUID as a string.

### 8.2 `GET /api/groups/{groupId}/expenses/{expenseId}`
Member-only. Returns `ExpenseDetailsResponse` with bucket name, bucket rate, master currency code, and amount projected into the group's default currency.

---

## 9. Settlements (`/api/settlements`)

### 9.1 `POST /api/settlements`
Headers:
- `Idempotency-Key: <token>` (required — see §1.1).

Validations:
- `payerId != payeeId`.
- Both users are members of the group.
- The authenticated user is either `payerId` or a group admin (P1 fix).
- `amount > 0`.

Body:
```json
{
  "groupId": "...",
  "payerId": "...",
  "payeeId": "...",
  "amount": "250.00",
  "currencyId": "<bucket-or-global-uuid>",
  "notes": "Paid via UPI"
}
```

Returns:
```json
{ "settlementId": "...", "message": "Settlement created successfully", "status": "SUCCESS" }
```

---

## 10. Invitations (`/api/invitations`)

### 10.1 `POST /api/invitations/invite`
Authenticated. If `groupId` is provided, the caller must be an admin of that group (P1 fix).
```json
{ "email": "newuser@example.com", "name": "New User", "groupId": null }
```

### 10.2 `POST /api/invitations/register`
Token-bearing — does not require an existing session.
```json
{ "token": "<invitation-token>", "password": "newpassword1" }
```

---

## 11. Home View (`/api/home`)

### 11.1 `GET /api/home/groups?displayCurrencyId={uuid}`
Aggregates net balances across all the viewer's groups.
- `displayCurrencyId` defaults to viewer default → falls back to `INR`.
- Returns `HomeGroupsResponse` with `totalOwedToYou`, `totalYouOwe`, `rawByCurrency`, `missingRates`, and a per-group `GroupSummaryDto` carrying both `netBalanceInGroupDefault` and `netBalanceInDisplayCurrency`.

### 11.2 `GET /api/home/friends?displayCurrencyId={uuid}`
Same shape but pivoted on friends. Each friend entry exposes `rawByCurrency`.

---

## 12. Schema changes (run once)

```sql
ALTER TABLE chip_in_core.group_currencies
    ADD COLUMN IF NOT EXISTS origin_currency_id UUID
        REFERENCES chip_in_core.currencies(currency_id);

ALTER TABLE chip_in_core.group_currencies
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE chip_in_core.group_currencies
SET origin_currency_id = master_currency_id
WHERE origin_currency_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_group_currencies_group_origin_master
    ON chip_in_core.group_currencies (groupid, origin_currency_id, master_currency_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_group_currencies_group_origin_master
    ON chip_in_core.group_currencies (groupid, origin_currency_id, master_currency_id)
    WHERE origin_currency_id IS NOT NULL AND is_active = TRUE;

-- Idempotency cache for retried POSTs (§1.1)
CREATE TABLE IF NOT EXISTS chip_in_core.idempotency_keys (
    id                UUID PRIMARY KEY,
    user_id           UUID         NOT NULL,
    idempotency_key   VARCHAR(128) NOT NULL,
    endpoint          VARCHAR(256) NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    response_status   INT          NOT NULL,
    response_body     TEXT,
    response_type     VARCHAR(256),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at
    ON chip_in_core.idempotency_keys (expires_at);
```

> A periodic job to delete rows older than `expires_at` is not yet implemented;
> Hibernate `ddl-auto` will create the table on next startup, but the index/
> constraint statements above are safe to apply manually too. Until a cleanup
> job exists, expired rows are also pruned lazily on the next request that
> references the same `(user_id, key)`.

