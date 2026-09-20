package com.ildcvf.experiment.llm.evaluation;

public record LatencyMetrics(
        long observedAttemptCount,
        Double meanMilliseconds,
        Double p50Milliseconds,
        Double p95Milliseconds,
        Double p99Milliseconds
) {
}
