package com.ildcvf.experiment.llm.evaluation;

import java.util.List;
import java.util.Objects;

public record HallucinationEvaluation(
        EvaluationApplicability applicability,
        Boolean hallucinated,
        List<HallucinationReasonCode> reasonCodes
) {

    public HallucinationEvaluation {
        Objects.requireNonNull(
                applicability,
                "applicability must not be null");

        reasonCodes =
                reasonCodes == null
                        ? List.of()
                        : List.copyOf(reasonCodes);

        if (applicability
                == EvaluationApplicability.NOT_APPLICABLE) {

            if (hallucinated != null
                    || !reasonCodes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Non-applicable hallucination result "
                                + "must not contain classification");
            }
        } else {
            Objects.requireNonNull(
                    hallucinated,
                    "hallucinated must be present when applicable");

            if (hallucinated != !reasonCodes.isEmpty()) {
                throw new IllegalArgumentException(
                        "hallucinated flag and reasons differ");
            }
        }
    }

    public static HallucinationEvaluation notApplicable() {
        return new HallucinationEvaluation(
                EvaluationApplicability.NOT_APPLICABLE,
                null,
                List.of()
        );
    }

    public static HallucinationEvaluation applicable(
            List<HallucinationReasonCode> reasons
    ) {
        List<HallucinationReasonCode> immutable =
                List.copyOf(reasons);

        return new HallucinationEvaluation(
                EvaluationApplicability.APPLICABLE,
                !immutable.isEmpty(),
                immutable
        );
    }
}
