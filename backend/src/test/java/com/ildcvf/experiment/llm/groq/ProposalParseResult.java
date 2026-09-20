package com.ildcvf.experiment.llm.groq;

import com.ildcvf.llm.proposal.ContractChangeProposal;

import java.util.Objects;

public record ProposalParseResult(
        ProposalParseStatus status,
        ContractChangeProposal proposal,
        String diagnostic
) {

    public ProposalParseResult {
        Objects.requireNonNull(
                status,
                "status must not be null");

        if (status == ProposalParseStatus.SUCCESS
                && proposal == null) {
            throw new IllegalArgumentException(
                    "Successful parse requires a proposal.");
        }

        if (status != ProposalParseStatus.SUCCESS
                && proposal != null) {
            throw new IllegalArgumentException(
                    "Failed parse must not contain a proposal.");
        }
    }

    public static ProposalParseResult success(
            ContractChangeProposal proposal
    ) {
        return new ProposalParseResult(
                ProposalParseStatus.SUCCESS,
                Objects.requireNonNull(proposal),
                null
        );
    }

    public static ProposalParseResult failure(
            ProposalParseStatus status,
            String diagnostic
    ) {
        if (status == ProposalParseStatus.SUCCESS) {
            throw new IllegalArgumentException(
                    "Use success() for successful parsing.");
        }

        return new ProposalParseResult(
                status,
                null,
                diagnostic
        );
    }
}