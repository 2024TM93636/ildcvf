package com.ildcvf.evaluation;

import com.ildcvf.drift.domain.EvaluationStatus;

import java.util.List;
import java.util.Objects;

public record DriftEvaluationScenarioResult(
        String scenarioId,
        EvaluationStatus expectedEvaluationStatus,
        EvaluationStatus actualEvaluationStatus,

        List<DriftFindingKey> expectedFindingKeys,
        List<DriftFindingKey> actualFindingKeys,

        List<DriftFindingKey> truePositiveKeys,
        List<DriftFindingKey> falsePositiveKeys,
        List<DriftFindingKey> falseNegativeKeys,

        String detectorEvaluationMessage
) {
    public DriftEvaluationScenarioResult {
        Objects.requireNonNull(scenarioId, "scenarioId");
        Objects.requireNonNull(
                expectedEvaluationStatus,
                "expectedEvaluationStatus");
        Objects.requireNonNull(
                actualEvaluationStatus,
                "actualEvaluationStatus");

        expectedFindingKeys = List.copyOf(expectedFindingKeys);
        actualFindingKeys = List.copyOf(actualFindingKeys);
        truePositiveKeys = List.copyOf(truePositiveKeys);
        falsePositiveKeys = List.copyOf(falsePositiveKeys);
        falseNegativeKeys = List.copyOf(falseNegativeKeys);
    }
}
