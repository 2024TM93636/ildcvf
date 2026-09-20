package com.ildcvf.experiment.llm.evaluation;

public record TokenMetrics(
        long inputObservedAttemptCount,
        long outputObservedAttemptCount,
        long totalObservedAttemptCount,
        long sumInputTokens,
        long sumOutputTokens,
        long sumTotalTokens,
        Double meanInputTokens,
        Double meanOutputTokens,
        Double meanTotalTokens
) {
}
