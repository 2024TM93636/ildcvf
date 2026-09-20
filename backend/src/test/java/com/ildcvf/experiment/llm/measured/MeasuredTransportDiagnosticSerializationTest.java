package com.ildcvf.experiment.llm.measured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ildcvf.experiment.llm.groq.TransportDiagnostic;
import com.ildcvf.experiment.llm.groq.TransportDiagnosticCode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasuredTransportDiagnosticSerializationTest {

    @Test
    void futureMeasuredResultSerializesSanitizedDiagnostic()
            throws Exception {

        MeasuredAttempt attempt =
                MeasuredAttemptSchedule.attempts()
                        .stream()
                        .filter(candidate ->
                                candidate.attemptId()
                                        .endsWith("-r3")
                        )
                        .findFirst()
                        .orElseThrow();

        MeasuredAttemptResult base =
                MeasuredTestFixtures.validResult(
                        attempt
                );

        TransportDiagnostic diagnostic =
                new TransportDiagnostic(
                        "TRANSPORT_FAILURE",
                        "com.ildcvf.experiment.llm.groq."
                                + "GroqTransportException",
                        "java.net.UnknownHostException",
                        TransportDiagnosticCode.DNS_FAILURE
                );

        MeasuredAttemptResult enriched =
                new MeasuredAttemptResult(
                        base.attemptId(),
                        base.scenarioId(),
                        base.repetition(),
                        base.evidenceFingerprint(),
                        base.protocolId(),
                        base.configurationLabel(),
                        base.promptVersion(),
                        base.promptSha256(),
                        base.schemaSha256(),
                        base.provider(),
                        base.requestedModel(),
                        base.returnedModel(),
                        base.systemFingerprint(),
                        base.serviceTier(),
                        base.temperature(),
                        base.topP(),
                        base.seed(),
                        base.maxCompletionTokens(),
                        base.timeoutMilliseconds(),
                        base.providerInvocationCount(),
                        base.retryCount(),
                        base.httpStatus(),
                        base.providerCallStatus(),
                        base.providerDiagnostic(),
                        diagnostic,
                        base.rawProviderResponse(),
                        base.assistantContent(),
                        base.proposalParseStatus(),
                        base.parsedProposal(),
                        base.validatorApplicability(),
                        base.validatorAccepted(),
                        base.validatorCodes(),
                        base.groundTruthApplicability(),
                        base.groundTruthFieldComparison(),
                        base.groundTruthExactMatch(),
                        base.hallucinationApplicability(),
                        base.hallucinated(),
                        base.hallucinationReasonCodes(),
                        base.latencyMilliseconds(),
                        base.inputTokens(),
                        base.outputTokens(),
                        base.reasoningTokens(),
                        base.totalTokens(),
                        base.finishReason(),
                        base.timestamp(),
                        true
                );

        ObjectMapper mapper =
                new ObjectMapper();

        JsonNode transport =
                mapper.valueToTree(
                        enriched.transportDiagnostic()
                );

        assertNotNull(transport);

        assertEquals(
                "TRANSPORT_FAILURE",
                transport.get("transportFailureKind")
                        .asText()
        );

        assertEquals(
                "com.ildcvf.experiment.llm.groq."
                        + "GroqTransportException",
                transport.get("transportExceptionClass")
                        .asText()
        );

        assertEquals(
                "java.net.UnknownHostException",
                transport.get("transportCauseClass")
                        .asText()
        );

        assertEquals(
                "DNS_FAILURE",
                transport.get("transportDiagnosticCode")
                        .asText()
        );

        String json =
                mapper.writeValueAsString(
                        enriched.transportDiagnostic()
                );

        assertFalse(json.contains("Authorization: Bearer"));
        assertFalse(json.contains("gsk_"));
    }

    @Test
    void permanentHistoricalResultsRemainReadableJson()
            throws Exception {

        Path repositoryRoot =
                Path.of("..")
                        .toAbsolutePath()
                        .normalize();

        Path measuredRoot =
                repositoryRoot.resolve(
                        Path.of(
                                "experiments",
                                "e6-llm-reliability",
                                "results",
                                "measured"
                        )
                );

        verifyHistorical(
                measuredRoot.resolve(
                        "llm-001-undocumented-operation-r1"
                ).resolve("result.json")
        );

        verifyHistorical(
                measuredRoot.resolve(
                        "llm-001-undocumented-operation-r2"
                ).resolve("result.json")
        );
    }

    private void verifyHistorical(
            Path result
    ) throws Exception {

        assertTrue(
                Files.isRegularFile(result),
                "Historical result missing: " + result
        );

        JsonNode root =
                new ObjectMapper()
                        .readTree(result.toFile());

        assertNotNull(root.get("attemptId"));
        assertNotNull(root.get("providerCallStatus"));

        /*
         * Historical evidence predates F4D.
         * It must not be retrofitted.
         */
        assertFalse(
                root.has("transportDiagnostic")
        );
    }
}