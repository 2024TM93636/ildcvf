package com.ildcvf.experiment.llm.groq;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record GroqConfiguration(
        URI endpoint,
        String model,
        double temperature,
        double topP,
        long seed,
        int maxCompletionTokens,
        String serviceTier,
        Duration timeout
) {

    public static final URI FROZEN_ENDPOINT =
            URI.create(
                    "https://api.groq.com/openai/v1/chat/completions");

    public static final String FROZEN_MODEL =
            "openai/gpt-oss-20b";

    public static final double FROZEN_TEMPERATURE = 0.0d;
    public static final double FROZEN_TOP_P = 1.0d;
    public static final long FROZEN_SEED = 20260919L;
    public static final int FROZEN_MAX_COMPLETION_TOKENS = 512;
    public static final String FROZEN_SERVICE_TIER = "on_demand";
    public static final Duration FROZEN_TIMEOUT = Duration.ofSeconds(30);

    public GroqConfiguration {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(serviceTier, "serviceTier must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

        if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException(
                    "endpoint must use HTTPS");
        }

        if (model.isBlank()) {
            throw new IllegalArgumentException(
                    "model must not be blank");
        }

        if (temperature < 0.0d) {
            throw new IllegalArgumentException(
                    "temperature must be non-negative");
        }

        if (topP <= 0.0d || topP > 1.0d) {
            throw new IllegalArgumentException(
                    "topP must be greater than 0 and at most 1");
        }

        if (maxCompletionTokens <= 0) {
            throw new IllegalArgumentException(
                    "maxCompletionTokens must be positive");
        }

        if (serviceTier.isBlank()) {
            throw new IllegalArgumentException(
                    "serviceTier must not be blank");
        }

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(
                    "timeout must be positive");
        }
    }

    public static GroqConfiguration frozenPreliminary() {
        return new GroqConfiguration(
                FROZEN_ENDPOINT,
                FROZEN_MODEL,
                FROZEN_TEMPERATURE,
                FROZEN_TOP_P,
                FROZEN_SEED,
                FROZEN_MAX_COMPLETION_TOKENS,
                FROZEN_SERVICE_TIER,
                FROZEN_TIMEOUT
        );
    }
}