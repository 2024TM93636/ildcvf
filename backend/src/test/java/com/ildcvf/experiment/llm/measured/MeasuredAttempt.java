package com.ildcvf.experiment.llm.measured;

import java.util.Objects;

public record MeasuredAttempt(
        String attemptId,
        String scenarioId,
        int repetition
) {

    public MeasuredAttempt {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(scenarioId, "scenarioId");

        if (attemptId.isBlank()) {
            throw new IllegalArgumentException("attemptId must not be blank");
        }

        if (scenarioId.isBlank()) {
            throw new IllegalArgumentException("scenarioId must not be blank");
        }

        if (repetition < 1
                || repetition
                > MeasuredProtocolConfiguration.REPETITIONS_PER_SCENARIO) {
            throw new IllegalArgumentException(
                    "repetition must be between 1 and "
                            + MeasuredProtocolConfiguration.REPETITIONS_PER_SCENARIO
            );
        }

        String expectedId = scenarioId + "-r" + repetition;

        if (!attemptId.equals(expectedId)) {
            throw new IllegalArgumentException(
                    "attemptId must equal " + expectedId
            );
        }
    }

    public static MeasuredAttempt of(
            String scenarioId,
            int repetition
    ) {
        Objects.requireNonNull(scenarioId, "scenarioId");

        return new MeasuredAttempt(
                scenarioId + "-r" + repetition,
                scenarioId,
                repetition
        );
    }
}