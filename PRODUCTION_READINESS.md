# ChipIn — Production Readiness Checklist

_Last updated: 2026-05-16. This is the working list to take ChipIn from "feature complete in dev" to "safe to point a real user at." Items are grouped by blocker / important / polish, with concrete acceptance criteria so we can tick them off._

The application logic itself (groups, expenses, custom currencies, FX, settlements, idempotency, multi-view currency aggregation) is in a good place. What remains is everything around the code: secrets, runtime, observability, schema lifecycle, and the operational edges.

---

## 0. Status snapshot

| Layer | State |
|------|-------|
| Domain logic | ✅ Phase 1–4 complete — IDOR, split math, auth, idempotency, currency resolution chain, custom currency CRUD all land. See `CODE_REVIEW.md`. |
| API contract | ✅ Documented in `API_CONTRACT.md`, Swagger UI live at `/swagger-ui/index.html`. |
| Unit tests | ✅ 23 unit tests including the new GlobalExceptionHandler contract. Controller/integration tests still pending. |
| Build & image | ✅ Multi-stage Dockerfile (non-root, healthcheck, drops Maven from runtime); GitHub Actions CI runs `mvn verify` + image build. |
| Secrets / config | ✅ `application.properties` is env-driven only; `application-local.properties` is gitignored; `application-prod.properties` exists with no secrets. JWT secret rotation still pending (auth scope). |
| Schema lifecycle | ✅ Flyway baseline (`V1__baseline_schema.sql`) plus `V2__group_currencies_origin_and_active.sql` and `V3__idempotency_keys.sql`. `baseline-on-migrate=true`. |
| Observability | ✅ Logback JSON encoder under `prod` profile, Prometheus actuator endpoint, traceId on every error response. OpenTelemetry agent still pending. |
| Deploy target | ✅ Real ChipIn K8s manifests under `deploy/k8s/` (namespace + configmap + secret template + deployment + service + networkpolicy). |
| Auth boundary | 🟡 JWT verification will move to a sidecar/ingress — design agreed, not yet wired. **(Excluded from current scope per user request.)** |

Legend: 🔴 launch blocker · 🟡 needed before real users · 🟢 polish.

### Pending (not addressed in this pass — all JWT/auth-flow scoped)

These were deferred at the user's request — they will be handled when the sidecar work lands.

- **1.1 / 1.2 / 1.3** — Rotate the JWT secret, delete the static `JwtService.SECRET_KEY`, hand JWT verification off to the sidecar.
- **1.6 (auth half)** — Delete the `chipin.security.enabled=false` kill switch once the sidecar is the authn boundary. The SQL/TRACE logging half of this is done (prod profile is quiet).
- **2.5** — Rate limiting on `/auth/**`. Lives in the sidecar; revisit if the sidecar's RL module isn't enough.
- **2.8 (auth half)** — Gate `/actuator/**` (other than health/info/prometheus) and Swagger behind the sidecar. Locally we already only expose health/info/prometheus and `springdoc.*.enabled` defaults can be flipped via env.
- **3, "AuthException"** — Dedicated 401 mapping in the exception handler.

---

## 1. 🔴 Launch blockers

These ship-stoppers must be done before exposing the service to anyone outside the local machine.

### 1.1 Rotate every committed secret and move them to env / KMS
- `src/main/resources/application.properties` has a **live** Neon connection string with a real-looking password (`npg_58Zb…`), a JWT HMAC secret in hex, and a 30-day token lifetime — all in git history (`git log -p src/main/resources/application.properties`).
- `src/main/java/com/chipIn/ChipIn/services/JwtService.java:22` has a **second** hardcoded HMAC secret as a `private static final String` that overrides the property entirely.
- Acceptance:
  - Rotate the Neon password; expire the old one.
  - Generate a new HMAC secret with `openssl rand -base64 64`. Inject via `JWT_SECRET` env var.
  - Read every secret through `@Value("${…:#{null}}")` and fail fast on null in `@PostConstruct`.
  - Run `git filter-repo` (or BFG) on the local repo before pushing it anywhere public; force-push the rewritten history.
  - Add `application-*.properties` to `.gitignore` and document `application-local.properties` as the only file allowed to hold local creds.
- Suggested file layout:
  ```
  src/main/resources/
    application.properties         # safe defaults + ${ENV} placeholders only
    application-prod.properties    # prod profile, no secrets
  ```

### 1.2 Delete the static `JwtService.SECRET_KEY`
- Right now `JwtService` ignores `jwt.secret` from properties and uses a baked-in constant. Even after 1.1 this code path remains.
- Acceptance:
  - `JwtService` reads the key with `@Value("${jwt.secret}")`, decodes once in a `@PostConstruct` that validates ≥256 bits, stores the resulting `SecretKey` in a final field.
  - Fails startup if `jwt.secret` is missing or < 32 bytes.

### 1.3 Hand off JWT to the sidecar (or wire access-token / refresh-token rotation)
- Today: 30-day (`jwt.expiration=2592000000`) opaque-ish JWTs signed with HS256, no refresh, no revocation other than `tokenVersion`.
- Plan of record (from `CODE_REVIEW.md`): sidecar verifies JWT, forwards a trusted `X-User-Id` header.
- Acceptance:
  - Decide: (a) keep app-issued JWTs and add a refresh endpoint, or (b) delete `JwtService` / `JwtAuthenticationFilter` and trust the sidecar's `X-User-Id`. Path (b) is what's been planned.
  - If (b): a new `SidecarAuthenticationFilter` reads `X-User-Id`, fetches the user, and rejects requests where the header is missing on `/api/**`. The sidecar must strip the header on inbound traffic so clients can't spoof it.
  - Either way: access tokens ≤ 15 min, refresh tokens 7 days with rotation + reuse detection.

### 1.4 Replace `GlobalExceptionHandler` with a non-leaking handler
- `config/GlobalExceptionHandler.java:28-37` catches `RuntimeException` and returns the raw `ex.getMessage()` — that string can contain JDBC stack messages, NPE descriptions, or anything else. `ResponseStatusExceptionResolver` in the framework still wins for `ResponseStatusException`, but the safety net is wrong.
- Acceptance:
  - Map `MethodArgumentNotValidException`, `ConstraintViolationException`, `HttpMessageNotReadableException`, `AccessDeniedException`, `AuthenticationException`, and `ResponseStatusException` to their proper statuses with a stable error body (`code`, `message`, `traceId`).
  - `RuntimeException` and `Exception` fallthroughs return a generic `500 Internal Server Error` body with **no exception detail**; log the full stack with a `traceId` field.
  - Add a unit test asserting `RuntimeException("oops")` returns a body without `"oops"`.

### 1.5 Replace the open CORS policy
- `SecurityConfig.corsConfigurationSource()` sets `setAllowedOrigins(List.of("*"))`. There is also a duplicate `util/CorsConfig.java`.
- Acceptance:
  - Single source of truth — delete `util/CorsConfig.java`.
  - Read the allow-list from `chipin.cors.allowed-origins` (comma-separated). Default to `[]` so dev fails loudly until configured.
  - `setAllowCredentials(true)` only paired with concrete origins (never `*`).
  - Add a `/actuator/info` smoke test that fails when the property is missing.

### 1.6 Remove dev-only kill switches before promoting to staging
- `chipin.security.enabled=true` is on, but the `false` branch in `SecurityConfig` permits everything — leaving it in production is a single config flip away from total bypass.
- Acceptance:
  - Either gate the `false` branch behind `spring.profiles.active=local` only, or delete it. Recommendation: delete.
  - Remove `logging.level.org.hibernate.SQL=DEBUG` and `org.hibernate.type.descriptor.sql=TRACE` from prod profile — they print raw SQL with bind values.

### 1.7 Apply the SQL migrations once via a real tool
- We currently rely on Hibernate `ddl-auto` (commented in `application.properties`) plus hand-pasted SQL from `API_CONTRACT.md` §12. That cannot survive a production rollout.
- Acceptance:
  - Add Flyway (`spring-boot-starter-flyway`). Baseline = current production schema.
  - Convert the SQL blocks from `CODE_REVIEW.md` and `API_CONTRACT.md` into versioned migrations:
    - `V1__baseline.sql`
    - `V2__group_currency_origin_and_active.sql`
    - `V3__idempotency_keys.sql`
  - Set `spring.flyway.baseline-on-migrate=true` for the existing Neon DB; future deploys add forward-only `V4__…sql` files.
  - Delete the Hibernate `ddl-auto` line for prod profiles.

### 1.8 Replace stub `EmailService` before any real invite goes out
- `services/impl/ResendEmailServiceImpl.java` just `log.info("MOCK EMAIL SENT …, link={}")`. That means: (a) no one gets the email, and (b) the link, which contains the invite token, is written to the application log.
- Acceptance:
  - Wire Resend's HTTP API (or SES, Postmark — whichever you'll actually use). API key via env var.
  - Stop logging the link entirely. Log `(toEmail, recipientName, traceId)` only.
  - Add a `MockEmailService` activated by `chipin.email.provider=mock` for local dev; default in prod is `resend`.

---

## 2. 🟡 Needed before real users

Not launch blockers, but you'll regret them within the first week.

### 2.1 Stop returning the `User` entity from `/auth/signup` and `/api/users/me`
- `entities/User.java` is a JPA entity, `implements UserDetails`, has `@Data` (so `toString()` includes the password hash) and `@ToString` (same). Several controllers still return `User` directly which round-trips the bcrypt hash through Jackson.
- Acceptance:
  - Introduce `UserResponse` (no password, no `tokenVersion`, no audit timestamps unless asked) and convert all controller returns.
  - Mark `password` `@JsonIgnore` and exclude from Lombok `@ToString` (`@ToString.Exclude`) as a belt-and-braces.
  - Same audit on `LoginResponse` (no token leaks in logs).

### 2.2 Add an idempotency cleanup job
- `idempotency_keys` rows are lazily pruned only when the same `(user_id, key)` is reused. Over time the table grows.
- Acceptance:
  - `@Scheduled(cron = "0 */15 * * * *")` job runs `IdempotencyKeyRepository.deleteAllExpired(now)` every 15 min.
  - Wrap in `@SchedulerLock` (ShedLock) so it runs in a single instance even when scaled out.

### 2.3 Daily FX refresh job
- Custom currencies + FX rates are CRUD-able but nothing populates them. Plan of record: a job UPSERTs `(origin=ISO, master=group.default)` rows daily.
- Acceptance:
  - One scheduled job per group, or one global job that fans out — start simple, global.
  - Source: ECB feed for fiat, configurable adapter for crypto if needed later.
  - Idempotent — re-running the job for the same date overwrites the same row.
  - Surface failures in `/actuator/health` (custom indicator).

### 2.4 Observability triad
- We log via SLF4J in plain text. No metrics, no traces, no JSON logs.
- Acceptance:
  - Add `logback-spring.xml` with a JSON encoder (`logstash-logback-encoder`). One log line per request with `traceId`, `userId`, `path`, `status`, `duration_ms`.
  - Expose Prometheus metrics via `spring-boot-starter-actuator` + `micrometer-registry-prometheus`. At minimum: HTTP latency, GC, datasource pool gauges, idempotency hit/miss counter, FX-missing counter.
  - OpenTelemetry agent for distributed tracing — sample 10%, propagate `traceparent` upstream from the JWT sidecar.
  - Alert routes: 5xx > 1% for 5m, p95 > 1s for 10m, DB pool exhaustion, idempotency-table size > 100k.

### 2.5 Rate limiting on auth and money endpoints
- Nothing throttles `/auth/login`, `/auth/signup`, or `POST /api/settlements`. A single client can brute-force credentials or hammer settlements.
- Acceptance:
  - Use `bucket4j-spring-boot-starter` or the sidecar's rate-limit module (if the planned sidecar covers this — confirm before duplicating).
  - Per-IP + per-account counters for `/auth/login` with progressive backoff.
  - Per-user `POST /api/settlements` and `/api/groups/*/expenses` ceiling, e.g. 60/min.

### 2.6 Switch read paths to `@Transactional(readOnly = true)` and `@EntityGraph`
- `GroupService.getGroupDashboard`, `HomeService.*`, `ExpenseService.getExpenseDetails` iterate `expense.getPayers()` / `getSplits()` / `currency.getMasterCurrency()` under open-session-in-view. That's both an N+1 and a correctness hazard when OSIV is disabled later.
- Acceptance:
  - Annotate the read service methods with `@Transactional(readOnly = true)`.
  - Define `@EntityGraph` on `ExpenseRepository.findByGroupGroupId…` to fetch `payers`, `splits`, `currency.masterCurrency`, `currency.originCurrency` in one round trip.
  - Set `spring.jpa.open-in-view=false` and confirm tests still pass.

### 2.7 Pagination on list endpoints
- `/api/users/search`, `/api/users/friends`, the dashboard's expense list, and `/api/groups/{id}/balances` all return unbounded lists.
- Acceptance:
  - Convert to `Page<T>` using `Pageable` with default `size=20`, max `size=100`.
  - Document `?page=` / `?size=` / `?sort=` in `API_CONTRACT.md`.

### 2.8 Hardening: actuator + Swagger lockdown
- `/actuator/**` is on the classpath by default; `/swagger-ui/**` is currently permit-all.
- Acceptance:
  - Only `/actuator/health` and `/actuator/info` exposed publicly. Everything else gated by a `ROLE_OPS` claim or sidecar IP allow-list.
  - Swagger UI behind the sidecar or stripped from the prod image (`-Dspringdoc.swagger-ui.enabled=false`).

### 2.9 CI pipeline
- Today: nothing automated.
- Acceptance:
  - GitHub Actions / GitLab CI: `mvn -B verify` on every push, JUnit report uploaded.
  - Build the Docker image on main and tag with SHA.
  - Lint: `mvn spotbugs:check`, `mvn org.owasp:dependency-check-maven:check`.

### 2.10 Container hygiene
- Current `Dockerfile` runs as root, leaves Maven in the runtime image, has no healthcheck.
- Acceptance:
  - Runtime stage: `RUN addgroup --system app && adduser --system --ingroup app app` and `USER app`.
  - Add `HEALTHCHECK CMD wget -qO- http://localhost:8080/actuator/health | grep UP || exit 1`.
  - Pin base images by digest, not `:21-jre`.
  - `.dockerignore` to keep `target/`, `.git/`, IDE folders, `.mvn/wrapper` out of build context.

---

## 3. 🟢 Polish / nice-to-have

Useful but not on the critical path.

- **Replace `RuntimeException("Invalid credentials")` in `AuthService`** with a dedicated `AuthException` mapped to 401 in the exception handler. Avoids accidental 500 mapping in the future.
- **Pre-commit hook for secret detection** (`gitleaks` or `trufflehog`). Wire into CI too.
- **Optimistic locking smoke test** — `User`, `Expense`, `Group`, `Currency` all carry `@Version` columns but we never assert that conflicts surface as 409. Add a concurrent-update test.
- **Replace `RoundingMode.HALF_UP` left in any remaining code path** with `HALF_EVEN`. `GroupService.java:459` was flagged in `CODE_REVIEW.md` P2.5 — confirm.
- **Tests, tests, tests**:
  - MockMvc / `WebMvcTest` coverage for at least one happy path per controller + one auth-denied case.
  - Concurrent-idempotency integration test (Spring `@DirtiesContext` + threads) that asserts only one settlement is written when two parallel requests share a key.
  - Currency-resolution property test with random buckets + FX rows.
- **Delete the stale doc files** — `VALIDATION_FIX_SUMMARY.md`, `SETTLEMENT_SERVICE_VALIDATION_FIX.md`, `CURRENCY_VALIDATION_CHANGES.md` describe in-flight changes that are now merged. They duplicate `CODE_REVIEW.md`.
- **Replace `nginx-test/`** with the actual ChipIn Deployment + Service + Ingress YAMLs (or Helm chart). The two existing files describe an nginx hello-world.
- **`pytests/` clean-up** — `test_chipin_logic.py` has three real-looking emails and the password `"password"` baked in. Move to fixtures driven by env vars; ensure the accounts exist only in a dedicated test schema.
- **Group hard-delete vs soft-delete** is still confused in `GroupService.deleteGroup`'s "hard" branch — see `CODE_REVIEW.md` P3.
- **`CreateExpenseRequest.type` is never read** by `ExpenseService` — either honour it or remove it.
- **Pagination + filter for `/api/users/search`** at the same time (cf. 2.7).
- **`@JsonIgnore` audit pass** on every entity that's still returned from a controller.
- **Schema-level `idempotency_keys.user_id` foreign key** to `users.userid` once we're sure cascade behaviour is what we want.

---

## 4. Open product / design decisions

These aren't "bugs" but they need a decision before the production cutover.

- **Multi-currency settlement semantics.** Today a settlement is recorded in one currency, like any expense. When the payer/payee debt straddles multiple master currencies (because the underlying expenses do), the user has to pick. Decide whether to: (a) keep this and let the UI do the math, (b) split a settlement into per-master-currency legs, or (c) auto-convert into one preferred currency at the time of payment and store the FX context.
- **Currency rounding at settlement time.** A `JPY → INR → JPY` round trip will not be exact. The current chain uses HALF_EVEN at scale 8 internally and rounds at the DTO. Confirm 2-dp is the right boundary for *every* currency (JPY conventionally uses 0 dp, BHD uses 3 dp).
- **Group archival and data retention.** What happens to a group with `isDeleted=true`? Today expenses and settlements still exist forever. Decide a retention window and write the cleanup job.
- **OAuth providers.** `AuthProvider` enum lists `LOCAL`, presumably you have `GOOGLE`/`APPLE` planned. Decide whether the sidecar terminates OAuth or the app does.
- **Mobile build of trust.** If the client is a mobile app, decide on certificate pinning policy and SDK update channel before launch.

---

## 5. Cutover order (suggested)

1. Section 1 (every bullet) — secrets, exception handler, CORS, migrations, real email. Without these you can't safely point a tester at the service.
2. Section 2.1, 2.2, 2.3, 2.8, 2.9, 2.10 — the things that turn a tested build into a deployable one.
3. Open a thin private beta. Watch logs, metrics, idempotency hit-rate.
4. Section 2.4–2.7 + Section 3 in priority order as feedback comes in.

When you can tick every 🔴 box and the 🟡 list is at least 70 % done, you're ready for a private beta.
