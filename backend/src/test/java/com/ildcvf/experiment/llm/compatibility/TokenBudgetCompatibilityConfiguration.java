package com.ildcvf.experiment.llm.compatibility;

import com.ildcvf.experiment.llm.groq.GroqConfiguration;

public final class TokenBudgetCompatibilityConfiguration {

    public static final String COMPATIBILITY_TEST_ID =
            "compat-001-token2048-llm-001-undocumented-operation";

    public static final String CONFIGURATION_LABEL =
            "compat-token2048-v1";

    public static final String SCENARIO_ID =
            "llm-001-undocumented-operation";

    public static final String PURPOSE =
            "Test whether increasing max_completion_tokens from "
                    + "512 to 2048 changes the Groq structured-output "
                    + "compatibility result.";

    public static final int BASELINE_MAX_COMPLETION_TOKENS =
            512;

    public static final int COMPATIBILITY_MAX_COMPLETION_TOKENS =
            2048;

    public static final String CHANGED_VARIABLE =
            "max_completion_tokens";

    public static final String EXPECTED_EVIDENCE_FINGERPRINT =
            "a856d15d5d95c2410d7f7a4d394753057eaf4f9b544dfbd6fe008a33edb81a8f";

    public static final String PROMPT_VERSION =
            "proposal-v1";

    public static final String EXPECTED_PROMPT_SHA256 =
            "d9ee37eddddff37d75ecdeb933639c356054966f70e2e76ba5694efc7c655947";

    private TokenBudgetCompatibilityConfiguration() {
    }

    public static GroqConfiguration baseline() {
        return GroqConfiguration.frozenPreliminary();
    }

    public static GroqConfiguration compatibility() {
        GroqConfiguration baseline = baseline();

        if (baseline.maxCompletionTokens()
                != BASELINE_MAX_COMPLETION_TOKENS) {
            throw new IllegalStateException(
                    "Frozen baseline max_completion_tokens "
                            + "is no longer 512.");
        }

        return new GroqConfiguration(
                baseline.endpoint(),
                baseline.model(),
                baseline.temperature(),
                baseline.topP(),
                baseline.seed(),
                COMPATIBILITY_MAX_COMPLETION_TOKENS,
                baseline.serviceTier(),
                baseline.timeout()
        );
    }
}
