package com.ildcvf.experiment.llm.groq;

import java.time.Duration;
import java.time.Instant;

public record ProviderAttemptMetadata(
        String provider,
        String requestedModel,
        String returnedModel,
        String systemFingerprint,
        String serviceTier,
        double temperature,
        double topP,
        long seed,
        int maxCompletionTokens,
        String promptSha256,
        String evidenceSha256,
        Integer httpStatus,
        Duration latency,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        String finishReason,
        Instant timestamp
) {
}