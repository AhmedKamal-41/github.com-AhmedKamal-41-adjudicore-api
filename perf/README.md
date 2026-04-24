# Performance Tests

k6 load tests asserting performance SLOs on the AdjudiCore API.

## SLOs

| Scenario | VUs | Duration | p95 budget | Error budget |
|---|---|---|---|---|
| `01-submit-only` | 50 | 30 s | < 500 ms (p99 < 1 s) | < 1% |
| `02-full-lifecycle` | ramp to 20 | 32 s | aggregate < 1000 ms; submit < 500, validate < 600, adjudicate < 1200 | < 1% |
| `03-duplicate-detection` | 30 | 20 s | < 900 ms | < 1% |

**Why scenario 2 uses a ramp** — starting 20 VUs at t=0 against a cold JVM
produces artificial p95 spikes unrelated to steady-state behavior. A 5 s
ramp to 20 VUs, 25 s steady, 2 s ramp-down produces budgets that reflect
what production traffic looks like.

**Why scenario 2 aggregates to 1000 ms and adjudicate to 1200 ms** —
adjudicate runs four rules serially, including a linear scan of
`memberHistory`. The observed p95 under 20 concurrent VUs is ~620 ms, so
a 1200 ms budget gives ~2× headroom for CI-runner variance.

**Why scenario 3 gets 900 ms** — every VU hammers the same
`(memberId, providerNpi, serviceDate, procedureCode)` tuple, so
`DuplicateClaimRule`'s `List<Claim>` scan grows linearly with
`memberHistory` *during* the run itself. Tighter than 900 ms produces
flakes on CI runners.

## Running locally

Prerequisites: k6 installed (`brew install k6` on macOS; Linux instructions
at https://k6.io/docs/get-started/installation/).

```bash
# Start Postgres + the app
docker compose up -d
mvn spring-boot:run
# In another terminal:
./perf/run-all.sh
```

Reports land in `target/k6-reports/` (per-scenario `-summary.json` and
`-raw.json`).

## Interpretation

k6 prints a threshold summary at the end of each run. Red crosses mean SLO
breach and fail the CI job; green checks mean the SLO held. Investigate
before relaxing any threshold — laptop-local runs against a local Postgres
container should pass each scenario with meaningful headroom.
