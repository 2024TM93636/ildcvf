package com.ildcvf.experiment.llm.smoke;

import java.util.Objects;

public record SmokeDefinition(
        String smokeId,
        String scenarioId
) {

    public SmokeDefinition {
        if (smokeId == null || smokeId.isBlank()) {
            throw new IllegalArgumentException(
                    "smokeId must not be blank");
        }

        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException(
                    "scenarioId must not be blank");
        }
    }
}