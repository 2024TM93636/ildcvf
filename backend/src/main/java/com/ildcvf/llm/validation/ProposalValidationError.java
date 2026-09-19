package com.ildcvf.llm.validation;

import java.util.Objects;

/**
 * One deterministic proposal-validation failure.
 *
 * @param code stable machine-readable validation code
 * @param message human-readable diagnostic message
 */
public record ProposalValidationError(
        ProposalValidationCode code,
        String message
) {

    public ProposalValidationError {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");

        if (message.isBlank()) {
            throw new IllegalArgumentException(
                    "message must not be blank"
            );
        }
    }
}