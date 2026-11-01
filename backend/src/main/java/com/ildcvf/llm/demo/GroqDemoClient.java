package com.ildcvf.llm.demo;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/** One-shot, manually invoked provider adapter; never retries or logs secrets. */
public class GroqDemoClient {
    private static final String PROMPT_SHA256 = "d9ee37eddddff37d75ecdeb933639c356054966f70e2e76ba5694efc7c655947";
    private static final URI ENDPOINT = URI.create("https://api.groq.com/openai/v1/chat/completions");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final Path assetsRoot;

    public GroqDemoClient(Path assetsRoot) {
        this(assetsRoot, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
    }

    GroqDemoClient(Path assetsRoot, HttpClient http) {
        this.assetsRoot = Objects.requireNonNull(assetsRoot).toAbsolutePath().normalize();
        this.http = Objects.requireNonNull(http);
    }

    public record Outcome(String status, String providerStatus, ContractChangeProposal proposal,
                          String message, Integer httpStatus, String rawProviderResponse) {}

    public Outcome generate(GroundedEvidencePackage evidence) {
        Objects.requireNonNull(evidence);
        final String body;
        try {
            body = buildRequest(evidence); // verifies read-only assets before network access
        } catch (Exception ex) {
            return new Outcome("PROVIDER_FAILURE", "CONFIGURATION_FAILURE", null,
                    "Frozen prompt/schema unavailable or integrity verification failed.", null, null);
        }
        String key = System.getenv("GROQ_API_KEY");
        if (key == null || key.isBlank()) {
            return new Outcome("PROVIDER_FAILURE", "CREDENTIAL_UNAVAILABLE", null,
                    "GROQ_API_KEY is not configured.", null, null);
        }
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT).timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        final HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException ex) {
            return new Outcome("TIMEOUT", "TIMEOUT", null, "Provider request timed out.", null, null);
        } catch (IOException ex) {
            String diagnostic = classifyTransportFailure(ex);

            return new Outcome(
                    "PROVIDER_FAILURE",
                    "TRANSPORT_FAILURE",
                    null,
                    "Provider transport failed. Diagnostic: " + diagnostic,
                    null,
                    null
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Outcome("PROVIDER_FAILURE", "INTERRUPTED", null, "Provider request interrupted.", null, null);
        }
        int code = response.statusCode();
        String raw = response.body();

        if (code < 200 || code >= 300) {
            String message = "Provider returned HTTP " + code + ".";

            if (code == 400) {
                message += " Generation diagnostic: "
                        + summarizeFailedGeneration(raw);
            }

            return new Outcome(
                    "PROVIDER_FAILURE",
                    "HTTP_ERROR",
                    null,
                    message,
                    code,
                    raw
            );
        }
        try {
            JsonNode root = json.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) throw new IllegalArgumentException();
            JsonNode proposalNode = json.readTree(content.asText());
            ContractChangeProposal proposal = parseProposal(proposalNode);
            return new Outcome("PARSED", "SUCCESS", proposal, null, code, raw);
        } catch (RuntimeException ex) {
            return new Outcome("PARSE_FAILURE", "SUCCESS", null,
                    "Provider response or proposal could not be parsed.", code, raw);
        }
    }

    private String buildRequest(GroundedEvidencePackage e) throws Exception {
        Path prompt = assetsRoot.resolve("prompts/proposal-v1.txt");
        Path checksum = assetsRoot.resolve("prompts/proposal-v1.sha256");
        Path schema = assetsRoot.resolve("schema/contract-change-proposal.schema.json");
        byte[] promptBytes = Files.readAllBytes(prompt);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(promptBytes));
        String recorded = Files.readString(checksum, StandardCharsets.UTF_8).trim();
        if (!PROMPT_SHA256.equals(actual) || !actual.equals(recorded)) throw new IllegalStateException("Prompt hash mismatch");
        JsonNode schemaNode = json.readTree(Files.readString(schema, StandardCharsets.UTF_8));
        if (!schemaNode.isObject() || !schemaNode.path("type").asText().equals("object")
                || !schemaNode.path("properties").isObject() || !schemaNode.path("required").isArray()
                || schemaNode.path("required").size() != 13) throw new IllegalStateException("Unexpected schema structure");
        ObjectNode request = json.createObjectNode();
        request.put("model", "openai/gpt-oss-20b");
        var messages = request.putArray("messages");
        messages.addObject().put("role", "system").put("content", new String(promptBytes, StandardCharsets.UTF_8));
        ObjectNode evidenceJson = json.createObjectNode();
        put(evidenceJson, "trafficEventId", e.trafficEventId());
        evidenceJson.put("trafficSequence", e.trafficSequence());
        put(evidenceJson, "findingId", e.findingId());
        put(evidenceJson, "driftType", e.driftType() == null ? null : e.driftType().name());
        put(evidenceJson, "severity", e.severity() == null ? null : e.severity().name());
        put(evidenceJson, "method", e.method());
        put(evidenceJson, "runtimePath", e.runtimePath());
        put(evidenceJson, "matchedOpenApiPath", e.matchedOpenApiPath());
        put(evidenceJson, "fieldPath", e.fieldPath());
        put(evidenceJson, "expected", e.expected());
        put(evidenceJson, "observed", e.observed());
        put(evidenceJson, "findingMessage", e.findingMessage());
        if (e.relevantContractContext() == null) evidenceJson.putNull("relevantContractContext");
        else {
            ObjectNode ctx = evidenceJson.putObject("relevantContractContext");
            put(ctx, "openApiVersion", e.relevantContractContext().openApiVersion());
            put(ctx, "method", e.relevantContractContext().method());
            put(ctx, "pathTemplate", e.relevantContractContext().pathTemplate());
            put(ctx, "operationId", e.relevantContractContext().operationId());
        }
        messages.addObject().put("role", "user").put("content", json.writeValueAsString(evidenceJson));
        request.put("temperature", 0);
        request.put("top_p", 1);
        request.put("seed", 20260919);
        request.put("max_completion_tokens", 2048);
        request.put("stream", false);
        request.put("service_tier", "on_demand");
        ObjectNode format = request.putObject("response_format");
        format.put("type", "json_schema");
        ObjectNode structured = format.putObject("json_schema");
        structured.put("name", "contract_change_proposal");
        structured.put("strict", true);
        structured.set("schema", schemaNode);
        return json.writeValueAsString(request);
    }

    private static void put(ObjectNode node, String name, String value) {
        if (value == null) node.putNull(name); else node.put(name, value);
    }

    private static String required(JsonNode n, String key) {
        JsonNode v = n.get(key);
        if (v == null || !v.isTextual() || v.asText().isBlank()) throw new IllegalArgumentException(key);
        return v.asText();
    }

    private static String nullable(JsonNode n, String key) {
        JsonNode v = n.get(key);
        if (v == null || (!v.isNull() && !v.isTextual())) throw new IllegalArgumentException(key);
        return v.isNull() ? null : v.asText();
    }

    private static ContractChangeProposal parseProposal(JsonNode n) {
        if (n == null || !n.isObject()) throw new IllegalArgumentException("Proposal must be object");
        return new ContractChangeProposal(required(n, "proposalId"), required(n, "sourceTrafficEventId"),
                required(n, "sourceFindingId"), DriftType.valueOf(required(n, "driftType")),
                ContractChangeKind.valueOf(required(n, "changeKind")), required(n, "method"),
                required(n, "runtimePath"), nullable(n, "matchedOpenApiPath"),
                nullable(n, "targetFieldPath"), nullable(n, "expected"), nullable(n, "observed"),
                nullable(n, "proposedValue"), required(n, "rationale"));
    }

    private static String classifyTransportFailure(IOException ex) {
        Throwable current = ex;
        boolean handshakeFailure = false;
        boolean socketFailure = false;

        while (current != null) {
            if (current instanceof java.security.cert.CertPathValidatorException
                    || current instanceof java.security.cert.CertPathBuilderException) {
                return "CERTIFICATE_TRUST_FAILURE";
            }

            if (current instanceof java.security.cert.CertificateException) {
                return "CERTIFICATE_VALIDATION_FAILURE";
            }

            if (current instanceof javax.net.ssl.SSLHandshakeException) {
                handshakeFailure = true;

                String message = current.getMessage();
                if (message != null) {
                    String normalized = message.toLowerCase(java.util.Locale.ROOT);

                    if (normalized.contains("pkix")
                            || normalized.contains("certification path")
                            || normalized.contains("trust anchor")) {
                        return "CERTIFICATE_TRUST_FAILURE";
                    }

                    if (normalized.contains("remote host terminated")
                            || normalized.contains("connection reset")) {
                        return "TLS_CONNECTION_TERMINATED";
                    }

                    if (normalized.contains("protocol")
                            || normalized.contains("handshake_failure")) {
                        return "TLS_NEGOTIATION_FAILURE";
                    }
                }
            }

            if (current instanceof java.net.UnknownHostException) {
                return "DNS_RESOLUTION_FAILURE";
            }

            if (current instanceof java.net.ConnectException) {
                return "CONNECTION_FAILURE";
            }

            if (current instanceof java.net.SocketException) {
                socketFailure = true;
            }

            current = current.getCause();
        }

        if (handshakeFailure) {
            return "TLS_HANDSHAKE_FAILURE";
        }

        if (socketFailure) {
            return "SOCKET_FAILURE";
        }

        return "OTHER_IO_FAILURE";
    }

    private String summarizeFailedGeneration(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ERROR_BODY_EMPTY";
        }

        try {
            JsonNode root = json.readTree(raw);
            JsonNode error = root.path("error");
            JsonNode failed = error.path("failed_generation");

            if (failed.isMissingNode() || failed.isNull()) {
                return "FAILED_GENERATION_ABSENT";
            }

            if (!failed.isTextual()) {
                return "FAILED_GENERATION_NOT_TEXT";
            }

            String generated = failed.asText();

            if (generated.isBlank()) {
                return "FAILED_GENERATION_EMPTY";
            }

            String jsonValidity;

            try {
                json.readTree(generated);
                jsonValidity = "JSON_SYNTAX_VALID";
            } catch (Exception ex) {
                jsonValidity = "JSON_SYNTAX_INVALID";
            }

            String trimmed = generated.trim();

            boolean startsWithObject = trimmed.startsWith("{");
            boolean endsWithObject = trimmed.endsWith("}");
            boolean containsCodeFence = trimmed.contains("```");

            return "FAILED_GENERATION_PRESENT"
                    + "; length=" + generated.length()
                    + "; " + jsonValidity
                    + "; startsWithObject=" + startsWithObject
                    + "; endsWithObject=" + endsWithObject
                    + "; containsCodeFence=" + containsCodeFence;

        } catch (Exception ex) {
            return "ERROR_BODY_PARSE_FAILED";
        }
    }
}
