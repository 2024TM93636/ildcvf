package com.ildcvf.llm.proposal;

/**
 * Conceptual lifecycle states for a generated contract-change proposal.
 *
 * <p>The M7 foundation does not implement a workflow engine or persistence
 * for these states. Keeping lifecycle state separate preserves
 * ContractChangeProposal as immutable provider output.</p>
 */
public enum ProposalLifecycleStatus {

    GENERATED,
    VALIDATED,
    REJECTED_BY_VALIDATION,
    APPROVED,
    REJECTED_BY_HUMAN,
    APPLIED
}
