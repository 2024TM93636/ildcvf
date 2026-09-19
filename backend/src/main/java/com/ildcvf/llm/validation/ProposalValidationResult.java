package com.ildcvf.llm.validation;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of deterministic contract-change proposal validation.
 *
 * <p>Validity is derived exclusively from whether validation errors exist,
 * preventing contradictory states such as valid=true with a non-empty error
 * list.</p>
 */
public record ProposalValidationResult(
        List<ProposalValidationError> errors
) {

    public ProposalValidationResult {
        Objects.requireNonNull(errors, "errors must not be null");
        errors = List.copyOf(errors);
    }

    /**
     * Returns true only when no deterministic validation errors exist.
     */
    public boolean valid() {
        return errors.isEmpty();
    }

    /**
     * Creates a successful validation result.
     */
    public static ProposalValidationResult validResult() {
        return new ProposalValidationResult(List.of());
    }

    /**
     * Creates an immutable invalid result from the supplied errors.
     */
    public static ProposalValidationResult invalid(
            List<ProposalValidationError> errors
    ) {
        Objects.requireNonNull(errors, "errors must not be null");

        if (errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid validation result must contain at least one error"
            );
        }

        return new ProposalValidationResult(errors);
    }
}