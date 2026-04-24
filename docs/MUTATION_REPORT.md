# Mutation Testing Report

**Run**: 2026-04-24
**PIT version**: 1.17.0
**Mutator set**: STRONGER
**Scope**: `com.ahmedali.claimguard.adjudication.*`, `com.ahmedali.claimguard.validation.*`

`service.*` is out of scope: its unit tests are `@DataJpaTest`-based, which
costs a full Spring context boot per mutant and blows the PIT budget without
improving coverage beyond what unit-scoped mutants on the downstream rules
already provide.

## Summary

| Metric | Baseline | After null-branch tests |
|---|---|---|
| Mutations generated | 132 | 132 |
| Killed | 118 | 130 |
| Survived | 13 | 2 |
| No coverage | 1 | 0 |
| Line coverage (mutated classes) | 98% | 99% |
| **Mutation score** | **89%** | **98%** |
| Test strength | 90% | 98% |

Both the baseline and post-cleanup runs clear the 75% `mutationThreshold`
and 85% `coverageThreshold` gates configured in `pom.xml`. The two
remaining survivors are both documented EQUIVALENT mutations (see below).
The report will be re-run weekly via `.github/workflows/mutation.yml`.

## Tests added to kill real gaps

Each surviving mutant in the baseline fell into one of two buckets:
behaviorally meaningful (REAL GAP — added a targeted test) or behaviorally
identical under the mutation (EQUIVALENT — documented below). No test was
added that doesn't directly correspond to a survivor in the baseline.

| Test | Kills |
|---|---|
| `ProviderNetworkValidatorTest.outOfNetwork_nullMember_returnsValid_doesNotNpeOnPlanCode` | `validate:22` — `member != null` short-circuit in the OON chain |
| `ProviderNetworkValidatorTest.outOfNetwork_memberWithNullPlanCode_returnsValid` | `enforcesStrictNetwork:32–33` — null-planCode early return |
| `CoverageLimitRuleTest.nullMember_returnsEmpty` | `evaluate:33` — null-member short-circuit |
| `CoverageLimitRuleTest.priorApproved_withNullServiceDate_isIgnoredFromSum_notDenied` | `evaluate:47` — null serviceDate skip-continue |
| `CoverageLimitRuleTest.priorApproved_withNullAllowedAmount_isIgnoredFromSum_notDenied` | `evaluate:50` — null allowedAmount `.add()` guard |
| `AmountValidatorTest.nullAmount_returnsCarc45` | `validate:22` — `amount == null` short-circuit |
| `AmountValidatorTest.nullMember_skipsCapCheck_returnsValid_evenIfOverAnyCap` | `validate:25` — `member != null` cap-check guard |
| `CodeFormatValidatorTest.nullProcedureCode_returnsCarc181` | `validate:27` — null CPT branch |
| `CodeFormatValidatorTest.nullDiagnosisCode_returnsCarc181` | `validate:30` — null ICD-10 branch |
| `ServiceDateValidatorTest.nullSubmissionDate_skipsFilingLimitCheck_returnsValid` | `validate:25` — null submissionDate guard |
| `PriorAuthRuleTest.nullProcedureCode_returnsEmpty_doesNotThrow` | `evaluate:27` — null CPT guard |

## Surviving mutants (documented, not fixed)

### EQ-1 ConditionalsBoundary on allowed-amount cap-at-billed
- **File**: `AllowedAmountCalculator.java:49`
- **Mutation**: `if (allowed.compareTo(billed) > 0)` → `> ` mutated to `>=`
- **Category**: EQUIVALENT
- **Rationale**: at the boundary `allowed == billed`, both the original
  (`>`, fall through, return `allowed`) and the mutant (`>=`, enter, return
  `billed`) yield the same `BigDecimal` value because `allowed == billed`.
  Any concrete output stream would be byte-identical.

### EQ-2 `!isValid()` check in validation pipeline
- **File**: `ClaimValidationPipeline.java:29`
- **Mutation**: `if (!result.isValid())` equality replaced with `true`
- **Category**: EQUIVALENT
- **Rationale**: `ValidationResult.valid()` returns empty `rejectCodes` and
  `messages`. With the mutation, the loop adds an empty list for every
  validator regardless of validity; the final aggregate still contains
  exactly the reject codes of genuinely invalid validators. No behavioral
  change.

## Interpretation

PIT is a test-quality probe, not a test generator. Every mutant killed here
means a test provably catches a real defect class; every surviving
equivalent means PIT's AST-level rewrite can't distinguish the code paths.
The two remaining survivors after cleanup fall into well-understood
equivalent-mutation categories (boundary with identical endpoints,
add-empty-list no-op) and are documented rather than papered over by
lowering the threshold.
