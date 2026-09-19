package com.ildcvf.llm.provider;

import com.ildcvf.llm.evidence.EvidenceFingerprint;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeKindMapping;
import com.ildcvf.llm.proposal.ContractChangeProposal;

import java.util.Objects;

/**
 * Deterministic test-only provider used to exercise the M7 provider boundary
 * without network access or a real language model.
 */
final class DeterministicFakeLLMProvider implements LLMProvider {

    @Override
    public ContractChangeProposal generateProposal(
            GroundedEvidencePackage evidence
    ) {
        Objects.requireNonNull(
                evidence,
                "evidence must not be null"
        );

        ContractChangeKind changeKind =
                ContractChangeKindMapping.allowedChangeKind(
                        evidence.driftType()
                );

        return new ContractChangeProposal(
                "fake-" + EvidenceFingerprint.sha256(evidence),
                evidence.trafficEventId(),
                evidence.findingId(),
                evidence.driftType(),
                changeKind,
                evidence.method(),
                evidence.runtimePath(),
                evidence.matchedOpenApiPath(),
                evidence.fieldPath(),
                evidence.expected(),
                evidence.observed(),
                proposedValue(evidence, changeKind),
                "Deterministic test-only proposal"
        );
    }

    private String proposedValue(
            GroundedEvidencePackage evidence,
            ContractChangeKind changeKind
    ) {
        return switch (changeKind) {
            case ADD_OPERATION ->
                    evidence.method()
                            + " "
                            + evidence.runtimePath();

            case RELAX_REQUEST_REQUIRED_FIELD,
                 RELAX_RESPONSE_REQUIRED_FIELD ->
                    "optional";

            case ADD_REQUEST_FIELD,
                 ADD_RESPONSE_FIELD,
                 CHANGE_REQUEST_FIELD_TYPE,
                 CHANGE_RESPONSE_FIELD_TYPE,
                 ADD_REQUEST_ENUM_VALUE,
                 ADD_RESPONSE_ENUM_VALUE,
                 ADD_RESPONSE_STATUS ->
                    evidence.observed();
        };
    }
}