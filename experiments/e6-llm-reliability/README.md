# E6 — LLM Proposal Reliability Evaluation

## Purpose

E6 evaluates whether the selected LLM can generate grounded REST API
contract-change proposals from deterministic ILDCVF evidence while the frozen
M7 `ContractChangeProposalValidator` remains the deterministic safety boundary.

The LLM does not detect drift. Drift detection occurs before proposal
generation.

## Dataset

The frozen dataset contains exactly 10 positive scenarios:

- one representative for every frozen `ContractChangeKind`;
- nine scenarios based on the controlled M5 Resident API drift semantics;
- one E6-only synthetic closed-response scenario for
  `UNEXPECTED_RESPONSE_FIELD`.

The synthetic scenario does not modify the frozen Resident OpenAPI baseline.

The canonical scientific ground truth is the Java
`LlmScenarioCatalog`. `scenarios/ground-truth.json` is a deterministic exported
artifact and is verified against the Java catalog.

Each scenario binds:

scenario ID
→ deterministic `GroundedEvidencePackage`
→ `EvidenceFingerprint.sha256(evidence)`
→ expected structured proposal.

## Scenarios

1. `llm-001-undocumented-operation`
    - `UNDOCUMENTED_OPERATION`
    - `ADD_OPERATION`

2. `llm-002-request-add-field`
    - `UNEXPECTED_REQUEST_FIELD`
    - `ADD_REQUEST_FIELD`

3. `llm-003-request-relax-required`
    - `MISSING_REQUIRED_REQUEST_FIELD`
    - `RELAX_REQUEST_REQUIRED_FIELD`

4. `llm-004-request-change-type`
    - `REQUEST_TYPE_MISMATCH`
    - `CHANGE_REQUEST_FIELD_TYPE`

5. `llm-005-request-add-enum`
    - `REQUEST_ENUM_MISMATCH`
    - `ADD_REQUEST_ENUM_VALUE`

6. `llm-006-response-add-status`
    - `UNDOCUMENTED_RESPONSE_STATUS`
    - `ADD_RESPONSE_STATUS`

7. `llm-007-response-relax-required`
    - `MISSING_REQUIRED_RESPONSE_FIELD`
    - `RELAX_RESPONSE_REQUIRED_FIELD`

8. `llm-008-response-change-type`
    - `RESPONSE_TYPE_MISMATCH`
    - `CHANGE_RESPONSE_FIELD_TYPE`

9. `llm-009-response-add-enum`
    - `RESPONSE_ENUM_MISMATCH`
    - `ADD_RESPONSE_ENUM_VALUE`

10. `llm-010-response-add-field`
    - `UNEXPECTED_RESPONSE_FIELD`
    - `ADD_RESPONSE_FIELD`
    - E6-only synthetic closed-response scenario.

## Scored ground-truth fields

Exactly 11 structured fields are scored:

1. `sourceTrafficEventId`
2. `sourceFindingId`
3. `driftType`
4. `changeKind`
5. `method`
6. `runtimePath`
7. `matchedOpenApiPath`
8. `targetFieldPath`
9. `expected`
10. `observed`
11. `proposedValue`

`proposalId` is not scored because it is generated identity.

`rationale` is not scored by exact string comparison because it is qualitative
prose.

## Canonical proposedValue rules

`ADD_OPERATION`:

`<method> <runtimePath>`

`RELAX_REQUEST_REQUIRED_FIELD` and
`RELAX_RESPONSE_REQUIRED_FIELD`:

`optional`

For:

- `ADD_REQUEST_FIELD`
- `CHANGE_REQUEST_FIELD_TYPE`
- `ADD_REQUEST_ENUM_VALUE`
- `ADD_RESPONSE_STATUS`
- `ADD_RESPONSE_FIELD`
- `CHANGE_RESPONSE_FIELD_TYPE`
- `ADD_RESPONSE_ENUM_VALUE`

the canonical value is exactly:

`evidence.observed()`

No richer proposal content is inferred.

## Campaign

The measured campaign is frozen as:

- 10 scenarios;
- 4 repetitions per scenario;
- 40 measured generations;
- maximum 2 pre-campaign smoke calls.

The four repetitions intentionally use identical evidence, prompt and generation
parameters, including the same seed. They measure repeatability under nominally
identical controlled conditions.

## Provider and generation configuration

PRELIMINARY UNTIL E6-D SMOKE-TEST FREEZE.

- provider: Groq
- model: `openai/gpt-oss-20b`
- temperature: `0.0`
- top_p: `1.0`
- seed: `20260919`
- max_completion_tokens: `512`
- stream: `false`
- structured output: JSON Schema
- strict schema: `true`
- service tier: `on_demand`
- client timeout: 30 seconds

This configuration must not be silently changed. Any E6-D adjustment must be
explicitly documented before the measured campaign begins.

## Retry policy

Maximum one retry is permitted only for an infrastructure-level:

- timeout;
- HTTP 5xx response;
- transport failure.

No retry is permitted for:

- malformed JSON;
- parse failure;
- validator rejection;
- hallucination;
- ground-truth incorrect output.

A failed original attempt must not be deleted merely because a permitted
infrastructure retry succeeds.

## Metrics

Let:

- `N` = total planned measured attempts;
- `P` = provider-successful responses;
- `J` = successfully parsed structured proposals;
- `V` = validator-valid proposals;
- `C` = proposals exactly matching all 11 scored ground-truth fields;
- `H` = parsed proposals containing at least one hallucinated grounded claim.

### Provider success rate

`provider_success_rate = P / N`

### Parse success rate

`parse_success_rate = J / P`

### Parse failure rate

`parse_failure_rate = (P - J) / P`

### Validator acceptance rate

`validator_acceptance_rate = V / J`

### Validator rejection rate

`validator_rejection_rate = (J - V) / J`

### Exact proposal correctness

`exact_proposal_correctness = C / J`

A proposal is exactly correct only when all 11 scored structured fields match
the frozen ground truth.

### End-to-end exact success

`end_to_end_exact_success = C / N`

This metric retains provider and parsing failures in the end-to-end denominator.

### Field accuracy

For scored field `f`:

`field_accuracy(f) = exact matches for f / J`

### Micro field accuracy

There are 11 scored fields per parsed proposal:

`micro_field_accuracy = total correct scored fields / (J * 11)`

### Hallucinated proposal rate

`hallucinated_proposal_rate = H / J`

### Exact structured pairwise agreement

Within each scenario, compare all successfully parsed repetitions using the
11-field scored structured tuple:

`exact_structured_pairwise_agreement =
 identical parsed output pairs / all comparable parsed output pairs`

With four parsed repetitions, there are six possible pairs.

### Change-kind agreement

`change_kind_agreement =
 pairs with identical changeKind / comparable parsed pairs`

### Target-field agreement

`target_field_agreement =
 pairs with identical targetFieldPath / comparable parsed pairs`

### Proposed-value agreement

`proposed_value_agreement =
 pairs with identical proposedValue / comparable parsed pairs`

Provider or parse failures are excluded from pairwise structured agreement but
remain visible in provider/parse reliability metrics.

## Hallucination

For E6:

A parsed structured proposal is hallucinated when at least one grounded
structured claim introduces, redirects, contradicts or substitutes information
unsupported by the supplied `GroundedEvidencePackage` and frozen deterministic
proposal rules.

The following are not equivalent:

`hallucinated != validator-rejected != ground-truth-incorrect`

These categories can overlap.

For example, malformed JSON is a generation/format failure and is not
automatically classified as a hallucinated structured proposal.

## Structured-output schema

`schema/contract-change-proposal.schema.json` controls the structural form of
provider output.

It:

- defines all 13 `ContractChangeProposal` properties;
- requires all 13 properties;
- rejects additional properties;
- enumerates the frozen drift types;
- enumerates the frozen change kinds;
- permits null only for fields with legitimate null cases.

The schema does not encode semantic DriftType-to-ContractChangeKind
relationships. Those remain the responsibility of the deterministic M7
validator.

Schema conformance therefore does not imply scientific correctness.

## Prompt control

`prompts/proposal-v1.txt` is generic across all 10 scenarios.

It does not contain:

- scenario IDs;
- scenario-specific expected proposals;
- the complete deterministic DriftType-to-ContractChangeKind mapping.

`prompts/proposal-v1.sha256` stores the lowercase SHA-256 of the exact prompt
bytes. The prompt hash will later be recorded for every measured attempt.

## Runtime isolation

E6 uses an isolated experiment harness.

It does not integrate LLM generation into:

- `TrafficProxyService`;
- the Resident API;
- normal application request processing.

No E6 provider latency is therefore added to the production-like proxy path or
to the already frozen M6 performance experiment.

## Privacy boundary

The future provider receives only the bounded `GroundedEvidencePackage`.

E6 does not require sending:

- raw `TrafficEvent`;
- request headers;
- response headers;
- request bodies;
- response bodies;
- cookies;
- JWTs;
- authorization headers;
- unrelated runtime traffic.

## Claims boundary

E6 characterizes only:

selected provider/model
× fixed prompt/configuration
× 10 controlled ILDCVF scenarios
× 4 repetitions.

It does not establish general LLM accuracy for arbitrary REST APIs, arbitrary
OpenAPI contracts, arbitrary providers, or production workloads.

Validator acceptance also does not establish that a proposed contract change is
desirable. It establishes only deterministic consistency with the frozen M7
grounding rules.

## E6-A provider boundary

E6-A contains no real provider invocation.

No API key is required or used.

Network/provider integration begins only after E6-A is reviewed and accepted.