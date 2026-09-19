package com.ildcvf.llm.proposal;

import com.ildcvf.drift.domain.DriftType;

/**
 * Immutable structured contract-change proposal produced by an LLMProvider.
 *
 * <p>This object is a proposal only. Constructing it does not validate,
 * approve, persist, or apply a contract change.</p>
 *
 * <p>The free-form rationale is intended for human review and is not treated
 * as authoritative evidence by deterministic validation.</p>
 */
public record ContractChangeProposal(
        String proposalId,
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
        String proposedValue,
        String rationale
) {
}
