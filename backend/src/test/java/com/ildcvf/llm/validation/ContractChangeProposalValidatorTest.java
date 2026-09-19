package com.ildcvf.llm.validation;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.support.M7TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractChangeProposalValidatorTest {

    private final ContractChangeProposalValidator validator =
            new ContractChangeProposalValidator();

    @Test
    void acceptsValidGroundedProposalForEveryFrozenChangeKind() {
        for (DriftType driftType : DriftType.values()) {
            GroundedEvidencePackage evidence =
                    M7TestFixtures.evidence(driftType);

            ContractChangeProposal proposal =
                    M7TestFixtures.validProposal(evidence);

            ProposalValidationResult result =
                    validator.validate(evidence, proposal);

            assertTrue(
                    result.valid(),
                    () -> "Expected valid proposal for "
                            + driftType
                            + " but received "
                            + result.errors()
            );
            assertTrue(result.errors().isEmpty());
        }

        assertEquals(10, DriftType.values().length);
    }

    @Test
    void nullEvidenceIsProgrammerMisuse() {
        GroundedEvidencePackage evidence =
                M7TestFixtures.evidence(
                        DriftType.REQUEST_TYPE_MISMATCH
                );

        ContractChangeProposal proposal =
                M7TestFixtures.validProposal(evidence);

        assertThrows(
                NullPointerException.class,
                () -> validator.validate(null, proposal)
        );
    }

    @Test
    void nullProposalReturnsStructuredInvalidResult() {
        GroundedEvidencePackage evidence =
                M7TestFixtures.evidence(
                        DriftType.REQUEST_TYPE_MISMATCH
                );

        ProposalValidationResult result =
                validator.validate(evidence, null);

        assertFalse(result.valid());
        assertEquals(
                List.of(ProposalValidationCode.NULL_PROPOSAL),
                codes(result)
        );
    }

    @Test
    void accumulatesMissingRequiredStructureDeterministically() {
        GroundedEvidencePackage evidence =
                M7TestFixtures.evidence(
                        DriftType.REQUEST_TYPE_MISMATCH
                );

        ContractChangeProposal proposal =
                new ContractChangeProposal(
                        " ",
                        null,
                        "",
                        null,
                        null,
                        null,
                        " ",
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        evidence.expected(),
                        evidence.observed(),
                        evidence.observed(),
                        ""
                );

        ProposalValidationResult result =
                validator.validate(evidence, proposal);

        assertFalse(result.valid());

        assertEquals(
                List.of(
                        ProposalValidationCode.MISSING_REQUIRED_VALUE,
                        ProposalValidationCode.MISSING_REQUIRED_VALUE,
                        ProposalValidationCode.MISSING_REQUIRED_VALUE,
                        ProposalValidationCode.MISSING_REQUIRED_VALUE,
                        ProposalValidationCode.MISSING_REQUIRED_VALUE,
                        ProposalValidationCode.MISSING_REQUIRED_VALUE,
                        ProposalValidationCode.MISSING_REQUIRED_VALUE,
                        ProposalValidationCode.MISSING_REQUIRED_VALUE,
                        ProposalValidationCode.SOURCE_EVENT_MISMATCH,
                        ProposalValidationCode.SOURCE_FINDING_MISMATCH,
                        ProposalValidationCode.DRIFT_TYPE_MISMATCH,
                        ProposalValidationCode.METHOD_MISMATCH,
                        ProposalValidationCode.RUNTIME_PATH_MISMATCH,
                        ProposalValidationCode.CHANGE_KIND_NOT_ALLOWED
                ),
                codes(result)
        );
    }

    @Test
    void rejectsSourceEventRedirection() {
        assertSingleTargetedError(
                proposalWith(
                        baseEvidence(),
                        "different-event",
                        baseEvidence().findingId(),
                        baseEvidence().driftType(),
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        baseEvidence().method(),
                        baseEvidence().runtimePath(),
                        baseEvidence().matchedOpenApiPath(),
                        baseEvidence().fieldPath(),
                        baseEvidence().expected(),
                        baseEvidence().observed(),
                        baseEvidence().observed()
                ),
                ProposalValidationCode.SOURCE_EVENT_MISMATCH
        );
    }

    @Test
    void rejectsSourceFindingRedirection() {
        GroundedEvidencePackage evidence = baseEvidence();

        assertSingleTargetedError(
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        "different-finding",
                        evidence.driftType(),
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        evidence.method(),
                        evidence.runtimePath(),
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        evidence.expected(),
                        evidence.observed(),
                        evidence.observed()
                ),
                ProposalValidationCode.SOURCE_FINDING_MISMATCH
        );
    }

    @Test
    void rejectsDriftTypeRedirection() {
        GroundedEvidencePackage evidence = baseEvidence();

        ContractChangeProposal proposal =
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        DriftType.RESPONSE_TYPE_MISMATCH,
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        evidence.method(),
                        evidence.runtimePath(),
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        evidence.expected(),
                        evidence.observed(),
                        evidence.observed()
                );

        ProposalValidationResult result =
                validator.validate(evidence, proposal);

        assertTrue(
                codes(result).contains(
                        ProposalValidationCode.DRIFT_TYPE_MISMATCH
                )
        );
    }

    @Test
    void rejectsMethodRedirection() {
        GroundedEvidencePackage evidence = baseEvidence();

        assertSingleTargetedError(
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        "PUT",
                        evidence.runtimePath(),
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        evidence.expected(),
                        evidence.observed(),
                        evidence.observed()
                ),
                ProposalValidationCode.METHOD_MISMATCH
        );
    }

    @Test
    void rejectsRuntimePathRedirection() {
        GroundedEvidencePackage evidence = baseEvidence();

        assertSingleTargetedError(
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        evidence.method(),
                        "/api/unrelated",
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        evidence.expected(),
                        evidence.observed(),
                        evidence.observed()
                ),
                ProposalValidationCode.RUNTIME_PATH_MISMATCH
        );
    }

    @Test
    void rejectsMatchedPathRedirection() {
        GroundedEvidencePackage evidence = baseEvidence();

        assertSingleTargetedError(
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        evidence.method(),
                        evidence.runtimePath(),
                        "/api/unrelated",
                        evidence.fieldPath(),
                        evidence.expected(),
                        evidence.observed(),
                        evidence.observed()
                ),
                ProposalValidationCode.MATCHED_PATH_MISMATCH
        );
    }

    @Test
    void rejectsFieldPathRedirection() {
        GroundedEvidencePackage evidence = baseEvidence();

        assertSingleTargetedError(
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        evidence.method(),
                        evidence.runtimePath(),
                        evidence.matchedOpenApiPath(),
                        "$.unrelated",
                        evidence.expected(),
                        evidence.observed(),
                        evidence.observed()
                ),
                ProposalValidationCode.FIELD_PATH_MISMATCH
        );
    }

    @Test
    void rejectsIncompatibleChangeKind() {
        GroundedEvidencePackage evidence = baseEvidence();

        ContractChangeProposal proposal =
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.ADD_RESPONSE_FIELD,
                        evidence.method(),
                        evidence.runtimePath(),
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        evidence.expected(),
                        evidence.observed(),
                        evidence.observed()
                );

        ProposalValidationResult result =
                validator.validate(evidence, proposal);

        assertTrue(
                codes(result).contains(
                        ProposalValidationCode.CHANGE_KIND_NOT_ALLOWED
                )
        );
    }

    @Test
    void rejectsExpectedEvidenceContradiction() {
        GroundedEvidencePackage evidence = baseEvidence();

        assertSingleTargetedError(
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        evidence.method(),
                        evidence.runtimePath(),
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        "invented-expected",
                        evidence.observed(),
                        evidence.observed()
                ),
                ProposalValidationCode.EXPECTED_VALUE_CONTRADICTION
        );
    }

    @Test
    void rejectsObservedEvidenceContradiction() {
        GroundedEvidencePackage evidence = baseEvidence();

        ContractChangeProposal proposal =
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        evidence.method(),
                        evidence.runtimePath(),
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        evidence.expected(),
                        "invented-observed",
                        evidence.observed()
                );

        ProposalValidationResult result =
                validator.validate(evidence, proposal);

        assertTrue(
                codes(result).contains(
                        ProposalValidationCode.OBSERVED_VALUE_CONTRADICTION
                )
        );
    }

    @Test
    void rejectsInventedProposedValue() {
        GroundedEvidencePackage evidence = baseEvidence();

        ContractChangeProposal proposal =
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        evidence.method(),
                        evidence.runtimePath(),
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        evidence.expected(),
                        evidence.observed(),
                        "boolean"
                );

        ProposalValidationResult result =
                validator.validate(evidence, proposal);

        assertTrue(
                codes(result).contains(
                        ProposalValidationCode.PROPOSED_VALUE_NOT_GROUNDED
                )
        );
    }

    @Test
    void requiredFieldRelaxationAcceptsOnlyCanonicalOptional() {
        GroundedEvidencePackage evidence =
                M7TestFixtures.evidence(
                        DriftType.MISSING_REQUIRED_REQUEST_FIELD
                );

        ContractChangeProposal valid =
                M7TestFixtures.validProposal(evidence);

        assertTrue(
                validator.validate(evidence, valid).valid()
        );

        ContractChangeProposal invalid =
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.RELAX_REQUEST_REQUIRED_FIELD,
                        evidence.method(),
                        evidence.runtimePath(),
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        evidence.expected(),
                        evidence.observed(),
                        "required"
                );

        assertEquals(
                List.of(
                        ProposalValidationCode.PROPOSED_VALUE_NOT_GROUNDED
                ),
                codes(validator.validate(evidence, invalid))
        );
    }

    @Test
    void addOperationAcceptsOnlyExactGroundedMethodAndRuntimePath() {
        GroundedEvidencePackage evidence =
                M7TestFixtures.evidence(
                        DriftType.UNDOCUMENTED_OPERATION
                );

        ContractChangeProposal valid =
                M7TestFixtures.validProposal(evidence);

        assertTrue(
                validator.validate(evidence, valid).valid()
        );

        ContractChangeProposal invalid =
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.ADD_OPERATION,
                        evidence.method(),
                        evidence.runtimePath(),
                        null,
                        null,
                        evidence.expected(),
                        evidence.observed(),
                        "POST /api/invented"
                );

        assertEquals(
                List.of(
                        ProposalValidationCode.PROPOSED_VALUE_NOT_GROUNDED
                ),
                codes(validator.validate(evidence, invalid))
        );
    }

    @Test
    void failsClosedWhenObservedGroundingIsUnavailable() {
        GroundedEvidencePackage evidence =
                M7TestFixtures.evidence(
                        DriftType.REQUEST_TYPE_MISMATCH,
                        "$.status",
                        "integer",
                        null
                );

        ContractChangeProposal proposal =
                proposalWith(
                        evidence,
                        evidence.trafficEventId(),
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        evidence.method(),
                        evidence.runtimePath(),
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        evidence.expected(),
                        null,
                        null
                );

        assertEquals(
                List.of(
                        ProposalValidationCode.PROPOSED_VALUE_NOT_GROUNDED
                ),
                codes(validator.validate(evidence, proposal))
        );
    }

    @Test
    void preservesDeterministicMultiErrorOrdering() {
        GroundedEvidencePackage evidence = baseEvidence();

        ContractChangeProposal proposal =
                new ContractChangeProposal(
                        "proposal-defective",
                        "wrong-event",
                        "wrong-finding",
                        DriftType.RESPONSE_ENUM_MISMATCH,
                        ContractChangeKind.ADD_OPERATION,
                        "DELETE",
                        "/wrong-runtime",
                        "/wrong-template",
                        "$.wrong",
                        "wrong-expected",
                        "wrong-observed",
                        "wrong-proposed",
                        "Controlled invalid proposal"
                );

        ProposalValidationResult result =
                validator.validate(evidence, proposal);

        assertEquals(
                List.of(
                        ProposalValidationCode.SOURCE_EVENT_MISMATCH,
                        ProposalValidationCode.SOURCE_FINDING_MISMATCH,
                        ProposalValidationCode.DRIFT_TYPE_MISMATCH,
                        ProposalValidationCode.METHOD_MISMATCH,
                        ProposalValidationCode.RUNTIME_PATH_MISMATCH,
                        ProposalValidationCode.MATCHED_PATH_MISMATCH,
                        ProposalValidationCode.FIELD_PATH_MISMATCH,
                        ProposalValidationCode.CHANGE_KIND_NOT_ALLOWED,
                        ProposalValidationCode.EXPECTED_VALUE_CONTRADICTION,
                        ProposalValidationCode.OBSERVED_VALUE_CONTRADICTION,
                        ProposalValidationCode.PROPOSED_VALUE_NOT_GROUNDED
                ),
                codes(result)
        );
    }

    @Test
    void validationErrorListIsImmutable() {
        GroundedEvidencePackage evidence = baseEvidence();

        ContractChangeProposal proposal =
                proposalWith(
                        evidence,
                        "wrong-event",
                        evidence.findingId(),
                        evidence.driftType(),
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                        evidence.method(),
                        evidence.runtimePath(),
                        evidence.matchedOpenApiPath(),
                        evidence.fieldPath(),
                        evidence.expected(),
                        evidence.observed(),
                        evidence.observed()
                );

        ProposalValidationResult result =
                validator.validate(evidence, proposal);

        assertThrows(
                UnsupportedOperationException.class,
                () -> result.errors().add(
                        new ProposalValidationError(
                                ProposalValidationCode.NULL_PROPOSAL,
                                "mutation"
                        )
                )
        );
    }

    private GroundedEvidencePackage baseEvidence() {
        return M7TestFixtures.evidence(
                DriftType.REQUEST_TYPE_MISMATCH
        );
    }

    private void assertSingleTargetedError(
            ContractChangeProposal proposal,
            ProposalValidationCode expectedCode
    ) {
        ProposalValidationResult result =
                validator.validate(baseEvidence(), proposal);

        assertFalse(result.valid());
        assertEquals(
                List.of(expectedCode),
                codes(result)
        );
    }

    private List<ProposalValidationCode> codes(
            ProposalValidationResult result
    ) {
        return result.errors()
                .stream()
                .map(ProposalValidationError::code)
                .toList();
    }

    private ContractChangeProposal proposalWith(
            GroundedEvidencePackage evidence,
            String sourceTrafficEventId,
            String sourceFindingId,
            DriftType driftType,
            ContractChangeKind changeKind,
            String method,
            String runtimePath,
            String matchedOpenApiPath,
            String targetFieldPath,
            String expected,
            String observed,
            String proposedValue
    ) {
        return new ContractChangeProposal(
                "proposal-controlled",
                sourceTrafficEventId,
                sourceFindingId,
                driftType,
                changeKind,
                method,
                runtimePath,
                matchedOpenApiPath,
                targetFieldPath,
                expected,
                observed,
                proposedValue,
                "Controlled validator test proposal"
        );
    }
}