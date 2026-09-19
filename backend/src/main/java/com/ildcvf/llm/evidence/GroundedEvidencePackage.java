package com.ildcvf.llm.evidence;

import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;

/**
 * Bounded deterministic evidence supplied to a future LLM provider.
 *
 * <p>The package contains only evidence required to reason about one
 * deterministic drift finding. It intentionally does not expose the source
 * TrafficEvent, complete HTTP request/response bodies, headers, cookies,
 * JWTs, or unrelated runtime traffic data.</p>
 */
public record GroundedEvidencePackage(
        String trafficEventId,
        long trafficSequence,
        String findingId,
        DriftType driftType,
        DriftSeverity severity,
        String method,
        String runtimePath,
        String matchedOpenApiPath,
        String fieldPath,
        String expected,
        String observed,
        String findingMessage,
        RelevantContractContext relevantContractContext
) {
}
