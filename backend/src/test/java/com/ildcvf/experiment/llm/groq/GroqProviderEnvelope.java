package com.ildcvf.experiment.llm.groq;

public record GroqProviderEnvelope(
        String providerResponseId,
        String returnedModel,
        String systemFingerprint,
        String serviceTier,
        String finishReason,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        String assistantContent,
        String rawProviderResponse
) {
}