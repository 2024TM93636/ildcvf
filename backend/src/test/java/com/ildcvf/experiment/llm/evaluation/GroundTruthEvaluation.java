package com.ildcvf.experiment.llm.evaluation;

import java.util.Objects;

public record GroundTruthEvaluation(
        EvaluationApplicability applicability,
        GroundTruthComparison comparison
) {

    public GroundTruthEvaluation {
        Objects.requireNonNull(
                applicability,
                "applicability must not be null");

        if (applicability
                == EvaluationApplicability.APPLICABLE
                && comparison == null) {
            throw new IllegalArgumentException(
                    "Applicable ground-truth evaluation "
                            + "requires a comparison");
        }

        if (applicability
                == EvaluationApplicability.NOT_APPLICABLE
                && comparison != null) {
            throw new IllegalArgumentException(
                    "Non-applicable ground-truth evaluation "
                            + "must not contain a comparison");
        }
    }

    public static GroundTruthEvaluation notApplicable() {
        return new GroundTruthEvaluation(
                EvaluationApplicability.NOT_APPLICABLE,
                null
        );
    }

    public static GroundTruthEvaluation applicable(
            GroundTruthComparison comparison
    ) {
        return new GroundTruthEvaluation(
                EvaluationApplicability.APPLICABLE,
                Objects.requireNonNull(comparison)
        );
    }
}