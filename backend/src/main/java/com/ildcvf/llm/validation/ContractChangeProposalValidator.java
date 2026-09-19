package com.ildcvf.llm.validation;

import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeKindMapping;
import com.ildcvf.llm.proposal.ContractChangeProposal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministically validates an untrusted structured contract-change
 * proposal against a bounded GroundedEvidencePackage.
 *
 * <p>This validator does not decide whether a contract should actually be
 * changed. It verifies only that the proposed change remains structurally
 * consistent with and grounded in deterministic evidence produced by the
 * authoritative drift-analysis path.</p>
 *
 * <p>Ordinary invalid proposal content is represented as validation errors
 * rather than exceptions. A null evidence package is programmer misuse of
 * the trusted validation boundary and is rejected with a
 * NullPointerException.</p>
 */
public final class ContractChangeProposalValidator {

    private static final String OPTIONAL_PROPOSED_VALUE = "optional";

    /**
     * Validates one untrusted proposal against one trusted evidence package.
     *
     * @param evidence trusted deterministic grounded evidence
     * @param proposal untrusted structured provider proposal; may be null
     * @return deterministic immutable validation result
     * @throws NullPointerException if evidence is null
     */
    public ProposalValidationResult validate(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal
    ) {
        Objects.requireNonNull(evidence, "evidence must not be null");

        if (proposal == null) {
            return ProposalValidationResult.invalid(
                    List.of(error(
                            ProposalValidationCode.NULL_PROPOSAL,
                            "Proposal must not be null"
                    ))
            );
        }

        List<ProposalValidationError> errors = new ArrayList<>();

        validateRequiredStructure(proposal, errors);
        validateSourceEvent(evidence, proposal, errors);
        validateSourceFinding(evidence, proposal, errors);
        validateDriftType(evidence, proposal, errors);
        validateMethod(evidence, proposal, errors);
        validateRuntimePath(evidence, proposal, errors);
        validateMatchedPath(evidence, proposal, errors);
        validateFieldPath(evidence, proposal, errors);
        validateChangeKind(evidence, proposal, errors);
        validateExpected(evidence, proposal, errors);
        validateObserved(evidence, proposal, errors);
        validateProposedValue(evidence, proposal, errors);

        if (errors.isEmpty()) {
            return ProposalValidationResult.validResult();
        }

        return ProposalValidationResult.invalid(errors);
    }

    private void validateRequiredStructure(
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        requireNonBlank(
                proposal.proposalId(),
                "proposalId",
                errors
        );

        requireNonBlank(
                proposal.sourceTrafficEventId(),
                "sourceTrafficEventId",
                errors
        );

        requireNonBlank(
                proposal.sourceFindingId(),
                "sourceFindingId",
                errors
        );

        if (proposal.driftType() == null) {
            errors.add(error(
                    ProposalValidationCode.MISSING_REQUIRED_VALUE,
                    "driftType must not be null"
            ));
        }

        if (proposal.changeKind() == null) {
            errors.add(error(
                    ProposalValidationCode.MISSING_REQUIRED_VALUE,
                    "changeKind must not be null"
            ));
        }

        requireNonBlank(
                proposal.method(),
                "method",
                errors
        );

        requireNonBlank(
                proposal.runtimePath(),
                "runtimePath",
                errors
        );

        requireNonBlank(
                proposal.rationale(),
                "rationale",
                errors
        );
    }

    private void validateSourceEvent(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (!Objects.equals(
                evidence.trafficEventId(),
                proposal.sourceTrafficEventId()
        )) {
            errors.add(error(
                    ProposalValidationCode.SOURCE_EVENT_MISMATCH,
                    "sourceTrafficEventId does not match grounded evidence"
            ));
        }
    }

    private void validateSourceFinding(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (!Objects.equals(
                evidence.findingId(),
                proposal.sourceFindingId()
        )) {
            errors.add(error(
                    ProposalValidationCode.SOURCE_FINDING_MISMATCH,
                    "sourceFindingId does not match grounded evidence"
            ));
        }
    }

    private void validateDriftType(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (!Objects.equals(
                evidence.driftType(),
                proposal.driftType()
        )) {
            errors.add(error(
                    ProposalValidationCode.DRIFT_TYPE_MISMATCH,
                    "driftType does not match grounded evidence"
            ));
        }
    }

    private void validateMethod(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (!Objects.equals(
                evidence.method(),
                proposal.method()
        )) {
            errors.add(error(
                    ProposalValidationCode.METHOD_MISMATCH,
                    "method does not match grounded evidence"
            ));
        }
    }

    private void validateRuntimePath(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (!Objects.equals(
                evidence.runtimePath(),
                proposal.runtimePath()
        )) {
            errors.add(error(
                    ProposalValidationCode.RUNTIME_PATH_MISMATCH,
                    "runtimePath does not match grounded evidence"
            ));
        }
    }

    private void validateMatchedPath(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (!Objects.equals(
                evidence.matchedOpenApiPath(),
                proposal.matchedOpenApiPath()
        )) {
            errors.add(error(
                    ProposalValidationCode.MATCHED_PATH_MISMATCH,
                    "matchedOpenApiPath does not match grounded evidence"
            ));
        }
    }

    private void validateFieldPath(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (!Objects.equals(
                evidence.fieldPath(),
                proposal.targetFieldPath()
        )) {
            errors.add(error(
                    ProposalValidationCode.FIELD_PATH_MISMATCH,
                    "targetFieldPath does not match grounded evidence"
            ));
        }
    }

    private void validateChangeKind(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (!ContractChangeKindMapping.isAllowed(
                evidence.driftType(),
                proposal.changeKind()
        )) {
            errors.add(error(
                    ProposalValidationCode.CHANGE_KIND_NOT_ALLOWED,
                    "changeKind is not allowed for the grounded driftType"
            ));
        }
    }

    private void validateExpected(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (!Objects.equals(
                evidence.expected(),
                proposal.expected()
        )) {
            errors.add(error(
                    ProposalValidationCode.EXPECTED_VALUE_CONTRADICTION,
                    "expected value contradicts grounded evidence"
            ));
        }
    }

    private void validateObserved(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (!Objects.equals(
                evidence.observed(),
                proposal.observed()
        )) {
            errors.add(error(
                    ProposalValidationCode.OBSERVED_VALUE_CONTRADICTION,
                    "observed value contradicts grounded evidence"
            ));
        }
    }

    private void validateProposedValue(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        ContractChangeKind changeKind = proposal.changeKind();

        if (changeKind == null) {
            return;
        }

        switch (changeKind) {
            case ADD_OPERATION ->
                    validateAddOperationProposedValue(
                            evidence,
                            proposal,
                            errors
                    );

            case RELAX_REQUEST_REQUIRED_FIELD,
                 RELAX_RESPONSE_REQUIRED_FIELD ->
                    validateExactProposedValue(
                            OPTIONAL_PROPOSED_VALUE,
                            proposal.proposedValue(),
                            "Required-constraint relaxation must use "
                                    + "the canonical proposedValue 'optional'",
                            errors
                    );

            case ADD_REQUEST_FIELD,
                 ADD_RESPONSE_FIELD,
                 CHANGE_REQUEST_FIELD_TYPE,
                 CHANGE_RESPONSE_FIELD_TYPE,
                 ADD_REQUEST_ENUM_VALUE,
                 ADD_RESPONSE_ENUM_VALUE,
                 ADD_RESPONSE_STATUS ->
                    validateObservedGroundedProposedValue(
                            evidence,
                            proposal,
                            errors
                    );
        }
    }

    private void validateAddOperationProposedValue(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (isBlank(evidence.method())
                || isBlank(evidence.runtimePath())) {
            errors.add(error(
                    ProposalValidationCode.PROPOSED_VALUE_NOT_GROUNDED,
                    "ADD_OPERATION cannot be grounded because method or "
                            + "runtimePath evidence is missing"
            ));
            return;
        }

        String requiredValue =
                evidence.method() + " " + evidence.runtimePath();

        validateExactProposedValue(
                requiredValue,
                proposal.proposedValue(),
                "ADD_OPERATION proposedValue must exactly identify the "
                        + "grounded runtime operation as '<method> <runtimePath>'",
                errors
        );
    }

    private void validateObservedGroundedProposedValue(
            GroundedEvidencePackage evidence,
            ContractChangeProposal proposal,
            List<ProposalValidationError> errors
    ) {
        if (evidence.observed() == null) {
            errors.add(error(
                    ProposalValidationCode.PROPOSED_VALUE_NOT_GROUNDED,
                    "proposedValue cannot be grounded because the "
                            + "deterministic finding has no observed value"
            ));
            return;
        }

        validateExactProposedValue(
                evidence.observed(),
                proposal.proposedValue(),
                "proposedValue must exactly match the grounded observed value",
                errors
        );
    }

    private void validateExactProposedValue(
            String requiredValue,
            String proposedValue,
            String message,
            List<ProposalValidationError> errors
    ) {
        if (!Objects.equals(requiredValue, proposedValue)) {
            errors.add(error(
                    ProposalValidationCode.PROPOSED_VALUE_NOT_GROUNDED,
                    message
            ));
        }
    }

    private void requireNonBlank(
            String value,
            String fieldName,
            List<ProposalValidationError> errors
    ) {
        if (isBlank(value)) {
            errors.add(error(
                    ProposalValidationCode.MISSING_REQUIRED_VALUE,
                    fieldName + " must not be null or blank"
            ));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ProposalValidationError error(
            ProposalValidationCode code,
            String message
    ) {
        return new ProposalValidationError(code, message);
    }
}