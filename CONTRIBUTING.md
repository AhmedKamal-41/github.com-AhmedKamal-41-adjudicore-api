# Contributing to AdjudiCore

Thanks for your interest. This project is primarily a portfolio piece,
but feedback, bug reports, and pull requests are welcome.

## Branch naming

- `feat/description` — new feature
- `fix/description` — bug fix
- `test/description` — test-only changes
- `docs/description` — documentation only
- `refactor/description` — no behavior change
- `ci/description` — build or pipeline changes

## Commit messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add PriorAuthRule short-circuit on unknown CPT
fix: correct BigDecimal scale in CoverageLimitRule projection
test: cover boundary at exactly 365 days stale
docs: clarify PEND status semantics
```

The `pr-quality.yml` workflow enforces this on PR titles.

## Tests

Every change must maintain:
- 85% JaCoCo line coverage (enforced by CI)
- Existing tests passing (228 baseline)

For new business logic, add tests at the appropriate layer:
- Pure function logic → unit test
- Spring wiring / DB behavior → integration test (Testcontainers)
- HTTP contract change → contract test
- New adjudication rule → add to PIT scope in `pom.xml`

## Pull request checklist

Before opening a PR:
- [ ] `mvn clean verify` passes locally
- [ ] New code has tests
- [ ] README / ARCHITECTURE updated if behavior changed
- [ ] Commit messages follow conventional commits
- [ ] PR title follows conventional commits

## Running mutation tests locally

```bash
mvn pitest:mutationCoverage
open target/pit-reports/index.html
```

If your change lowers mutation score below 85%, investigate the surviving
mutants. They're usually real test gaps, not false alarms.

## Running performance tests locally

```bash
docker compose up -d && mvn spring-boot:run &
./perf/run-all.sh
```

Truncate the claims table between runs — the duplicate-stress scenario
accumulates state and inflates p95 artificially:

```bash
docker exec claimguard-postgres psql -U claimguard -d claimguard \
    -c "TRUNCATE TABLE claim_audit_log, claims RESTART IDENTITY CASCADE;"
```
