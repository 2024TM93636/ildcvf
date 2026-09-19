package com.ildcvf.llm.provider;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.support.M7TestFixtures;
import com.ildcvf.llm.validation.ContractChangeProposalValidator;
import com.ildcvf.llm.validation.ProposalValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMProviderReproducibilityTest {

    private final DeterministicFakeLLMProvider provider =
            new DeterministicFakeLLMProvider();

    private final ContractChangeProposalValidator validator =
            new ContractChangeProposalValidator();

    @Test
    void sameEvidenceProducesIdenticalStructuredProposal() {
        GroundedEvidencePackage evidence =
                M7TestFixtures.evidence(
                        DriftType.REQUEST_ENUM_MISMATCH
                );

        ContractChangeProposal first =
                provider.generateProposal(evidence);

        ContractChangeProposal second =
                provider.generateProposal(evidence);

        assertEquals(first, second);
    }

    @Test
    void deterministicFakeProposalPassesDeterministicValidation() {
        GroundedEvidencePackage evidence =
                M7TestFixtures.evidence(
                        DriftType.RESPONSE_ENUM_MISMATCH
                );

        ContractChangeProposal firstProposal =
                provider.generateProposal(evidence);

        ContractChangeProposal secondProposal =
                provider.generateProposal(evidence);

        ProposalValidationResult firstResult =
                validator.validate(
                        evidence,
                        firstProposal
                );

        ProposalValidationResult secondResult =
                validator.validate(
                        evidence,
                        secondProposal
                );

        assertEquals(firstProposal, secondProposal);
        assertEquals(firstResult, secondResult);
        assertTrue(firstResult.valid());
        assertTrue(secondResult.valid());
    }
}