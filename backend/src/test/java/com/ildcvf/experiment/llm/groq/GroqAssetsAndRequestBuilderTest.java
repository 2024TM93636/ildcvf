package com.ildcvf.experiment.llm.groq;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroqAssetsAndRequestBuilderTest {

    @Test
    void frozenPromptAndSchemaVerify() {
        GroqExperimentAssets assets =
                new GroqExperimentAssets();

        assertFalse(
                assets.loadVerifiedPrompt()
                        .isBlank());

        JsonNode schema =
                assets.loadVerifiedSchema();

        assertEquals(
                13,
                schema.get("properties").size());

        assertEquals(
                13,
                schema.get("required").size());

        assertFalse(
                schema.get("additionalProperties")
                        .asBoolean());
    }

    @Test
    void requestContainsFrozenConfigurationSchemaPromptAndEvidence()
            throws Exception {

        LlmReliabilityScenario scenario =
                new LlmScenarioCatalog()
                        .scenarios()
                        .get(1);

        String request =
                new GroqRequestBuilder()
                        .build(
                                GroqConfiguration
                                        .frozenPreliminary(),
                                scenario.evidence());

        ObjectMapper mapper =
                new ObjectMapper();

        JsonNode root =
                mapper.readTree(request);

        assertEquals(
                "openai/gpt-oss-20b",
                root.get("model").asText());

        assertEquals(
                0.0d,
                root.get("temperature").asDouble());

        assertEquals(
                1.0d,
                root.get("top_p").asDouble());

        assertEquals(
                20260919L,
                root.get("seed").asLong());

        assertEquals(
                512,
                root.get(
                                "max_completion_tokens")
                        .asInt());

        assertFalse(
                root.get("stream").asBoolean());

        assertEquals(
                "on_demand",
                root.get("service_tier").asText());

        JsonNode responseFormat =
                root.get("response_format");

        assertEquals(
                "json_schema",
                responseFormat
                        .get("type")
                        .asText());

        JsonNode jsonSchema =
                responseFormat
                        .get("json_schema");

        assertTrue(
                jsonSchema
                        .get("strict")
                        .asBoolean());

        assertEquals(
                "contract_change_proposal",
                jsonSchema
                        .get("name")
                        .asText());

        assertEquals(
                13,
                jsonSchema
                        .get("schema")
                        .get("properties")
                        .size());

        String systemPrompt =
                root.get("messages")
                        .get(0)
                        .get("content")
                        .asText();

        assertTrue(
                systemPrompt.contains(
                        "trusted deterministic ILDCVF drift-evidence"));

        String evidenceJson =
                root.get("messages")
                        .get(1)
                        .get("content")
                        .asText();

        assertTrue(
                evidenceJson.contains(
                        scenario
                                .evidence()
                                .trafficEventId()));

        assertTrue(
                evidenceJson.contains(
                        scenario
                                .evidence()
                                .findingId()));

        assertFalse(
                request.contains(
                        GroqTestFixtures.FAKE_SECRET));

        assertFalse(
                request.contains(
                        "ground-truth.json"));

        assertFalse(
                request.contains(
                        scenario
                                .groundTruth()
                                .toCanonicalProposal(
                                        scenario.scenarioId())
                                .rationale()));
    }

    @Test
    void requestSerializationIsByteForByteDeterministic() {
        LlmReliabilityScenario scenario =
                new LlmScenarioCatalog()
                        .scenarios()
                        .get(4);

        GroqRequestBuilder builder =
                new GroqRequestBuilder();

        GroqConfiguration configuration =
                GroqConfiguration
                        .frozenPreliminary();

        String first =
                builder.build(
                        configuration,
                        scenario.evidence());

        String second =
                builder.build(
                        configuration,
                        scenario.evidence());

        assertEquals(first, second);
    }

    @Test
    void requestDoesNotContainRawTrafficMaterial() {
        LlmReliabilityScenario scenario =
                new LlmScenarioCatalog()
                        .scenarios()
                        .getFirst();

        String request =
                new GroqRequestBuilder()
                        .build(
                                GroqConfiguration
                                        .frozenPreliminary(),
                                scenario.evidence());

        assertFalse(
                request.contains(
                        "\"requestHeaders\""));

        assertFalse(
                request.contains(
                        "\"responseHeaders\""));

        assertFalse(
                request.contains(
                        "\"requestBody\""));

        assertFalse(
                request.contains(
                        "\"responseBody\""));

        assertFalse(
                request.contains(
                        "\"cookies\""));

        assertFalse(
                request.contains(
                        "\"authorization\""));
    }
}