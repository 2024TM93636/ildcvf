package com.ildcvf.experiment.llm;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.evidence.EvidenceFingerprint;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeKindMapping;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.validation.ContractChangeProposalValidator;
import com.ildcvf.llm.validation.ProposalValidationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class E6ExperimentContractTest {

    private static final Path EXPERIMENT_ROOT =
            Path.of(
                    "..",
                    "experiments",
                    "e6-llm-reliability");

    private static final Path GROUND_TRUTH_PATH =
            EXPERIMENT_ROOT
                    .resolve("scenarios")
                    .resolve("ground-truth.json");

    private static final Path PROMPT_PATH =
            EXPERIMENT_ROOT
                    .resolve("prompts")
                    .resolve("proposal-v1.txt");

    private static final Path PROMPT_HASH_PATH =
            EXPERIMENT_ROOT
                    .resolve("prompts")
                    .resolve("proposal-v1.sha256");

    private static final Path SCHEMA_PATH =
            EXPERIMENT_ROOT
                    .resolve("schema")
                    .resolve(
                            "contract-change-proposal.schema.json");

    private static final List<String> EXPECTED_SCENARIO_IDS =
            List.of(
                    "llm-001-undocumented-operation",
                    "llm-002-request-add-field",
                    "llm-003-request-relax-required",
                    "llm-004-request-change-type",
                    "llm-005-request-add-enum",
                    "llm-006-response-add-status",
                    "llm-007-response-relax-required",
                    "llm-008-response-change-type",
                    "llm-009-response-add-enum",
                    "llm-010-response-add-field");

    private static List<LlmReliabilityScenario> scenarios;

    @BeforeAll
    static void setUpCatalog() {
        scenarios =
                new LlmScenarioCatalog()
                        .scenarios();
    }

    @Test
    void scenarioCatalogContainsExactlyTheFrozenTenScenarios() {
        assertEquals(10, scenarios.size());

        List<String> actualIds =
                scenarios.stream()
                        .map(
                                LlmReliabilityScenario::scenarioId)
                        .toList();

        assertEquals(
                EXPECTED_SCENARIO_IDS,
                actualIds);

        assertEquals(
                10,
                new HashSet<>(actualIds).size());
    }

    @Test
    void everyDriftTypeAndChangeKindIsRepresentedExactlyOnce() {
        Set<DriftType> driftTypes =
                scenarios.stream()
                        .map(scenario ->
                                scenario
                                        .groundTruth()
                                        .driftType())
                        .collect(Collectors.toSet());

        Set<ContractChangeKind> changeKinds =
                scenarios.stream()
                        .map(scenario ->
                                scenario
                                        .groundTruth()
                                        .changeKind())
                        .collect(Collectors.toSet());

        assertEquals(
                EnumSet.allOf(DriftType.class),
                driftTypes);

        assertEquals(
                EnumSet.allOf(ContractChangeKind.class),
                changeKinds);

        assertEquals(
                10,
                driftTypes.size());

        assertEquals(
                10,
                changeKinds.size());

        for (LlmReliabilityScenario scenario : scenarios) {
            LlmProposalGroundTruth truth =
                    scenario.groundTruth();

            assertEquals(
                    ContractChangeKindMapping
                            .allowedChangeKind(
                                    truth.driftType()),
                    truth.changeKind());
        }
    }

    @Test
    void groundTruthIsBoundExactlyToEvidenceAndFingerprint() {
        for (LlmReliabilityScenario scenario : scenarios) {
            GroundedEvidencePackage evidence =
                    scenario.evidence();

            LlmProposalGroundTruth truth =
                    scenario.groundTruth();

            assertEquals(
                    EvidenceFingerprint.sha256(evidence),
                    scenario.evidenceFingerprint());

            assertEquals(
                    evidence.trafficEventId(),
                    truth.sourceTrafficEventId());

            assertEquals(
                    evidence.findingId(),
                    truth.sourceFindingId());

            assertEquals(
                    evidence.driftType(),
                    truth.driftType());

            assertEquals(
                    evidence.method(),
                    truth.method());

            assertEquals(
                    evidence.runtimePath(),
                    truth.runtimePath());

            assertEquals(
                    evidence.matchedOpenApiPath(),
                    truth.matchedOpenApiPath());

            assertEquals(
                    evidence.fieldPath(),
                    truth.targetFieldPath());

            assertEquals(
                    evidence.expected(),
                    truth.expected());

            assertEquals(
                    evidence.observed(),
                    truth.observed());

            assertEquals(
                    ContractChangeKindMapping
                            .allowedChangeKind(
                                    evidence.driftType()),
                    truth.changeKind());

            assertEquals(
                    canonicalProposedValue(
                            evidence,
                            truth.changeKind()),
                    truth.proposedValue());
        }
    }

    @Test
    void everyCanonicalGroundTruthProposalPassesM7Validation() {
        ContractChangeProposalValidator validator =
                new ContractChangeProposalValidator();

        for (LlmReliabilityScenario scenario : scenarios) {
            ContractChangeProposal proposal =
                    scenario
                            .groundTruth()
                            .toCanonicalProposal(
                                    scenario.scenarioId());

            ProposalValidationResult result =
                    validator.validate(
                            scenario.evidence(),
                            proposal);

            assertTrue(
                    result.valid(),
                    () ->
                            "Canonical proposal failed for "
                                    + scenario.scenarioId()
                                    + ": "
                                    + result.errors());
        }
    }

    @Test
    void syntheticResponseFieldScenarioIsExplicitAndBaselineIndependent()
            throws IOException {

        LlmReliabilityScenario synthetic =
                scenarios.get(9);

        assertEquals(
                "llm-010-response-add-field",
                synthetic.scenarioId());

        assertTrue(
                synthetic.e6OnlySynthetic());

        assertEquals(
                DriftType.UNEXPECTED_RESPONSE_FIELD,
                synthetic.evidence().driftType());

        assertEquals(
                ContractChangeKind.ADD_RESPONSE_FIELD,
                synthetic.groundTruth().changeKind());

        for (int i = 0; i < 9; i++) {
            assertFalse(
                    scenarios.get(i).e6OnlySynthetic());
        }

        String baseline =
                Files.readString(
                        new LlmScenarioCatalog()
                                .residentBaselinePath(),
                        StandardCharsets.UTF_8);

        assertFalse(
                baseline.contains("experimentalField"),
                "Frozen Resident baseline must not contain "
                        + "the E6 synthetic response field");
    }

    @Test
    void promptIsGenericAndHashMatchesExactBytes()
            throws IOException,
            NoSuchAlgorithmException {

        assertTrue(Files.isRegularFile(PROMPT_PATH));
        assertTrue(Files.isRegularFile(PROMPT_HASH_PATH));

        byte[] promptBytes =
                Files.readAllBytes(PROMPT_PATH);

        String prompt =
                new String(
                        promptBytes,
                        StandardCharsets.UTF_8);

        String expectedHash =
                Files.readString(
                                PROMPT_HASH_PATH,
                                StandardCharsets.UTF_8)
                        .trim();

        String actualHash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest
                                        .getInstance("SHA-256")
                                        .digest(promptBytes));

        assertEquals(
                actualHash,
                expectedHash);

        for (String scenarioId : EXPECTED_SCENARIO_IDS) {
            assertFalse(
                    prompt.contains(scenarioId),
                    () ->
                            "Prompt leaks scenario ID "
                                    + scenarioId);
        }

        assertFalse(
                prompt.contains(
                        "UNDOCUMENTED_OPERATION -> ADD_OPERATION"));

        assertFalse(
                prompt.contains(
                        "MISSING_REQUIRED_REQUEST_FIELD "
                                + "-> RELAX_REQUEST_REQUIRED_FIELD"));

        assertFalse(
                prompt.contains(
                        "UNEXPECTED_RESPONSE_FIELD "
                                + "-> ADD_RESPONSE_FIELD"));

        assertFalse(
                prompt.contains(
                        "\"changeKind\": \"ADD_OPERATION\""));

        assertFalse(
                prompt.contains(
                        "\"proposedValue\": \"optional\""));
    }

    @Test
    void schemaHasExactStructuralContract()
            throws IOException {

        ObjectMapper objectMapper =
                new ObjectMapper();

        JsonNode root =
                objectMapper.readTree(
                        Files.readString(
                                SCHEMA_PATH,
                                StandardCharsets.UTF_8));

        assertEquals(
                "object",
                root.get("type").asText());

        assertFalse(
                root.get("additionalProperties")
                        .asBoolean());

        JsonNode properties =
                root.get("properties");

        JsonNode required =
                root.get("required");

        assertNotNull(properties);
        assertNotNull(required);

        assertEquals(
                13,
                properties.size());

        assertEquals(
                13,
                required.size());

        Set<String> requiredNames =
                new HashSet<>();

        required.forEach(node ->
                requiredNames.add(node.asText()));

        Set<String> propertyNames =
                new HashSet<>(properties.propertyNames());

        assertEquals(
                propertyNames,
                requiredNames);

        assertEquals(
                Arrays.stream(DriftType.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()),
                enumValues(
                        properties.get("driftType")));

        assertEquals(
                Arrays.stream(
                                ContractChangeKind.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()),
                enumValues(
                        properties.get("changeKind")));

        assertNullableString(
                properties.get("matchedOpenApiPath"));

        assertNullableString(
                properties.get("targetFieldPath"));

        assertNullableString(
                properties.get("expected"));

        assertNullableString(
                properties.get("observed"));

        assertNullableString(
                properties.get("proposedValue"));
    }

    @Test
    void committedGroundTruthJsonExactlyMatchesCanonicalJavaCatalog()
            throws IOException {

        String expected =
                E6ArtifactSupport
                        .groundTruthJson(scenarios);

        String actual =
                Files.readString(
                        GROUND_TRUTH_PATH,
                        StandardCharsets.UTF_8);

        assertEquals(
                normalizeNewlines(expected),
                normalizeNewlines(actual));
    }

    private static String canonicalProposedValue(
            GroundedEvidencePackage evidence,
            ContractChangeKind changeKind
    ) {
        return switch (changeKind) {
            case ADD_OPERATION ->
                    evidence.method()
                            + " "
                            + evidence.runtimePath();

            case RELAX_REQUEST_REQUIRED_FIELD,
                 RELAX_RESPONSE_REQUIRED_FIELD ->
                    "optional";

            case ADD_REQUEST_FIELD,
                 CHANGE_REQUEST_FIELD_TYPE,
                 ADD_REQUEST_ENUM_VALUE,
                 ADD_RESPONSE_STATUS,
                 ADD_RESPONSE_FIELD,
                 CHANGE_RESPONSE_FIELD_TYPE,
                 ADD_RESPONSE_ENUM_VALUE ->
                    evidence.observed();
        };
    }

    private static Set<String> enumValues(
            JsonNode property
    ) {
        assertNotNull(property);

        JsonNode enumNode =
                property.get("enum");

        assertNotNull(enumNode);

        Set<String> values =
                new HashSet<>();

        enumNode.forEach(node ->
                values.add(node.asText()));

        return values;
    }

    private static void assertNullableString(
            JsonNode property
    ) {
        assertNotNull(property);

        JsonNode type =
                property.get("type");

        assertNotNull(type);
        assertTrue(type.isArray());

        Set<String> types =
                new HashSet<>();

        type.forEach(node ->
                types.add(node.asText()));

        assertEquals(
                Set.of("string", "null"),
                types);
    }

    private static String normalizeNewlines(
            String value
    ) {
        return Objects.requireNonNull(value)
                .replace("\r\n", "\n");
    }
}