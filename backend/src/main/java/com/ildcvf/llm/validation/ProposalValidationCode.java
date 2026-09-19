package com.ildcvf.llm.validation;

/**
 * Stable machine-readable validation codes produced by the deterministic
 * contract-change proposal validator.
 *
 * <p>The order of enum constants does not define validation order.
 * ContractChangeProposalValidator explicitly controls the deterministic
 * ordering of returned validation errors.</p>
 */
public enum ProposalValidationCode {

    NULL_PROPOSAL,
    MISSING_REQUIRED_VALUE,

    SOURCE_EVENT_MISMATCH,
    SOURCE_FINDING_MISMATCH,

    DRIFT_TYPE_MISMATCH,
    METHOD_MISMATCH,
    RUNTIME_PATH_MISMATCH,
    MATCHED_PATH_MISMATCH,
    FIELD_PATH_MISMATCH,

    CHANGE_KIND_NOT_ALLOWED,

    EXPECTED_VALUE_CONTRADICTION,
    OBSERVED_VALUE_CONTRADICTION,

    PROPOSED_VALUE_NOT_GROUNDED
}