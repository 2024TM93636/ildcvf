package com.ildcvf.experiment.llm.compatibility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class GroqTokenBudgetCompatibilityRealCall {

    @Test
    void executeAuthorizedTokenBudgetCompatibilityExactlyOnce()
            throws Exception {

        TokenBudgetCompatibilityRunner runner =
                new TokenBudgetCompatibilityRunner();

        TokenBudgetCompatibilityRunner
                .CompatibilityExecution result =
                runner.executeRealExactlyOnce();

        assertEquals(
                1,
                result.providerInvocationCount());

        assertEquals(
                0,
                result.retryCount());

        assertEquals(
                2048,
                result.providerAttempt()
                        .metadata()
                        .maxCompletionTokens());

        System.out.println(
                "=== E6-D4 TOKEN-BUDGET COMPATIBILITY RESULT ===");

        System.out.println(
                "compatibility test ID = "
                        + TokenBudgetCompatibilityConfiguration
                        .COMPATIBILITY_TEST_ID);

        System.out.println(
                "scenario ID = "
                        + result.scenario()
                        .scenarioId());

        System.out.println(
                "changed variable = "
                        + TokenBudgetCompatibilityConfiguration
                        .CHANGED_VARIABLE);

        System.out.println(
                "baseline max_completion_tokens = 512");

        System.out.println(
                "compatibility max_completion_tokens = 2048");

        System.out.println(
                "provider invocation count = "
                        + result.providerInvocationCount());

        System.out.println(
                "provider status = "
                        + result.providerAttempt()
                        .status());

        System.out.println(
                "HTTP status = "
                        + result.providerAttempt()
                        .metadata()
                        .httpStatus());

        System.out.println(
                "proposal parse status = "
                        + (
                        result.providerAttempt()
                                .proposalParseResult()
                                == null
                                ? null
                                : result.providerAttempt()
                                .proposalParseResult()
                                .status()));

        System.out.println(
                "validator applicability = "
                        + result.evaluation()
                        .validator()
                        .applicability());

        System.out.println(
                "ground truth applicability = "
                        + result.evaluation()
                        .groundTruth()
                        .applicability());

        System.out.println(
                "hallucination applicability = "
                        + result.evaluation()
                        .hallucination()
                        .applicability());

        System.out.println(
                "retry count = "
                        + result.retryCount());

        System.out.println(
                "STOP: E6-D4 compatibility execution is complete.");

        System.out.println(
                "DO NOT execute this command again.");

        System.out.println(
                "DO NOT execute Smoke A, Smoke B, "
                        + "or measured attempts.");
    }
}
