# ClaimGuard

[![CI](https://github.com/AhmedKamal-41/adjudicore/actions/workflows/ci.yml/badge.svg)](https://github.com/AhmedKamal-41/adjudicore/actions/workflows/ci.yml)
![Coverage](https://img.shields.io/badge/coverage-98%25-brightgreen)
![Java](https://img.shields.io/badge/java-17-orange)
![Spring Boot](https://img.shields.io/badge/spring_boot-3.3.5-green)

Healthcare claims validation and adjudication API with synthetic data.

## Prerequisites

- Java 17
- Docker
- Maven

## Setup

Setup coming soon.

## Testing strategy

Three tiers. `mvn test` runs unit tests only; `mvn verify` runs all three.

| Tier | Count | Tooling | Asserts | Typical time |
|---|---|---|---|---|
| **Unit** | 146 | H2, Mockito, `@DataJpaTest` | Internal logic, wiring, rule behavior | ~1.5 s |
| **Integration** | 35 | Testcontainers Postgres + `@SpringBootTest(RANDOM_PORT)` | End-to-end behavior, persistence, audit log | ~45 s |
| **Contract** | 33 | Testcontainers + REST Assured | HTTP shape, field names, status codes, error envelope | ~30 s |

Total: ~214 tests, ~93-97% merged line coverage.

The 85% line-coverage gate applies to the merged coverage report produced at
`target/site/jacoco-merged` after `mvn verify`.

**Contract vs integration:** integration tests answer *does the system work*;
contract tests answer *does the HTTP contract remain stable*. If someone
renames a JSON field, flips a status code, or leaks a stack trace into an
error response, the contract tests are the first thing that should fail.
