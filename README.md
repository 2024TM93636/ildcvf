# ILDCVF

**Intelligent Living Documentation and Contract Validation Framework for Large-Scale REST APIs Using LLM-Augmented Traffic Analysis**

ILDCVF is an M.Tech Software Engineering dissertation research prototype that compares observed REST API behavior with a documented OpenAPI baseline and supports an evidence-grounded, human-governed workflow for candidate contract updates.

The system is **not** an automatic contract publisher or a production-scale API monitoring platform. Its primary contribution is the integration of runtime evidence, deterministic drift detection, structured LLM suggestions, independent proposal validation, human review, and traceable candidate contract versioning.

## Project status

**Final dissertation preparation: completed (November 2026).** The final report and 14-slide viva presentation were accepted for project-tracking closure, subject to formal university submission and acceptance. Research limitations described below remain applicable.

## System workflow

```text
API Client
    |
    v
Monitoring Gateway -------> Resident Management REST API
    |
    v
Captured Request/Response Evidence
    |
    v
PostgreSQL Traffic Events
    |
    v
OpenAPI Operation Matching + Deterministic Drift Detection
    |
    v
Structured Drift Finding
    |
    v
Bounded Evidence + Groq LLM Proposal
    |
    v
Independent Deterministic Proposal Validation
    |
    +---- Invalid/unsupported ----> Rejected Proposal
    |
    v
Human Review (Approve / Reject)
    |
    +---- Rejected ---------------> Recorded Rejection
    |
    v
Approved, Validated Proposal
    |
    v
Candidate OpenAPI Generation + Validation + SHA-256
    |
    v
Immutable Candidate Contract Version (not automatic publication)
```

The LLM **does not perform drift detection** and cannot independently approve or publish a contract change. A detected mismatch may indicate either an implementation defect or outdated documentation; human review is therefore required.

## Technology stack

| Area | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.1, Maven |
| Persistence | PostgreSQL 17, Flyway |
| Contract specification and parsing | OpenAPI 3.1, Swagger Parser 2.1.48 |
| LLM integration | Groq API, `openai/gpt-oss-20b` |
| Frontend | React 19, Material UI, Vite |
| Reference application | Resident Management REST API |
| Containerization | Docker for the **Resident API database** |

The implementation is modular; it should not be described as a deployed distributed microservices platform. Docker usage does not imply that all services were containerized.

## Implemented capabilities

### Runtime evidence capture

A monitoring gateway forwards selected HTTP requests to the reference Resident API and records request/response evidence. Traffic events have a durable sequence, unique event identifier, timestamps, HTTP metadata, available body content, body-size information, and truncation indicators. PostgreSQL stores structured header/query metadata in JSONB fields. Evidence persistence is synchronous in the evaluated gateway path.

### Deterministic OpenAPI drift analysis

The analyzer matches HTTP methods and concrete paths to OpenAPI operations (for example, `GET /api/residents/2` to `GET /api/residents/{id}`) and checks supported request/response contract rules. The controlled evaluation covered categories including undocumented operations, field presence, required fields, types, enum values, and response statuses. Detection is rule-based rather than LLM-based.

A demonstrated finding identified a response `id` documented as an integer but observed as a string: `RESPONSE_TYPE_MISMATCH`, severity `BREAKING`.

### LLM-assisted structured proposals

Groq receives a bounded evidence package derived from an already identified drift finding and relevant contract context. It is asked to produce a structured proposal describing a potential change. Successful provider communication or JSON parsing is **not** proof of a valid proposal.

An independent validator checks the proposed operation, target field, change kind, and evidence grounding. Unsupported, invalid, ambiguous, or ungrounded suggestions are rejected.

### Human review and candidate versioning

Eligible validated proposals can be approved or rejected through a human review workflow. Candidate contract generation requires a validated proposal and an approved review decision referring to the same attempt. The generated OpenAPI YAML is validated before persistence; SHA-256 checksums protect exact document content, and schema isolation prevents unrelated reusable definitions from being unintentionally modified. Transactional checks, locking, and uniqueness constraints protect candidate version creation.

A candidate version is retained as a separate record. **The framework does not automatically replace or publish the baseline contract.**

### React dashboard

The user interface provides six views:

1. Overview
2. Traffic Monitor
3. Drift Analysis
4. LLM Proposals
5. Human Review
6. Contract Versions

These views expose distinct stages of the evidence-to-review workflow rather than conflating observed traffic with approved changes.

## Research evaluation and verified outcomes

### Automated verification

| Check | Recorded final outcome |
| --- | --- |
| Backend regression tests | **388 passed**, no reported failures |
| Frontend lint | Passed |
| Frontend production build | Passed |
| Candidate generator focused acceptance | 5/5 passed (historical focused run) |

Test counts are records of completed verification, not a claim that every production condition or contract variant has been tested.

### Controlled drift evaluation

The historical controlled dataset comprised **18 scenarios**: 13 evaluated, four partially evaluated, and one not evaluated. For the evaluated subset, the recorded confusion counts were 10 true positives, three true negatives, zero false positives, and zero false negatives, giving precision, recall, and F1 of 1.0 **on that subset only**. Partial and unevaluated scenarios must not be silently counted as successful detections, and these figures do not establish general accuracy across arbitrary REST APIs.

### Demonstrated workflow evidence

Two distinct historical examples should not be combined into a single end-to-end transaction:

- **Drift demonstration:** `traffic-00000027` captured a `GET /proxy/api/residents/2` response with the documented integer `id` observed as a string, resulting in a breaking response type mismatch.
- **Approved candidate-version chain:** `traffic-00000020` was linked to a validated proposal, approved review, and persisted candidate contract version. This demonstrates a historical approval-gated versioning chain, but it is not the same event as the drift demonstration above.

The historical candidate YAML export was not available for a separate retrospective checksum re-verification.

### Gateway performance observations

Historical local measurements compared direct Resident API requests with requests routed through the monitoring gateway:

| Workload | Direct mean | Gateway mean | Difference |
| --- | ---: | ---: | ---: |
| 5 virtual users | 0.786 ms | 22.427 ms | +21.641 ms |
| 10 virtual users | 1.633 ms | 34.750 ms | +33.117 ms |

These figures describe the gateway forwarding, capture, synchronous PostgreSQL persistence, and logging path—not synchronous LLM inference. They come from historical summary evidence; the raw paired measurements were unavailable for full reconciliation, and some historical smoke artifacts did not meet HTTP 200 checks. They are **descriptive local observations**, not production performance or throughput guarantees.

### Real-LLM reliability campaign

The frozen protocol planned **10 scenarios × 4 repetitions = 40 attempts**, without automatic retries, using Groq and `openai/gpt-oss-20b`. The recorded campaign was **suspended after six attempts**, spanning two scenarios:

| Outcome | Count |
| --- | ---: |
| Planned attempts | 40 |
| Recorded attempts | 6 |
| Unexecuted attempts | 34 |
| Transport failures without a usable retained provider response | 2 |
| HTTP 200 responses with successfully parsed structured output | 4 |
| Parsed proposals rejected by deterministic grounding validation | 4 |
| Accepted proposals within the measured campaign | 0 |

The four parsed responses were rejected with `PROPOSED_VALUE_NOT_GROUNDED`; each matched 10 of 11 expected fields. Thus, **parse success was 4/4 among responses reaching parsing, while validation acceptance was 0/4**. The first two transport failures cannot be attributed to a specific cause from retained evidence and are not evidence of hallucination. The incomplete six-attempt campaign does **not** establish general model reliability or statistical performance. A separate historical successful validated proposal chain does not change these measured-campaign outcomes.

The measured-campaign procedure and recorded deviations should be consulted before any future continuation. Do not characterize the 34 unexecuted attempts as completed or assume the frozen experiment can be resumed without research-governance review.

## Experiment tooling

The repository's historical operator-controlled LLM experiment entry point is `scripts/e6-measured.ps1`. It supports explicit `-Preflight`, `-Report`, and guarded `-Execute` operations with a scheduled attempt identifier; execution additionally requires controller authorization. It does not automatically retry or select the next attempt.

**Do not execute measured provider calls merely to reproduce this README.** Protect Groq credentials and authorization tokens; never commit them to source control or experiment artifacts.

## Local setup and verification

The project uses separate backend/frontend components and a Resident API reference application. The precise checkout layout, environment-variable names, database credentials, ports, and startup commands must be confirmed from the repository's current configuration before being documented as reproducible instructions. This README intentionally does not invent those values.

For a local verification of the backend, use the Maven wrapper from the applicable backend project directory (Windows PowerShell):

```powershell
.\mvnw.cmd test
```

Frontend verification should use the scripts defined in the frontend's actual `package.json` (lint and production build were recorded as passing). Start PostgreSQL and the Resident API using their checked-in configuration; Docker was specifically used for the Resident API database. Only run Groq integration or measured experiments with valid credentials and deliberate authorization.

## Known limitations and future work

- **Scalability:** synchronous gateway persistence adds latency; asynchronous bounded processing, batching, retention, and partitioning require further design and evaluation.
- **Privacy:** captured headers and payload values can contain sensitive data. Full schema-only evidence storage, systematic redaction, and retention controls were not established as complete production safeguards.
- **Security:** enterprise-grade authentication, authorization, and auditing are future hardening work; do not infer them from prototype review controls.
- **Coverage:** deterministic analysis implements supported OpenAPI rules, not the entire specification or every compatibility scenario.
- **LLM reliability:** the 40-attempt experiment was incomplete, and the recorded structured proposals in that campaign failed evidence grounding.
- **Deployment:** no production-scale deployment, automatic publication of approved contracts, or proven production-grade service resilience is claimed.
- **Regression-test generation:** a potential extension, not a demonstrated final integrated capability.

## Research context

The dissertation followed a Design Science Research approach and used a controlled Resident Management REST API to study runtime contract drift, LLM-assisted proposals, validation, and governance. The final dissertation report is the authoritative source for detailed methodology, findings, limitations, and references.

**Academic status:** dissertation preparation accepted and closed in project tracking (November 2026); formal university acceptance is separate.
