package com.ildcvf.llm.provider;

import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.proposal.ContractChangeProposal;

/**
 * Vendor-neutral boundary for future LLM-assisted contract-change proposal
 * generation.
 *
 * <p>The deterministic M7 foundation defines only this abstraction. A real
 * provider integration is intentionally outside the current milestone.</p>
 */
public interface LLMProvider {

    /**
     * Generates one structured proposal from one bounded grounded evidence
     * package.
     *
     * <p>The returned proposal is untrusted until it passes deterministic
     * ContractChangeProposalValidator validation.</p>
     *
     * @param evidence deterministic bounded evidence
     * @return structured, still-untrusted proposal
     */
    ContractChangeProposal generateProposal(GroundedEvidencePackage evidence);
}
