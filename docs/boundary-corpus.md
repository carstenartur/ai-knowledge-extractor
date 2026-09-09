# Boundary corpus and validation status

The [committed corpus](../examples/boundary-corpus/manifest.json) contains eleven small,
redistributable synthetic repositories under the project's Apache-2.0 license. Its labels specify
client source locations, server operations and link outcomes independently of the extractor output.
The scenarios cover Spring/fetch, Spring/axios, JAX-RS/fetch, typed single-operation clients,
frontend orchestration, a backend facade, DTO/view-model translation, backend-state interpretation,
inconsistent errors, dynamic URLs, frontend-only and Java-only code, and non-code examples.

Run the reproducible evaluation with:

```bash
./gradlew :core:test --tests org.aiknowledge.core.BoundaryCorpusEvaluationTest --rerun-tasks
```

The test writes `core/build/reports/boundary-corpus/report.json` and `report.md`; normal CI runs it
and retains both as `boundary-corpus-evaluation`. JSON contains complete raw boundary/dependency
facts and dimensions, case-level true/false positives and false negatives, precision/recall by
framework and boundary kind, all weight perturbations, and expected ranking comparisons.
Precision or recall is null when its denominator is zero; empty evidence is not reported as perfect
coverage. The JSON explicitly records `blindedExpertReviewPerformed=false`.

## Current observations (corpus 1.0, scoring model 1.0)

| Scenario | Default proxy score | Score range with one weight changed by ±25% |
|---|---:|---:|
| Clean typed client | 1 | 0–1 |
| Frontend orchestration | 23 | 21–25 |
| Backend facade | 1 | 0–1 |
| Manual translation | 3 | 3–4 |
| Backend-state interpretation | 7 | 6–8 |
| Inconsistent error handling | 4 | 4–5 |
| Dynamic URL | 14 | 12–16 |
| Frontend-only | 12 | 11–13 |
| Java-only | 0 | 0 |
| JAX-RS client | 1 | 0–1 |
| Non-code examples | 0 | 0 |

After correcting quoted API-call examples being counted as executable calls, there are no false
positives or false negatives in these hand-labelled fixtures. Six expected relative rankings hold;
none invert under the fourteen single-weight perturbations. These are observations about this small
corpus, not estimates of extraction accuracy in arbitrary repositories. The low clean-client score
also includes residual contract uncertainty; it is not a subjective effort estimate.

The non-code example originally produced a false client call from a quoted `fetch(...)` string.
Boundary call detection now distinguishes quoted text from executable template expressions. The
fixture remains as a regression control. Structural parsing still has limitations, including
framework wrappers, runtime route construction, reflection and unsupported syntax; false positives
and missed calls in real code must be added to the labelled corpus instead of tuning labels to match
the extractor.

## Scoring evolution and external validation

`boundary-analysis.json.scoringModelVersion` is independent of index schema v2, the report's own
schema version and provider fact-contract versions. `scoringWeights` and all raw dimensions are
retained. `BoundaryScoringModel.score(...)` supports recomputing the aggregate; `contractClarity` is
inverted so that greater clarity lowers the score, and supplied weights are normalized.

The weights remain **heuristic and uncalibrated against human expert judgment**. The tests establish
reproducibility, limited extraction conformance and selected ranking regressions. They do not
establish developer mental load, productivity gains, or universally suitable gate defaults.

Still required before stronger empirical claims:

1. Recruit independent architecture reviewers and hide extractor scores while they assess a larger,
   representative corpus, including real systems and counterexamples.
2. Record the review rubric, disagreements, anonymized judgments and ranking agreement; assess
   inter-rater consistency and extraction errors by framework.
3. Evaluate revised weights on a held-out set, version any changed model, and publish remaining
   uncertainty and sensitivity results. Keep all raw evidence available for alternative scoring.

This human study remains tracked in [issue #103](https://github.com/carstenartur/ai-knowledge-extractor/issues/103).
