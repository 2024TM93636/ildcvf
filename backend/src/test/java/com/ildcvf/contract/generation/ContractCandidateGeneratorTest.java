package com.ildcvf.contract.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import io.swagger.v3.core.util.Yaml;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class ContractCandidateGeneratorTest {
    private static final String PATH = "../datasets/openapi/baseline/resident-api-v1.yaml";
    private ContractChangeProposal proposal(String field, String value) {
        return new ContractChangeProposal("fixture", "traffic-fixture", "finding-fixture",
                DriftType.RESPONSE_TYPE_MISMATCH, ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                "GET", "/api/residents/1", "/api/residents/{id}", field,
                "integer", "string", value, "Test fixture");
    }

    private ContractChangeProposal proposalWith(
            ContractChangeKind changeKind,
            String method,
            String matchedPath,
            String targetField,
            String expected,
            String observed,
            String proposedValue) {

        return new ContractChangeProposal(
                "fixture",
                "traffic-fixture",
                "finding-fixture",
                DriftType.RESPONSE_TYPE_MISMATCH,
                changeKind,
                method,
                "/api/residents/1",
                matchedPath,
                targetField,
                expected,
                observed,
                proposedValue,
                "Test fixture"
        );
    }

    @Test void transformsOnlyLocalResponseAndPreservesOriginalBytes() throws Exception {
        Path path = Path.of(PATH);
        byte[] before = Files.readAllBytes(path);
        var generator = new ContractCandidateGenerator(path.toString());
        var a = generator.generate(proposal("id", "string"));
        var b = generator.generate(proposal("id", "string"));
        assertTrue(a.validation().valid());
        assertEquals(a.openapiYaml(), b.openapiYaml());
        assertEquals(a.documentSha256(), b.documentSha256());
        assertArrayEquals(before, Files.readAllBytes(path));
        assertEquals(sha(before), a.baselineSha256());
        assertEquals(sha(a.openapiYaml().getBytes(StandardCharsets.UTF_8)), a.documentSha256());
        JsonNode doc = Yaml.mapper().readTree(a.openapiYaml());
        JsonNode shared = doc.path("components").path("schemas").path("ResidentResponse");

        JsonNode baselineDoc = Yaml.mapper().readTree(before);

        assertEquals(
                baselineDoc.path("components").path("schemas").path("ResidentResponse"),
                shared,
                "Original shared ResidentResponse must remain unchanged"
        );

        assertEquals(
                baselineDoc.path("components").path("schemas"),
                doc.path("components").path("schemas"),
                "All shared component schemas must remain unchanged"
        );

        JsonNode sharedId = shared.path("properties").path("id");
        assertEquals("integer", sharedId.path("type").asText());
        assertEquals("int64", sharedId.path("format").asText());
        assertEquals(1, sharedId.path("minimum").asInt());
        JsonNode local = doc.path("paths").path("/api/residents/{id}").path("get")
                .path("responses").path("200").path("content").path("application/json").path("schema");
        JsonNode id = local.path("properties").path("id");
        assertFalse(local.has("$ref"));
        assertEquals("string", id.path("type").asText());
        assertFalse(id.has("format"));
        assertFalse(id.has("minimum"));

        for (String numericConstraint : new String[]{
                "format",
                "minimum",
                "maximum",
                "exclusiveMinimum",
                "exclusiveMaximum",
                "multipleOf"
        }) {
            assertFalse(
                    id.has(numericConstraint),
                    "String field must not retain numeric constraint: "
                            + numericConstraint
            );
        }

        assertTrue(id.path("readOnly").asBoolean());
        assertEquals("1", id.path("example").asText());
        assertTrue(id.path("example").isTextual());
        assertEquals("#/components/schemas/ResidentResponse", doc.path("paths")
                .path("/api/residents/{id}").path("put").path("responses")
                .path("200").path("content").path("application/json")
                .path("schema").path("$ref").asText());
    }
    @Test void rejectsAmbiguousAndUnsupportedMappings() {
        var generator = new ContractCandidateGenerator(PATH);
        assertEquals("UNSUPPORTED_TARGET", assertThrows(CandidateGenerationException.class,
                () -> generator.generate(proposal("properties.id", "string"))).code());
        assertEquals("UNSUPPORTED_TARGET", assertThrows(CandidateGenerationException.class,
                () -> generator.generate(proposal("id", "integer"))).code());
        assertEquals("MISSING_PROPOSAL", assertThrows(CandidateGenerationException.class,
                () -> generator.generate(null)).code());
    }

    @Test
    void rejectsUnsupportedChangeKinds() {
        var generator = new ContractCandidateGenerator(PATH);

        var unsupported = proposalWith(
                ContractChangeKind.ADD_RESPONSE_FIELD,
                "GET",
                "/api/residents/{id}",
                "id",
                "integer",
                "string",
                "string"
        );

        var exception = assertThrows(
                CandidateGenerationException.class,
                () -> generator.generate(unsupported)
        );

        assertEquals("UNSUPPORTED_CHANGE", exception.code());
    }

    @Test
    void rejectsIncorrectTargetMappings() {
        var generator = new ContractCandidateGenerator(PATH);

        var wrongMethod = proposalWith(
                ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                "POST",
                "/api/residents/{id}",
                "id",
                "integer",
                "string",
                "string"
        );

        var wrongPath = proposalWith(
                ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                "GET",
                "/api/residents",
                "id",
                "integer",
                "string",
                "string"
        );

        var wrongExpectedType = proposalWith(
                ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                "GET",
                "/api/residents/{id}",
                "id",
                "number",
                "string",
                "string"
        );

        var wrongObservedType = proposalWith(
                ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                "GET",
                "/api/residents/{id}",
                "id",
                "integer",
                "boolean",
                "string"
        );

        var missingField = proposalWith(
                ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                "GET",
                "/api/residents/{id}",
                null,
                "integer",
                "string",
                "string"
        );

        for (var invalid : new ContractChangeProposal[]{
                wrongMethod,
                wrongPath,
                wrongExpectedType,
                wrongObservedType,
                missingField
        }) {
            var exception = assertThrows(
                    CandidateGenerationException.class,
                    () -> generator.generate(invalid)
            );

            assertEquals("UNSUPPORTED_TARGET", exception.code());
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
