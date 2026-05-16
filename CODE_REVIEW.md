# ChipIn — Code Review

Generated 2026-05-16. This is a full backend audit of the `chip-in-service` Spring Boot app, organised by priority. Items marked **Skipped (sidecar)** are not addressed inside the app because a JWT sidecar (ingress) will handle them.

Legend: P1 = must-fix-now, P2 = important improvement, P3 = cleanup / nice-to-have.

---

## Currency feature gap analysis (drives Phase 2 + 3 work)

| Area | Status today | What's missing |
|------|-------------|----------------|
| Bucket → master conversion | Working: `expense.amount * GroupCurrency.exchangeRate` is applied in `GroupService.getGroupDashboard`, `calculateGroupBalances`, `getGroupBalances`, and `HomeService` for the master leg. | Nothing on the first hop. |
| Cross-fiat (true → group default) | **Not implemented.** `HomeService` lines 80-88 and 169-172 contain `TODO: Cross currency conversion` and pass through 1:1. | A persisted FX rate (`group_currencies` row with `origin != master`) and a resolver service. |
| User-default conversion | **Not implemented.** Responses label everything with `group.defaultCurrency`. | Conversion + new DTO fields. |
| Raw-by-currency breakdown | **Not exposed.** Only a single `currencyCode` at the group/home level. `GroupBalancesResponse.TransactionDto` does carry per-row `currencyCode` (master), but no aggregation. | Map `<currencyCode, BigDecimal>` per balance / per group / at the top level. |
| Custom group currencies (e.g. `YEN-Day1`) | Partially modelled: `GroupCurrency.name + masterCurrency + exchangeRate`. Endpoint exists as `POST /api/groups/{groupId}/currencies/{currencyId}` with **query params**. | JSON body, list / update / delete, admin authz, soft-delete, plus the `origin_currency_id` column to flag rate-only rows. |

---

## P1 — must fix now

### P1.1 IDOR / missing group-scoping across dashboard, balances, expenses, settlements, currencies
**Files / lines:**
- `controller/GroupController.java:116` (`getGroupDashboard` — no membership check)
- `controller/GroupController.java:128` (`getGroupsDataByUserId` — fetches any user's groups)
- `controller/GroupController.java:142` (`addGroupCurrency` — no admin check)
- `controller/ExpenseController.java:35` (`getExpense` — no group/membership check)
- `services/ExpenseService.java:78` (`createExpense` — does not verify caller is in group; payers/splits can reference non-members)
- `services/SettlementService.java:64` (`createSettlement` — does not verify caller / payer / payee belong to group)

**Risk:** Any authenticated user can read or mutate financial data in groups they don't belong to. (OWASP A01.)

**Fix:** Introduce an `AccessGuard` service exposing `requireGroupMember(groupId, user)` and `requireGroupAdmin(groupId, user)` and call from every relevant handler/service.

### P1.2 Broken-auth on lifecycle endpoints
**Files:**
- `controller/AuthController.java:34` — `/auth/logout` reads `email` from body and bumps `tokenVersion` for that user.
- `controller/UserController.java:46-56` — `/api/users/disable` and `/api/users/enable` take `email` query param and suspend / re-enable any user.

**Risk:** Authenticated user (or even unauthenticated for `/auth/**` which is `permitAll`) can log out / suspend / re-enable any other user.

**Fix:** Logout uses the authenticated principal. Disable/enable act on the current principal only (self-disable) — admin-driven user moderation belongs in a separate admin endpoint that doesn't exist yet.

### P1.3 Invitation flow has no admin check
**File:** `services/impl/InvitationServiceImpl.java:78-99` — `inviteUser` with a `groupId` adds the invited user as a member without verifying the inviter is an admin of that group.

**Risk:** Any authenticated user can add arbitrary invitees into other groups.

**Fix:** Require `currentUser` is a member-admin of `inviteRequest.getGroupId()` before saving the `GroupMember`.

### P1.4 Settlement actor not verified
**File:** `services/SettlementService.java:64-72` — Any caller can record a settlement between any `payerId` / `payeeId` for any group.

**Risk:** Lets people fabricate debt-clearing records on others' behalf.

**Fix:** Require `currentUser.getUserid()` equals `request.getPayerId()` OR `currentUser` is a group admin. Reject otherwise.

### P1.5 `deleteGroup` soft-delete branch is inverted
**File:** `services/GroupService.java:380-391` — When `hasUnsettled` is true the group is soft-deleted; when settled the call throws "There are unsettled expenses".

**Risk:** Opposite of the documented behaviour; data-loss surprise.

**Fix:** Swap conditions: only delete when settled, throw when unsettled, escalate to `hardDelete=true` to override.

### P1.6 Server trusts client-supplied split math
**File:** `services/ExpenseService.java:35-129` — `paidAmount` and `amountOwed` are taken straight from the request without checking `sum(payers) == sum(splits) == amount`, and without enforcing rules per `splitType` (EQUAL / PERCENTAGE / SHARES).

**Risk:** Inconsistent ledger; users can over-allocate / under-allocate; balances drift.

**Fix:** Recompute splits from `splitType` + raw values for EQUAL/PERCENTAGE/SHARES (already passed in `SplitRequest.rawValue`). For EXACT, validate `sum(splits) == amount`. Always validate `sum(payers) == amount`. Reject otherwise.

### P1.7 Bean Validation is not actually enforced
**File:** `pom.xml` declares only `jakarta.validation-api`; there is no Hibernate Validator / `spring-boot-starter-validation` on the classpath. `@Valid` annotations on controllers therefore do nothing.

**Fix:** Add `spring-boot-starter-validation`. Add `@Valid` on every controller. Add missing constraint annotations on `LoginRequest`, `SignupRequest`, `CreateExpenseRequest`, `CreateGroupRequest`, `PayerRequest`, etc.

### P1.8 Credential / token / invite-URL logging
**Files:**
- `services/AuthService.java:29` — `log.info(request.toString())` logs `LoginRequest` which includes `password`.
- `services/ExpenseService.java:38` and `services/GroupService.java:287` log full request / expense graphs that contain financial data.
- `services/impl/InvitationServiceImpl.java:110` — logs the full invitation link (single-use secret).
- `config/JwtAuthenticationFilter.java:52` — logs the first 50 chars of the JWT.

**Risk:** Secrets / sensitive data persisted to logs.

**Fix:** Strip these `log.info`/`log.debug` calls. Replace with sanitised messages (`userId`, `expenseId`, request size, etc.).

### P1.9 `addGroupCurrency` endpoint is poorly designed and unauthorised
**File:** `controller/GroupController.java:142-151` — uses `@RequestParam` for `name` + `exchangeRate` (should be JSON body), `BigDecimal` accepted without range constraints, no admin/member check, and no list/update/delete companion endpoints. Combined with P1.1.

**Fix:** Implemented as part of Phase 3 (custom currencies CRUD).

---

## P1 — explicitly skipped (sidecar handles)

The following items are real findings but are intentionally **not** addressed inside this app because the JWT sidecar / ingress will own them. They are listed for completeness so we don't lose them.

- Hardcoded JWT signing secret in `services/JwtService.java:22`.
- Committed credentials in `src/main/resources/application.properties` (DB password, `jwt.secret`, commented Neon creds).
- `User` entity returning password hash through `/auth/signup`, `/api/users/me`, `/api/users/search` (sandbox-only password, will rotate).
- CORS wide-open (`SecurityConfig`, duplicated by `util/CorsConfig.java`).
- Swagger UI public (`SecurityConfig.requestMatchers` allow-list).
- `chipin.security.enabled=false` global kill switch.
- 7-day JWT access tokens with no refresh.

---

## P2 — important improvements

### P2.1 `Double` used for money in DTO
**File:** `dto/GroupDataByUserResponse.java:13` — `private Double amountOwedByUser;`. All other money fields are `BigDecimal`.

**Fix:** Convert to `BigDecimal`. Same field is `null` in practice because the service stub returns `null`, but the type should still be correct for when it's wired up.

### P2.2 `groupAndTallySplitsForUser` / `getGroupsDataByUserId` are stubs
**File:** `services/GroupService.java:283-316` — `getGroupsDataByUserId` returns `null`; `groupAndTallySplitsForUser` walks expenses without returning anything.

**Fix:** Either remove from controller surface (`GroupController.getGroupsDataByUserId` at line 128) or implement and unit test. Currently the endpoint will NPE on the response wrapper. Recommend removing the endpoint for now and reintroducing after Phase 2.

### P2.3 N+1 and lazy-init risk on read paths
**Files:** `services/GroupService.getGroupDashboard`, `calculateGroupBalances`, `getGroupBalances`, `services/HomeService.*`, `services/ExpenseService.getExpenseDetails`.

These iterate `expense.getPayers()`, `expense.getSplits()`, `expense.getCurrency().getMasterCurrency()` outside of a `@Transactional` scope on read methods. Hibernate's open-session-in-view masks it, but it's still a correctness/perf risk.

**Fix:** Annotate the read service methods with `@Transactional(readOnly = true)`, and add `@EntityGraph` on the queries that load expenses for dashboard / balances.

### P2.4 Greedy settlement suggestions ignore `simplifyDebt`
**Files:** `entities/Group.java:42` declares `simplifyDebt`; `services/GroupService.calculateSettlements:230` always runs the greedy algorithm.

**Fix:** Branch on `group.isSimplifyDebt()`. When false, derive suggestions directly from pairwise balances rather than netting via the greedy matcher.

### P2.5 Pairwise balance math uses `ROUND_HALF_UP` int constant
**File:** `services/GroupService.java:459` — `divide(totalPaid, 2, BigDecimal.ROUND_HALF_UP)` uses the deprecated int constant and rounds to 2 dp inside the loop.

**Fix:** Use `RoundingMode.HALF_EVEN` (banker's rounding) and keep scale 6 internally, rounding only at the DTO boundary.

### P2.6 `findActiveCurrenciesForGroupOrGlobal` accepts `null` groupId
**File:** `repository/CurrencyRepository.java:17` — the JPQL `c.group IS NULL OR c.group.groupId = :groupId` returns globals when `:groupId` is null which is fine, but `CurrencyController.getCurrencies(@RequestParam(required = false) UUID groupId)` propagates a null param into the bind. This works on PostgreSQL but is fragile.

**Fix:** Add an `if (groupId == null)` early return at the service layer to call a dedicated `findActiveGlobalCurrencies()` query.

### P2.7 `Currency.group` (group-scoped global Currency rows) is redundant with `GroupCurrency`
**File:** `entities/Currency.java:48-50` lets you create a `Currency` row that's "scoped to a group" by setting `group`. `GroupCurrency` already models per-group buckets with rates. Two paths for the same concept ⇒ confusion (and `CurrencyService.validateAndGetGroupCurrency` carries branching logic for both).

**Fix:** Drop new creates via `POST /api/currencies` where `group != null` (handled in Phase 3). Eventually drop the column from `Currency` once data is migrated. (No legacy data per user, so we can plan a clean rename in this iteration.)

### P2.8 Settlement / Expense `splitType` is a free-form string
**File:** `entities/Expense.java:50-51` — `splitType` is `String`; `enums/SplitType.java` exists but isn't used.

**Fix:** Map `Expense.splitType` to the enum (already declared in `entities/enums/SplitType.java`).

### P2.9 `getExpensesForUser` then filter is O(N) per group
**File:** `services/HomeService.java:43-45` and similar — fetch all expenses for user, then filter by group inside a loop.

**Fix:** Add `findByGroupGroupIdAndUserAndIsDeletedFalse` or compute home aggregates with a single SQL aggregate query.

---

## P2 — explicitly skipped (sidecar / infra)

- Duplicate CORS configs in `config/SecurityConfig.java` and `util/CorsConfig.java`.
- Swagger UI exposure in prod.
- `chipin.security.enabled` global kill-switch profile separation.
- Flyway / Liquibase migrations — using Hibernate ddl-auto for now.

---

## P3 — cleanup

- `util/JwtUtil.java` is unused dead code (`JwtService` is the live implementation). Delete.
- `util/LocalSecurityConfig.java` is empty with stale imports. Delete.
- `ChipInApplication.java:10` uses `System.out.println("Starting ChipInApplication")`. Use the logger.
- `services/UserService.loadUserByUsername:79-82` returns `null` (silently breaks if Spring picks this bean as `UserDetailsService`). Either implement or remove the `implements UserDetailsService`.
- `services/GroupService.deleteGroup` "hard" branch still calls `group.setDeleted(true)` instead of `groupRepository.delete(group)` — naming is misleading; choose one.
- No pagination on `/api/users/search`, `/api/users/friends`, `/api/groups/{id}/dashboard` (expenses list).
- `dto/CreateExpenseRequest.type` is never read by `ExpenseService` (drops `type`/`category` distinction silently).
- Tests: `GroupServiceTest` only covers `calculateSettlements`; no controller integration tests, no MockMvc, no security tests. Add at least one round-trip test per critical service method.

---

## Implementation phases

### Phase 1 — applied immediately
- AccessGuard + IDOR fixes (P1.1)
- Lifecycle-endpoint hardening (P1.2)
- Invitation admin check (P1.3)
- Settlement actor check (P1.4)
- `deleteGroup` inversion fix (P1.5)
- Server-side split math validation (P1.6)
- `spring-boot-starter-validation` + DTO annotations (P1.7)
- Credential / token / invite-URL log scrubbing (P1.8)
- `Double` → `BigDecimal` (P2.1)
- `groupAndTallySplitsForUser` / `getGroupsDataByUserId` cleanup (P2.2)
- N+1 / `@Transactional(readOnly=true)` on read paths (P2.3)
- `calculateSettlements` honours `simplifyDebt` (P2.4)
- `RoundingMode.HALF_EVEN` (P2.5)
- `Currency` enum mapping (P2.8)
- Dead code cleanup (P3 selected)

### Phase 2 — currency conversion
- Schema: `group_currencies` gains `origin_currency_id UUID NULL` (FK → `currencies.currency_id`) and `is_active BOOLEAN NOT NULL DEFAULT TRUE`. SQL in [Schema migration](#schema-migration) below.
- New `CurrencyResolutionService` exposing `toGroupDefault(expense)`, `toUserDefault(amountInGroupDefault, group, user)`, `aggregateByMasterCurrency(...)`, and `findFxRate(group, from, to)`.
- Refactor `GroupService` + `HomeService` to use the resolver.
- DTO additions (additive; OK because no backward-compat required):
  - `GroupDashboardResponse`, `GroupBalancesResponse`: `totalInGroupDefault`, `totalInUserDefault`, `userDefaultCurrencyCode`, `Map<String,BigDecimal> rawByCurrency`, `List<String> missingRates`. Per-user balance: same triple view.
  - `HomeGroupsResponse`, `HomeFriendsResponse`: `Map<String,BigDecimal> rawByCurrency`, `List<String> missingRates`.
  - `ExpenseDetailsResponse`: `bucketName`, `bucketRate`, `masterCurrencyCode`.

### Phase 3 — custom currency CRUD + FX admin
- Replace query-param endpoint with JSON-body `POST /api/groups/{groupId}/currencies` and add `GET`, `PUT`, `DELETE`. Admin-only via `AccessGuard`.
- New `PUT /api/groups/{groupId}/fx-rates` for the `true → default` rate (used by the future daily job).
- Stop creating new `Currency` rows scoped to a group via `POST /api/currencies` (`Currency.group != null` rejected).
- Update `API_CONTRACT.md`.

### Phase 4 — idempotency for money-moving POSTs
- New `IdempotencyKey` entity / repository / `IdempotencyService` (`Idempotency-Key` header, SHA-256 over `endpoint || '|' || body`, 24 h TTL, unique `(user_id, idempotency_key)` constraint).
- Replays return the original cached response. Mismatched bodies fail 422. Concurrent retries fail 409 on the constraint; the loser's transaction (action included) rolls back so no duplicate ledger entry is ever written.
- Header is **required** on `POST /api/settlements` and `POST /api/groups/{groupId}/expenses`. SQL added below.
- Unit tests in `IdempotencyServiceTest` cover: fresh execute, replay, body-mismatch 422, endpoint-mismatch 422, malformed key 400, concurrent-race 409, expired-row reissue.
- Followup (not yet implemented): scheduled job to vacuum rows where `expires_at < now()`. Currently pruned lazily.

---

## Schema migration

```sql
-- Phase 2: GroupCurrency gains origin_currency_id and is_active
ALTER TABLE chip_in_core.group_currencies
    ADD COLUMN IF NOT EXISTS origin_currency_id UUID
        REFERENCES chip_in_core.currencies(currency_id);

ALTER TABLE chip_in_core.group_currencies
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Backfill existing rows: origin = master (so "1 bucket-unit = rate * master" semantic
-- still holds, and these rows behave exactly as before).
UPDATE chip_in_core.group_currencies
SET origin_currency_id = master_currency_id
WHERE origin_currency_id IS NULL;

-- Helpful index for the resolver's "find FX row" lookup
CREATE INDEX IF NOT EXISTS idx_group_currencies_group_origin_master
    ON chip_in_core.group_currencies (groupid, origin_currency_id, master_currency_id);

-- Stop allowing two FX rows for the same (group, origin -> master) pair
CREATE UNIQUE INDEX IF NOT EXISTS uq_group_currencies_group_origin_master
    ON chip_in_core.group_currencies (groupid, origin_currency_id, master_currency_id)
    WHERE origin_currency_id IS NOT NULL AND is_active = TRUE;

-- Phase 4: Idempotency cache for retried POSTs
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

Run these in your local Postgres (`chip_in_core` schema). Hibernate's `ddl-auto` is commented out in `application.properties`, so the columns / table must be added manually.
