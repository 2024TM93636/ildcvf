package com.ildcvf.experiment.llm.measured;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.ildcvf.experiment.llm.groq.TransportDiagnostic;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonPropertyOrder({
        "attemptId",
        "scenarioId",
        "repetition",
        "evidenceFingerprint",
        "protocolId",
        "configurationLabel",
        "promptVersion",
        "promptSha256",
        "schemaSha256",
        "provider",
        "requestedModel",
        "returnedModel",
        "systemFingerprint",
        "serviceTier",
        "temperature",
        "topP",
        "seed",
        "maxCompletionTokens",
        "timeoutMilliseconds",
        "providerInvocationCount",
        "retryCount",
        "httpStatus",
        "providerCallStatus",
        "providerDiagnostic",
        "transportDiagnostic",
        "rawProviderResponse",
        "assistantContent",
        "proposalParseStatus",
        "parsedProposal",
        "validatorApplicability",
        "validatorAccepted",
        "validatorCodes",
        "groundTruthApplicability",
        "groundTruthFieldComparison",
        "groundTruthExactMatch",
        "hallucinationApplicability",
        "hallucinated",
        "hallucinationReasonCodes",
        "latencyMilliseconds",
        "inputTokens",
        "outputTokens",
        "reasoningTokens",
        "totalTokens",
        "finishReason",
        "timestamp",
        "measuredCampaignAttempt"
})
public record MeasuredAttemptResult(
        String attemptId,
        String scenarioId,
        int repetition,
        String evidenceFingerprint,

        String protocolId,
        String configurationLabel,

        String promptVersion,
        String promptSha256,
        String schemaSha256,

        String provider,
        String requestedModel,
        String returnedModel,
        String systemFingerprint,
        String serviceTier,

        double temperature,
        double topP,
        long seed,
        int maxCompletionTokens,
        int timeoutMilliseconds,

        int providerInvocationCount,
        int retryCount,

        Integer httpStatus,
        String providerCallStatus,
        String providerDiagnostic,
        TransportDiagnostic transportDiagnostic,

        JsonNode rawProviderResponse,
        String assistantContent,

        String proposalParseStatus,
        JsonNode parsedProposal,

        String validatorApplicability,
        Boolean validatorAccepted,
        List<String> validatorCodes,

        String groundTruthApplicability,
        Map<String, Boolean> groundTruthFieldComparison,
        Boolean groundTruthExactMatch,

        String hallucinationApplicability,
        Boolean hallucinated,
        List<String> hallucinationReasonCodes,

        Double latencyMilliseconds,

        Long inputTokens,
        Long outputTokens,
        Long reasoningTokens,
        Long totalTokens,

        String finishReason,
        Instant timestamp,

        boolean measuredCampaignAttempt
) {

    public MeasuredAttemptResult {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(scenarioId, "scenarioId");
        Objects.requireNonNull(
                evidenceFingerprint,
                "evidenceFingerprint"
        );
        Objects.requireNonNull(protocolId, "protocolId");
        Objects.requireNonNull(
                configurationLabel,
                "configurationLabel"
        );
        Objects.requireNonNull(
                promptVersion,
                "promptVersion"
        );
        Objects.requireNonNull(
                promptSha256,
                "promptSha256"
        );
        Objects.requireNonNull(
                schemaSha256,
                "schemaSha256"
        );
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(
                requestedModel,
                "requestedModel"
        );
        Objects.requireNonNull(
                providerCallStatus,
                "providerCallStatus"
        );
        Objects.requireNonNull(timestamp, "timestamp");

        validatorCodes =
                validatorCodes == null
                        ? List.of()
                        : List.copyOf(validatorCodes);

        groundTruthFieldComparison =
                groundTruthFieldComparison == null
                        ? Map.of()
                        : Map.copyOf(
                        groundTruthFieldComparison
                );

        hallucinationReasonCodes =
                hallucinationReasonCodes == null
                        ? List.of()
                        : List.copyOf(
                        hallucinationReasonCodes
                );

        if (!protocolId.equals(
                MeasuredProtocolConfiguration.PROTOCOL_ID
        )) {
            throw new IllegalArgumentException(
                    "Unexpected measured protocol ID."
            );
        }

        if (!configurationLabel.equals(
                MeasuredProtocolConfiguration
                        .CONFIGURATION_LABEL
        )) {
            throw new IllegalArgumentException(
                    "Unexpected measured configuration label."
            );
        }

        if (providerInvocationCount < 0
                || providerInvocationCount > 1) {
            throw new IllegalArgumentException(
                    "providerInvocationCount must be 0 or 1."
            );
        }

        if (retryCount != 0) {
            throw new IllegalArgumentException(
                    "Measured retryCount must be 0."
            );
        }

        if (!measuredCampaignAttempt) {
            throw new IllegalArgumentException(
                    "Measured result must set "
                            + "measuredCampaignAttempt=true."
            );
        }
    }

    /*
     * Compatibility constructor matching the pre-F4D record.
     *
     * Existing offline fixtures and tests continue to compile
     * without manufacturing transport diagnostics.
     */
    public MeasuredAttemptResult(
            String attemptId,
            String scenarioId,
            int repetition,
            String evidenceFingerprint,
            String protocolId,
            String configurationLabel,
            String promptVersion,
            String promptSha256,
            String schemaSha256,
            String provider,
            String requestedModel,
            String returnedModel,
            String systemFingerprint,
            String serviceTier,
            double temperature,
            double topP,
            long seed,
            int maxCompletionTokens,
            int timeoutMilliseconds,
            int providerInvocationCount,
            int retryCount,
            Integer httpStatus,
            String providerCallStatus,
            String providerDiagnostic,
            JsonNode rawProviderResponse,
            String assistantContent,
            String proposalParseStatus,
            JsonNode parsedProposal,
            String validatorApplicability,
            Boolean validatorAccepted,
            List<String> validatorCodes,
            String groundTruthApplicability,
            Map<String, Boolean> groundTruthFieldComparison,
            Boolean groundTruthExactMatch,
            String hallucinationApplicability,
            Boolean hallucinated,
            List<String> hallucinationReasonCodes,
            Double latencyMilliseconds,
            Long inputTokens,
            Long outputTokens,
            Long reasoningTokens,
            Long totalTokens,
            String finishReason,
            Instant timestamp,
            boolean measuredCampaignAttempt
    ) {
        this(
                attemptId,
                scenarioId,
                repetition,
                evidenceFingerprint,
                protocolId,
                configurationLabel,
                promptVersion,
                promptSha256,
                schemaSha256,
                provider,
                requestedModel,
                returnedModel,
                systemFingerprint,
                serviceTier,
                temperature,
                topP,
                seed,
                maxCompletionTokens,
                timeoutMilliseconds,
                providerInvocationCount,
                retryCount,
                httpStatus,
                providerCallStatus,
                providerDiagnostic,
                null,
                rawProviderResponse,
                assistantContent,
                proposalParseStatus,
                parsedProposal,
                validatorApplicability,
                validatorAccepted,
                validatorCodes,
                groundTruthApplicability,
                groundTruthFieldComparison,
                groundTruthExactMatch,
                hallucinationApplicability,
                hallucinated,
                hallucinationReasonCodes,
                latencyMilliseconds,
                inputTokens,
                outputTokens,
                reasoningTokens,
                totalTokens,
                finishReason,
                timestamp,
                measuredCampaignAttempt
        );
    }
}