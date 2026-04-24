# AdjudiCore — Architecture Notes

## 1. Overview

AdjudiCore models a commercial payer claim as a two-phase state machine.
A claim starts at `SUBMITTED` on intake, passes through `VALIDATED` if
pre-flight checks clear, and lands at `APPROVED`, `DENIED`, or stays at
`VALIDATED` with a PEND audit entry. A claim that fails validation goes
straight to `REJECTED` and never reaches adjudication. There are five
terminal states total: `SUBMITTED`, `VALIDATED`, `APPROVED`, `DENIED`,
`REJECTED`. `PENDED` is deliberately *not* a status — it's a VALIDATED
claim with an audit-only marker, so auth-code arrival can re-trigger
adjudication without a state unwind.

## 2. Validation vs adjudication

Validation and adjudication are separate endpoints because they answer
different questions. Validation asks *can this claim be processed at
all?* — is the member eligible, is the provider known, is the date
sane, is the amount positive. Adjudication asks *what is the answer?* —
duplicate detection, prior-auth, coverage limits, allowed-amount
calculation. Collapsing the two into a single endpoint would mean
every adjudication failure has to disambiguate "the claim was bad" from
"the rules say no," which is exactly the split the domain already has.

The phases also have opposite aggregation semantics. Validation runs
every validator and aggregates *all* reject codes (no short-circuit), so
a provider submitting a bad claim sees every issue in one response and
can fix them all at once. Adjudication short-circuits on the first DENY
or PEND because once the claim is denied for duplication, running the
coverage-limit rule is pointless. The analogy: validation is pre-flight
checks (report every issue), adjudication is the flight (the first
engine failure matters).

## 3. Why PEND keeps status as VALIDATED

Two designs were on the table:

- **Introduce a `PENDED` status.** Clean from a state-diagram
  perspective, but requires a transition back to `VALIDATED` once the
  auth code arrives, plus callers have to handle another status value.
- **Keep `VALIDATED`, record PEND in the audit log only.** No state
  unwind — when the auth code arrives, the system just re-adjudicates
  and the rule now passes. Callers keep treating the claim as validated
  and pending processing.

The second option wins because it keeps the state machine simpler and
mirrors how most payers handle prior-auth workflows internally: the
claim isn't in a different *state*, it's in the same state with a flag.

## 4. Audit log design

`claim_audit_log` is append-only with no foreign key to `claims`. Every
state transition writes exactly one row, and we *never* update audit
rows. The missing FK is intentional: audit trails have to survive claim
archival or anonymization (GDPR/HIPAA-style concerns in real
healthcare), and a cascading delete would destroy the historical record.

The one-row-per-transition invariant is verified by
`PersistencePendBehaviorIntegrationTest` using Hibernate Statistics.
It asserts that a PEND decision produces `getEntityInsertCount() == 1`
(the audit row) and `getEntityUpdateCount() == 0` (no mutation to
`claims`). An APPROVE decision asserts `insertCount == 1 &&
updateCount == 1` — the audit row plus the status change on the claim.
This is the only way to catch a regression where someone accidentally
marks the PEND audit as a real state transition.

## 5. BigDecimal and money arithmetic

Every money value uses `BigDecimal` with scale 2 and
`RoundingMode.HALF_UP`. Floating-point arithmetic would compound
representation errors across thousands of claims, and healthcare money
needs to match to the cent. The fee schedule is a `Map.ofEntries(...)`
in `AllowedAmountCalculator`, and the calculator is also used by
`CoverageLimitRule` to project this claim's contribution to the
running-year sum. That sharing prevented a real bug during Step 6:
`CoverageLimitRule` was originally projecting `billedAmount` into the
year sum, which meant a $2000 billed claim on CPT 99213 would
"contribute" $2000 toward the cap even though the claim would only ever
pay out $125. Extracting the calculator and re-using it forced the
projection to match what actually gets paid.

Cap-at-billed is applied last — after the fee schedule lookup and any
out-of-network 0.60× adjustment. Payers never pay more than providers
billed.

## 6. Test strategy rationale

Four test layers target four different quality axes:

- **Unit** — branch coverage and algorithmic correctness, ~2 seconds
  across 157 tests.
- **Integration** — full-stack behavior against a real Postgres in
  Testcontainers, catching migration drift and Spring wiring issues.
- **Contract** — HTTP response shape and error envelopes via REST
  Assured. These are the tests that fire first when a refactor
  accidentally changes a JSON field.
- **Performance** — k6 scenarios with concrete p95 SLOs.

Why mutation testing on top of 98% line coverage: coverage is vanity,
mutation score is reality. 98% line coverage means every line executed;
it does not mean a bug in that line would be caught. PIT confirms the
tests are actually meaningful by breaking production code in small ways
and checking that a test fails. See
[MUTATION_REPORT.md](MUTATION_REPORT.md) for per-mutant analysis — the
baseline turned up 14 surviving mutants, all of which were real
null-branch gaps.

Why H2 for `@DataJpaTest` slice tests but Testcontainers for end-to-end
integration: H2 is ~5× faster per test and catches migration issues
(PostgreSQL-dialect Flyway migrations still need to parse on H2),
whereas Testcontainers catches Postgres-specific behavior and FK
semantics that H2 fakes. Both layers pull their weight.

Why k6 runs only on main pushes and not every PR: k6 takes 2+ minutes
and needs a fresh Postgres per run. Running it on every PR would be
wasteful and noisy; running it on main keeps a public dashboard of
observed p95s without slowing PR feedback.

## 7. Known sharp edges

- **Unbounded `memberHistory` query.** `findAllByMemberId` loads every
  prior claim, which degrades linearly. Documented in the README
  roadmap; the k6 duplicate-stress scenario surfaces the effect when
  claims accumulate between runs.
- **Testcontainers singleton pattern.** `AbstractIntegrationTest`
  deliberately does *not* use `@Container`/`@Testcontainers`. JUnit's
  lifecycle would stop the static Postgres container between test
  classes, but the next class's Spring context still holds a Hikari
  pool pointing at the torn-down port. We use a static initializer +
  Ryuk reaper at JVM exit instead.
- **springdoc-openapi first-hit cost.** `GET /v3/api-docs` takes ~2s on
  the first call due to lazy spec generation, which trips the global
  2s contract-test response-time budget. `ContractOpenApiTest` has a
  `@BeforeEach` warmup that primes the endpoint before the timed
  assertions.
- **`CoverageLimitRule` uses fee-schedule projection, not billed
  amount.** A $2000 billed claim on CPT 99213 contributes only $125
  toward the annual cap. Surprising on first reading; documented in the
  rule's test file to keep future contributors from "fixing" it.
