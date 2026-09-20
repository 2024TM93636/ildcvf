package com.ildcvf.experiment.llm.evaluation;

import java.util.Objects;
import java.util.Set;

public record AttemptIdentity(
        String scenarioId,
        int repetition
) {

    private static final Set<String> FROZEN_SCENARIO_IDS =
            Set.of(
                    "llm-001-undocumented-operation",
                    "llm-002-request-add-field",
                    "llm-003-request-relax-required",
                    "llm-004-request-change-type",
                    "llm-005-request-add-enum",
                    "llm-006-response-add-status",
                    "llm-007-response-relax-required",
                    "llm-008-response-change-type",
                    "llm-009-response-add-enum",
                    "llm-010-response-add-field"
            );

    public AttemptIdentity {
        Objects.requireNonNull(
                scenarioId,
                "scenarioId must not be null");

        if (!FROZEN_SCENARIO_IDS.contains(scenarioId)) {
            throw new IllegalArgumentException(
                    "Unknown frozen E6 scenario: " + scenarioId);
        }

        if (repetition < 1 || repetition > 4) {
            throw new IllegalArgumentException(
                    "repetition must be between 1 and 4");
        }
    }

    public String attemptId() {
        return scenarioId + "-r" + repetition;
    }
}