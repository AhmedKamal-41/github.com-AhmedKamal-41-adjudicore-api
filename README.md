# ClaimGuard

[![CI](https://github.com/AhmedKamal-41/adjudicore/actions/workflows/ci.yml/badge.svg)](https://github.com/AhmedKamal-41/adjudicore/actions/workflows/ci.yml)
[![Mutation Testing](https://github.com/AhmedKamal-41/adjudicore/actions/workflows/mutation.yml/badge.svg)](https://github.com/AhmedKamal-41/adjudicore/actions/workflows/mutation.yml)
[![Performance](https://github.com/AhmedKamal-41/adjudicore/actions/workflows/perf.yml/badge.svg)](https://github.com/AhmedKamal-41/adjudicore/actions/workflows/perf.yml)
![Coverage](https://img.shields.io/badge/coverage-98%25-brightgreen)
![Mutation Score](https://img.shields.io/badge/mutation_score-98%25-brightgreen)
![Java](https://img.shields.io/badge/java-17-orange)
![Spring Boot](https://img.shields.io/badge/spring_boot-3.3.5-green)

Healthcare claims validation and adjudication API with synthetic data.

## Prerequisites

- Java 17
- Docker
- Maven

## Setup

Setup coming soon.

## API Documentation

Interactive API docs auto-generated from controller signatures:

- Spec: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Implementation and docs cannot drift — every endpoint signature change
updates the spec automatically. Contract tests in
`com/ahmedali/claimguard/contract/ContractOpenApiTest.java` verify the spec
stays consistent with expected endpoints and schemas.

## Testing strategy

Five quality axes, each tool doing one job the others don't:

| Layer | Count | Tool | Purpose | Typical time |
|---|---|---|---|---|
| Unit | 157 | JUnit 5, Mockito, `@DataJpaTest` | Pure logic, wiring, rule behavior | ~1.5 s |
| Integration | 35 | Testcontainers Postgres + `@SpringBootTest` | Full-stack behavior, persistence, audit log | ~45 s |
| Contract | 36 | REST Assured | HTTP contract stability (field names, status codes, error envelope, OpenAPI spec) | ~40 s |
| Performance | 3 scenarios | k6 | p95 < 500 ms, error rate < 1% SLOs | ~90 s |
| Mutation | 98% (130/132) | PIT | Test quality — kills mutated code to prove tests catch bugs | ~90 s |

`mvn test` runs unit only; `mvn verify` adds integration + contract;
`mvn pitest:mutationCoverage` runs mutation; `./perf/run-all.sh` drives k6
against a running app. See [`docs/MUTATION_REPORT.md`](docs/MUTATION_REPORT.md)
for surviving-mutant analysis.

The 85% line-coverage gate applies to the merged coverage report produced
at `target/site/jacoco-merged` after `mvn verify`. PIT enforces a separate
mutation-score threshold (75% initial, raising to 85% once baseline is clean).

**Why five layers instead of one:** unit tests tell you logic is right;
integration tests tell you the wiring survives a real DB; contract tests
tell you the API won't accidentally change shape on a refactor; mutation
tests tell you the other three aren't coincidentally passing on trivial
assertions; k6 tells you none of that matters if the system falls over
under load.
