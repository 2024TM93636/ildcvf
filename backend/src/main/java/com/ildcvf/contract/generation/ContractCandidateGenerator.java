package com.ildcvf.contract.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Pure candidate generation: no writes, approval checks, or publication. */
@Service
public class ContractCandidateGenerator {
    private static final String TARGET_PATH = "/api/residents/{id}";
    private static final String REF = "#/components/schemas/ResidentResponse";
    private final Path baselinePath;

    public ContractCandidateGenerator(@Value("${ildcvf.contract.baseline-path}") String baselinePath) {
        this.baselinePath = Path.of(baselinePath).toAbsolutePath().normalize();
    }

    public GeneratedContractCandidate generate(ContractChangeProposal proposal) {
        require(proposal != null, "MISSING_PROPOSAL", "Proposal is required");
        require(proposal.changeKind() == ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                "UNSUPPORTED_CHANGE", "Only CHANGE_RESPONSE_FIELD_TYPE is supported");
        require("GET".equals(proposal.method()) && TARGET_PATH.equals(proposal.matchedOpenApiPath())
                        && "id".equals(proposal.targetFieldPath())
                        && "integer".equals(proposal.expected())
                        && "string".equals(proposal.observed())
                        && "string".equals(proposal.proposedValue()),
                "UNSUPPORTED_TARGET", "Proposal does not exactly identify the supported GET response id type change");
        try {
            byte[] baselineBytes = Files.readAllBytes(baselinePath);
            String original = new String(baselineBytes, StandardCharsets.UTF_8);
            parseStrict(original, "BASELINE_INVALID");
            ObjectNode root = (ObjectNode) Yaml.mapper().readTree(original);
            ObjectNode originalTree = root.deepCopy();
            JsonNode shared = root.path("components").path("schemas").path("ResidentResponse");
            JsonNode response = root.path("paths").path(TARGET_PATH).path("get")
                    .path("responses").path("200").path("content").path("application/json").path("schema");
            require(shared.isObject() && REF.equals(response.path("$ref").asText(null))
                            && response.size() == 1, "BASELINE_SHAPE", "Unexpected response schema reference");
            JsonNode id = shared.path("properties").path("id");
            require(id.isObject() && "integer".equals(id.path("type").asText(null)),
                    "BASELINE_SHAPE", "ResidentResponse.id is not an integer property");
            ObjectNode local = shared.deepCopy();
            ObjectNode localId = (ObjectNode) local.path("properties").path("id");
            localId.put("type", "string");
            for (String numeric : new String[]{"format", "minimum", "maximum", "exclusiveMinimum",
                    "exclusiveMaximum", "multipleOf"}) localId.remove(numeric);
            JsonNode example = localId.get("example");
            if (example != null && example.isNumber()) localId.put("example", example.asText());
            ((ObjectNode) root.path("paths").path(TARGET_PATH).path("get")
                    .path("responses").path("200").path("content").path("application/json"))
                    .set("schema", local);
            String yaml = Yaml.pretty().writeValueAsString(root);
            parseStrict(yaml, "GENERATED_DOCUMENT_INVALID");
            JsonNode reparsed = Yaml.mapper().readTree(yaml);
            require(Objects.equals(shared, reparsed.path("components").path("schemas").path("ResidentResponse")),
                    "ISOLATION_FAILED", "Shared ResidentResponse changed");
            JsonNode changed = reparsed.path("paths").path(TARGET_PATH).path("get")
                    .path("responses").path("200").path("content").path("application/json").path("schema");
            require("string".equals(changed.path("properties").path("id").path("type").asText(null))
                    && changed.path("properties").path("id").path("format").isMissingNode()
                    && changed.path("properties").path("id").path("minimum").isMissingNode(),
                    "TRANSFORMATION_INVALID", "Target field transformation failed");
            ObjectNode beforeOther = originalTree.deepCopy();
            ObjectNode afterOther = ((ObjectNode) reparsed).deepCopy();
            // Compare every unaffected path and component, excluding only the target GET response schema.
            ((ObjectNode) beforeOther.path("paths").path(TARGET_PATH).path("get")
                    .path("responses").path("200").path("content").path("application/json"))
                    .set("schema", response.deepCopy());
            ((ObjectNode) afterOther.path("paths").path(TARGET_PATH).path("get")
                    .path("responses").path("200").path("content").path("application/json"))
                    .set("schema", response.deepCopy());
            require(beforeOther.equals(afterOther), "ISOLATION_FAILED", "Unrelated document content changed");
            return new GeneratedContractCandidate(baselinePath.getFileName().toString(),
                    sha(baselineBytes), yaml, sha(yaml.getBytes(StandardCharsets.UTF_8)),
                    new CandidateValidationResult(true, "Full document reparsed without diagnostics; target and isolation checks passed"));
        } catch (CandidateGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new CandidateGenerationException("GENERATION_FAILED", "Unable to generate candidate", e);
        }
    }

    private static void parseStrict(String yaml, String code) {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(yaml, null, options);
        require(result != null && result.getOpenAPI() != null
                        && result.getMessages() != null && result.getMessages().isEmpty(),
                code, "Swagger Parser rejected document: " + (result == null ? "null result" : result.getMessages()));
        require(result.getOpenAPI().getPaths() != null, code, "OpenAPI paths are missing");
    }

    private static void require(boolean condition, String code, String message) {
        if (!condition) throw new CandidateGenerationException(code, message);
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
