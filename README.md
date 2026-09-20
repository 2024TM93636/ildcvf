
# ILDCVF

ILDCVF is an M.Tech dissertation prototype for continuous verification of REST API contracts against observed runtime behaviour.

The framework captures API request/response evidence, compares it deterministically with a frozen OpenAPI contract, identifies contract drift, and provides a controlled foundation for LLM-assisted contract-change proposals and later regression-test generation.

The project follows a Design Science Research approach and is evaluated using controlled, reproducible REST API evolution scenarios.

## Architecture

The current implementation follows this flow:

```text
API Client
    |
    v
Traffic Interceptor
    |
    v
Target REST API
    |
    v
Runtime Evidence Capture
    |
    v
PostgreSQL Runtime Knowledge Repository
    |
    v
Deterministic Contract Drift Detection
    |
    v
Grounded Evidence Construction
    |
    v
LLM-Assisted Contract Proposal
    |
    v
Deterministic Proposal Validation
    |
    v
Human Review / Later Application Workflow
````

The current implementation is intentionally modular rather than decomposed into multiple independent microservices.

Major components developed so far include:

* Traffic Interceptor Middleware
* PostgreSQL runtime evidence repository
* deterministic Contract Drift Detection Engine
* controlled deterministic evaluation harness
* interceptor/persistence performance evaluation harness
* bounded LLM evidence construction
* deterministic LLM proposal validation
* isolated real-LLM reliability experiment infrastructure
* measured-campaign safety and reporting tooling

## Completed Implementation Milestones

### Foundation and Target API

The repository contains the project foundation, a controlled Resident Management REST API, and a frozen OpenAPI baseline used for contract-drift experiments.

### Runtime Traffic Capture

The interceptor captures structured request and response evidence while preserving the upstream API response. Sensitive headers are excluded and request/response body truncation is supported.

### Durable Runtime Evidence

Captured traffic events are persisted in PostgreSQL using Flyway-managed schema evolution and deterministic event identities.

### Deterministic Contract Drift Detection

The drift engine compares runtime evidence with the frozen OpenAPI contract and detects controlled deviations including:

* undocumented operations
* unexpected request fields
* missing required request fields
* request type mismatches
* request enum mismatches
* undocumented response statuses
* unexpected response fields where the schema is closed
* missing required response fields
* response type mismatches
* response enum mismatches

Drift detection remains deterministic so that experimental results are reproducible and measurable independently of LLM behaviour.

## Controlled Deterministic Evaluation

The frozen Resident API evaluation dataset contains 18 controlled scenarios:

| Measure             | Result |
| ------------------- | -----: |
| Total scenarios     |     18 |
| EVALUATED           |     13 |
| PARTIALLY_EVALUATED |      4 |
| NOT_EVALUATED       |      1 |
| True positives      |     10 |
| False positives     |      0 |
| False negatives     |      0 |
| Precision           |    1.0 |
| Recall              |    1.0 |
| F1                  |    1.0 |

These values apply only to the controlled evaluated dataset. They must not be interpreted as evidence of general drift-detection accuracy across arbitrary REST APIs.

The partially evaluated and non-evaluated cases deliberately cover conditions such as truncated payloads, malformed JSON, and failure to reach the upstream service.

## Performance Evaluation

Performance evaluation measures the runtime cost of the interceptor and evidence-persistence path.

The completed campaign compared direct Resident API traffic with traffic routed through ILDCVF.

Observed mean latency in the controlled environment was approximately:

| Workload         |   Direct |    ILDCVF | Mean overhead |
| ---------------- | -------: | --------: | ------------: |
| Steady, 5 VUs    | 0.786 ms | 22.427 ms |    +21.641 ms |
| Moderate, 10 VUs | 1.633 ms | 34.750 ms |    +33.117 ms |

These measurements characterize the interceptor/runtime-evidence persistence path in the controlled test environment. They do **not** measure LLM inference latency and should not be generalized to production deployments without further evaluation.

## LLM Reliability Architecture

LLM functionality is deliberately separated from deterministic drift detection.

The reliability boundary is:

```text
Deterministic drift detection
        |
        v
Bounded grounded evidence
        |
        v
LLM-assisted structured proposal
        |
        v
Deterministic validation
        |
        v
Human review
```

Runtime traffic is not supplied directly to the LLM provider. A bounded `GroundedEvidencePackage` is constructed from an already detected drift finding and the relevant contract context.

The LLM proposes a structured contract change. The proposal then passes through deterministic validation before it can be considered for human review or any later application workflow.

This design treats LLM output as untrusted generated data rather than authoritative contract state.

## Frozen Real-LLM Reliability Protocol

The E6 reliability experiment defines:

* 10 controlled positive drift scenarios
* 4 repetitions per scenario
* 40 scheduled measured attempts
* no automatic retries

The frozen measured configuration is:

| Parameter             | Value                |
| --------------------- | -------------------- |
| Provider              | Groq                 |
| Model                 | `openai/gpt-oss-20b` |
| Temperature           | `0`                  |
| topP                  | `1`                  |
| Seed                  | `20260919`           |
| Max completion tokens | `2048`               |
| Retry count           | `0`                  |

The experiment uses a frozen prompt, JSON schema, ground truth, scenario schedule, and protocol manifest.

## Compatibility Observation

Before the measured campaign, a controlled compatibility run using the 2048-token configuration reached the provider successfully with HTTP 200 and produced structured output.

The proposal was parseable, but deterministic validation rejected it because its `proposedValue` was not supported by the grounded evidence.

This observation demonstrates the purpose of the deterministic validation boundary: successful provider communication and syntactically structured output do not by themselves establish that an LLM-generated contract change is acceptable.

It is a single compatibility observation and is not presented as a general model-quality result.

## Current Measured Campaign Status

At the mid-semester checkpoint:

* `llm-001-undocumented-operation-r1` is permanently recorded as `TRANSPORT_FAILURE`.
* `llm-001-undocumented-operation-r2` is permanently recorded as `TRANSPORT_FAILURE`.
* both observations occurred before a retained HTTP response was available
* retry count remained zero
* their exact underlying transport causes cannot be reconstructed from the historical retained evidence
* structured-output parsing is not applicable to these observations
* deterministic proposal validation is not applicable
* ground-truth correctness evaluation is not applicable
* hallucination evaluation is not applicable
* transport diagnostic observability has subsequently been enhanced for future attempts
* `r3` and all later scheduled attempts remain unexecuted

The two transport failures are **not evidence of LLM correctness failure or hallucination failure**. They are provider/infrastructure-layer observations only.

Current campaign progress:

```text
Permanently recorded attempts: 2 / 40
Unexecuted attempts:           38 / 40
```

## Measured Campaign Controller

`scripts/e6-measured.ps1` provides explicit operator-controlled access to the frozen measured campaign.

### Preflight

Performs safety and integrity checks for one explicitly selected scheduled attempt without executing the provider call.

```powershell
.\scripts\e6-measured.ps1 `
    -Preflight `
    -AttemptId "<explicit-scheduled-attempt-id>"
```

### Report

Reads and reports an already recorded attempt without provider execution.

```powershell
.\scripts\e6-measured.ps1 `
    -Report `
    -AttemptId "<recorded-attempt-id>"
```

### Execute

Execution is deliberately guarded and requires an explicit attempt identifier and controller authorization.

```powershell
.\scripts\e6-measured.ps1 `
    -Execute `
    -AttemptId "<explicit-scheduled-attempt-id>" `
    -ControllerAuthorization "<session-supplied-authorization>"
```

Provider credentials and controller authorization must never be stored in repository files or committed to Git. Execution remains explicitly operator-controlled; the script does not automatically select the next attempt, overwrite an existing result, or retry a provider call.

## Verification Baseline

The accepted mid-semester backend regression baseline is:

```text
Tests run: 308
Failures: 0
Errors: 0
Skipped: 0
```

The E6-F4D focused verification baseline is:

```text
Tests run: 64
Failures: 0
Errors: 0
Skipped: 0
```

These baselines were accepted before the repository-freeze documentation step. The documentation-only checkpoint does not alter backend behaviour.

## Mid-Semester Checkpoint Boundary

Implementation is intentionally paused at this point for the mid-semester dissertation checkpoint.

Completed work establishes:

* runtime API evidence capture
* durable evidence persistence
* deterministic OpenAPI drift detection
* controlled deterministic drift evaluation
* interceptor/persistence performance evaluation
* grounded LLM evidence construction
* deterministic LLM proposal validation
* frozen real-LLM reliability protocol
* safe explicit measured-attempt execution infrastructure
* enhanced transport diagnostics for future measured attempts

The final dissertation implementation and evaluation are **not complete**.

### Post-Mid-Sem Experimental Work

Remaining experimental work includes:

* execute the remaining 38 measured LLM attempts under the frozen protocol
* calculate aggregate reliability metrics
* analyze correctness, hallucination, and repetition agreement
* document and appropriately treat any provider/environment reliability issues

### Later System Work

Later system work may include, as required by the final dissertation scope:

* LLM-assisted OpenAPI proposal/application workflow
* generated regression-test workflow
* dashboard and human-review integration
* final integrated evaluation
* dissertation results and analysis
* final viva preparation

The repository at this checkpoint therefore represents a reproducible mid-semester research and implementation baseline, not the final completed ILDCVF system.
