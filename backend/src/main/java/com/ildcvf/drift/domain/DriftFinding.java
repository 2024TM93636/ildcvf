package com.ildcvf.drift.domain;

public record DriftFinding(
        String findingId,
        String trafficEventId,
        long trafficSequence,

        DriftType type,
        DriftSeverity severity,

        String method,
        String runtimePath,
        String matchedOpenApiPath,

        String fieldPath,
        String expected,
        String observed,

        String message) {
}