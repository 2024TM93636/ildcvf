package com.ildcvf.experiment.llm.measured;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MeasuredTestFixtures {

    private MeasuredTestFixtures() {
    }

    static Map<String, String> authorizedEnvironment() {
        return Map.of(
                MeasuredCampaignAuthorization
                        .API_KEY_ENVIRONMENT_VARIABLE,
                "synthetic-test-api-key",
                MeasuredCampaignAuthorization
                        .AUTHORIZATION_ENVIRONMENT_VARIABLE,
                "synthetic-controller-authorization"
        );
    }

    static String expectedAuthorization() {
        return "synthetic-controller-authorization";
    }

    static MeasuredAttemptResult validResult(
            MeasuredAttempt attempt
    ) {
        return result(
                attempt,
                "SUCCESS",
                "SUCCESS",
                "APPLICABLE",
                true,
                List.of(),
                "APPLICABLE",
                true,
                "APPLICABLE",
                false,
                List.of()
        );
    }

    static MeasuredAttemptResult result(
            MeasuredAttempt attempt,
            String providerCallStatus,
            String proposalParseStatus,
            String validatorApplicability,
            Boolean validatorAccepted,
            List<String> validatorCodes,
            String groundTruthApplicability,
            Boolean groundTruthExactMatch,
            String hallucinationApplicability,
            Boolean hallucinated,
            List<String> hallucinationReasonCodes
    ) {
        ObjectNode raw =
                JsonNodeFactory.instance.objectNode();
        raw.put("synthetic", true);

        ObjectNode proposal =
                JsonNodeFactory.instance.objectNode();
        proposal.put(
                "sourceTrafficEventId",
                "synthetic-event"
        );

        Map<String, Boolean> fieldComparison =
                new LinkedHashMap<>();

        for (String field
                : MeasuredProtocolConfiguration.SCORED_FIELDS) {
            fieldComparison.put(
                    field,
                    Boolean.TRUE.equals(
                            groundTruthExactMatch
                    )
            );
        }

        return new MeasuredAttemptResult(
                attempt.attemptId(),
                attempt.scenarioId(),
                attempt.repetition(),
                "synthetic-evidence-fingerprint",

                MeasuredProtocolConfiguration.PROTOCOL_ID,
                MeasuredProtocolConfiguration.CONFIGURATION_LABEL,

                MeasuredProtocolConfiguration.PROMPT_VERSION,
                MeasuredProtocolConfiguration.PROMPT_SHA256,
                MeasuredProtocolConfiguration.SCHEMA_SHA256,

                MeasuredProtocolConfiguration.PROVIDER,
                MeasuredProtocolConfiguration.REQUESTED_MODEL,
                MeasuredProtocolConfiguration.REQUESTED_MODEL,
                "synthetic-system-fingerprint",
                MeasuredProtocolConfiguration.SERVICE_TIER,

                MeasuredProtocolConfiguration.TEMPERATURE,
                MeasuredProtocolConfiguration.TOP_P,
                MeasuredProtocolConfiguration.SEED,
                MeasuredProtocolConfiguration.MAX_COMPLETION_TOKENS,
                MeasuredProtocolConfiguration.TIMEOUT_MILLISECONDS,

                1,
                0,

                "SUCCESS".equals(providerCallStatus)
                        ? 200
                        : 500,
                providerCallStatus,
                "SUCCESS".equals(providerCallStatus)
                        ? null
                        : "synthetic provider failure",

                raw,
                "{\"synthetic\":true}",

                proposalParseStatus,
                "SUCCESS".equals(proposalParseStatus)
                        ? proposal
                        : null,

                validatorApplicability,
                validatorAccepted,
                validatorCodes,

                groundTruthApplicability,
                fieldComparison,
                groundTruthExactMatch,

                hallucinationApplicability,
                hallucinated,
                hallucinationReasonCodes,

                12.5,

                100L,
                20L,
                5L,
                120L,

                "stop",
                Instant.parse("2026-09-20T00:00:00Z"),

                true
        );
    }
}