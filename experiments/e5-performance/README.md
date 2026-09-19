# E5 — ILDCVF Performance Evaluation

## Purpose

This experiment evaluates the client-observed runtime overhead introduced by
the currently implemented ILDCVF traffic interception and persistence path
under controlled local conditions.

It is an empirical dissertation experiment, not a production capacity test.

## Measurement paths

### DIRECT

k6/client
→ http://localhost:8090/api/residents
→ Resident API

### ILDCVF

k6/client
→ http://localhost:8080/proxy/api/residents
→ TrafficProxyController
→ TrafficProxyService
→ ResidentForwardingClient
→ Resident API
→ TrafficEvent persistence in PostgreSQL
→ client

The ILDCVF measurement includes traffic interception, forwarding, evidence
capture, TrafficEvent construction, synchronous PostgreSQL persistence, normal
proxy processing, the additional HTTP hop, and the current per-request logging.

It does not include synchronous DriftAnalysisService execution because the
frozen M4 drift detector is not invoked by the current live proxy path.

It also does not include LLM processing.

## TrafficEvent.durationMs

TrafficEvent.durationMs is not the primary performance metric.

The internal timer is calculated after upstream forwarding but before
TrafficEvent persistence and some remaining proxy processing. It therefore
does not represent total client-observed proxy latency.

Primary latency measurements come from equivalent client-observed k6 requests.

## Primary latency metrics

Source: k6 http_req_duration.

Unit: milliseconds.

Metrics:

- mean
- p50 / median
- p95
- p99

## Successful throughput

successful throughput =
successful HTTP requests / measured seconds

Total requests and failed-request evidence are retained separately.

Attempted request rate must not be reported as successful throughput when
failures occur.

## Observed proxy overhead

For latency statistic S:

absolute overhead(S) =
ILDCVF(S) - DIRECT(S)

relative overhead(S) =
((ILDCVF(S) - DIRECT(S)) / DIRECT(S)) * 100

Relative overhead is unavailable when the DIRECT value is zero.

These values represent observed end-to-end overhead under this controlled
local experiment. They are not measurements of pure interceptor CPU cost.

## Workload

The primary operation is read-only:

GET /api/residents

Profiles:

| Profile | VUs | Duration | Purpose |
| --- | ---: | ---: | --- |
| smoke | 1 | 5 s | instrumentation validation only |
| steady | 5 | 30 s | primary low/moderate measurement |
| moderate | 10 | 30 s | modestly increased concurrency |

Constant VUs are used because this experiment compares runtime paths rather
than attempting to determine maximum system capacity.

No stress, spike, soak, breakpoint, or failure-capacity test is included.

## Warm-up

Before every measured run, warm up the corresponding path separately:

- 1 VU
- 10 seconds
- discard the result

Warm-up samples are not part of the measured dataset.

## Final measured repetitions

For each of steady and moderate:

- 3 DIRECT measured runs
- 3 ILDCVF measured runs

Order:

1. DIRECT → ILDCVF
2. ILDCVF → DIRECT
3. DIRECT → ILDCVF

The same workload profile is used for each paired comparison.

Smoke runs are validation only and are NOT dissertation performance results.

## Raw results

Each measured run must be identifiable by:

- run ID
- timestamp
- DIRECT or ILDCVF path
- profile
- target URL
- configured VUs
- configured duration

Captured metrics must permit derivation of:

- total requests
- successful requests
- failed requests / failure rate
- measured duration
- successful throughput
- mean latency
- p50 latency
- p95 latency
- p99 latency

Generated result files are experimental evidence and should not be casually
committed. Keep them locally during experimentation. Large raw k6 logs should
not be committed.

## Aggregation

Repeated runs are aggregated using the arithmetic mean of each per-run metric.

Example:

aggregate DIRECT p95 =
mean(DIRECT-run-1 p95, DIRECT-run-2 p95, DIRECT-run-3 p95)

This is a mean of per-run percentile estimates. It is not a reconstructed
global percentile over all requests.

No claim of statistical significance or confidence interval is made at this
stage.

## Environment metadata

Record for the final measured campaign:

- experiment date/time
- operating system
- CPU model/core information
- RAM
- Java version
- k6 version
- Docker version
- PostgreSQL image/version
- ILDCVF Git commit
- Resident API repository commit
- DIRECT URL
- ILDCVF URL
- profile
- TrafficEvent repository mode
- relevant logging behavior

Do not record unnecessary usernames, machine names, serial numbers, IP
addresses, or other personal identifiers.

## Experimental confounders and limitations

Relevant factors include:

- Resident API processing time
- local loopback/network cost
- additional proxy HTTP hop
- request/response body copying
- evidence/header/payload capture
- PostgreSQL TrafficEvent persistence
- TrafficEvent table growth
- JVM/JIT warm-up
- database/cache warm-up
- per-request INFO logging
- Docker resource contention
- background machine processes
- garbage collection and scheduling variability
- direct/proxy ordering
- first-run effects
- absence of synchronous drift analysis

The experiment controls or documents these factors rather than attempting to
engineer all of them away.

Results are local controlled measurements and must not be generalized directly
to production capacity or arbitrary REST APIs.

## Scientific scope

The ILDCVF path measures the overhead of the currently implemented runtime
interception and persistence path.

It does NOT establish the overhead of:

runtime proxy
+ deterministic drift analysis
+ LLM processing

because deterministic drift analysis is not currently invoked synchronously by
the live proxy and LLM processing has not yet been integrated.

A later separately approved ablation experiment may investigate additional
component overhead.