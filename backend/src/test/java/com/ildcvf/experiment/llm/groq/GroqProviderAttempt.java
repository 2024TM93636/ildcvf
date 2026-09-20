package com.ildcvf.experiment.llm.groq;

import java.util.Objects;

public record GroqProviderAttempt(
        ProviderCallStatus status,
        ProviderAttemptMetadata metadata,
        GroqProviderEnvelope envelope,
        ProposalParseResult proposalParseResult,
        String diagnostic,
        TransportDiagnostic transportDiagnostic
) {

    public GroqProviderAttempt {
        Objects.requireNonNull(
                status,
                "status must not be null"
        );

        Objects.requireNonNull(
                metadata,
                "metadata must not be null"
        );
    }

    /*
     * Compatibility constructor.
     *
     * Existing non-transport synthetic/provider-result construction
     * remains source-compatible and receives no transport diagnostic.
     */
    public GroqProviderAttempt(
            ProviderCallStatus status,
            ProviderAttemptMetadata metadata,
            GroqProviderEnvelope envelope,
            ProposalParseResult proposalParseResult,
            String diagnostic
    ) {
        this(
                status,
                metadata,
                envelope,
                proposalParseResult,
                diagnostic,
                null
        );
    }
}