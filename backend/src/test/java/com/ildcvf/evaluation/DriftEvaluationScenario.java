package com.ildcvf.evaluation;

import com.ildcvf.drift.domain.EvaluationStatus;
import com.ildcvf.traffic.domain.TrafficEvent;

import java.util.List;
import java.util.Objects;

public record DriftEvaluationScenario(
        String scenarioId,
        String description,
        TrafficEvent event,
        EvaluationStatus expectedEvaluationStatus,
        List<DriftFindingKey> expectedFindingKeys
) {
    public DriftEvaluationScenario {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException(
                    "scenarioId must not be blank");
        }

        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(
                expectedEvaluationStatus,
                "expectedEvaluationStatus");
        Objects.requireNonNull(
                expectedFindingKeys,
                "expectedFindingKeys");

        expectedFindingKeys = List.copyOf(expectedFindingKeys);
    }
}
