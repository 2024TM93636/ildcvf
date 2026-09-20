package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.llm.validation.ProposalValidationCode;
import com.ildcvf.llm.validation.ProposalValidationError;

import java.util.List;
import java.util.Objects;

public record ValidatorEvaluation(
        EvaluationApplicability applicability,
        Boolean accepted,
        List<ProposalValidationError> errors,
        List<ProposalValidationCode> codes
) {

    public ValidatorEvaluation {
        Objects.requireNonNull(
                applicability,
                "applicability must not be null");

        errors =
                errors == null
                        ? List.of()
                        : List.copyOf(errors);

        codes =
                codes == null
                        ? List.of()
                        : List.copyOf(codes);

        if (applicability
                == EvaluationApplicability.NOT_APPLICABLE) {

            if (accepted != null
                    || !errors.isEmpty()
                    || !codes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Non-applicable validation "
                                + "must not contain results");
            }
        } else {
            Objects.requireNonNull(
                    accepted,
                    "accepted must be present when applicable");

            if (errors.size() != codes.size()) {
                throw new IllegalArgumentException(
                        "validation error/code counts differ");
            }

            for (int index = 0;
                 index < errors.size();
                 index++) {

                if (errors.get(index).code()
                        != codes.get(index)) {
                    throw new IllegalArgumentException(
                            "validation code order differs");
                }
            }

            if (accepted && !errors.isEmpty()) {
                throw new IllegalArgumentException(
                        "accepted validation cannot contain errors");
            }

            if (!accepted && errors.isEmpty()) {
                throw new IllegalArgumentException(
                        "rejected validation must contain errors");
            }
        }
    }

    public static ValidatorEvaluation notApplicable() {
        return new ValidatorEvaluation(
                EvaluationApplicability.NOT_APPLICABLE,
                null,
                List.of(),
                List.of()
        );
    }

    public static ValidatorEvaluation applicable(
            boolean accepted,
            List<ProposalValidationError> errors
    ) {
        List<ProposalValidationError> immutable =
                List.copyOf(errors);

        return new ValidatorEvaluation(
                EvaluationApplicability.APPLICABLE,
                accepted,
                immutable,
                immutable.stream()
                        .map(ProposalValidationError::code)
                        .toList()
        );
    }
}