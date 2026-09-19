package com.ildcvf.drift.domain;

import java.util.List;
import java.util.Objects;

public record DriftAnalysisResult(
        String trafficEventId,
        long trafficSequence,
        EvaluationStatus evaluationStatus,

        String method,
        String runtimePath,
        String matchedOpenApiPath,

        List<DriftFinding> findings,
        String evaluationMessage) {

    public DriftAnalysisResult {
        Objects.requireNonNull(findings, "findings must not be null");
        findings = List.copyOf(findings);
    }
}