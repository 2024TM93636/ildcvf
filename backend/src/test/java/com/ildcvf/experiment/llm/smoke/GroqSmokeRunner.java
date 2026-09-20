package com.ildcvf.experiment.llm.smoke;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.evaluation.GroundTruthComparator;
import com.ildcvf.experiment.llm.evaluation.GroundTruthComparison;
import com.ildcvf.experiment.llm.evaluation.HallucinationClassifier;
import com.ildcvf.experiment.llm.evaluation.HallucinationEvaluation;
import com.ildcvf.experiment.llm.groq.ContractChangeProposalParser;
import com.ildcvf.experiment.llm.groq.GroqApiKeyResolver;
import com.ildcvf.experiment.llm.groq.GroqConfiguration;
import com.ildcvf.experiment.llm.groq.GroqExperimentAssets;
import com.ildcvf.experiment.llm.groq.GroqExperimentClient;
import com.ildcvf.experiment.llm.groq.GroqProviderAttempt;
import com.ildcvf.experiment.llm.groq.GroqProviderResponseParser;
import com.ildcvf.experiment.llm.groq.GroqRequestBuilder;
import com.ildcvf.experiment.llm.groq.JdkGroqTransport;
import com.ildcvf.experiment.llm.groq.ProposalParseResult;
import com.ildcvf.llm.evidence.EvidenceFingerprint;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.validation.ContractChangeProposalValidator;
import com.ildcvf.llm.validation.ProposalValidationResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

public final class GroqSmokeRunner {

    private final SmokeCatalog smokeCatalog;
    private final SmokeResultExporter exporter;
    private final ContractChangeProposalValidator validator;
    private final GroundTruthComparator comparator;
    private final HallucinationClassifier hallucinationClassifier;

    public GroqSmokeRunner() {
        this(
                new SmokeCatalog(),
                new SmokeResultExporter(),
                new ContractChangeProposalValidator(),
                new GroundTruthComparator(),
                new HallucinationClassifier());
    }

    GroqSmokeRunner(
            SmokeCatalog smokeCatalog,
            SmokeResultExporter exporter,
            ContractChangeProposalValidator validator,
            GroundTruthComparator comparator,
            HallucinationClassifier hallucinationClassifier
    ) {
        this.smokeCatalog =
                Objects.requireNonNull(smokeCatalog);

        this.exporter =
                Objects.requireNonNull(exporter);

        this.validator =
                Objects.requireNonNull(validator);

        this.comparator =
                Objects.requireNonNull(comparator);

        this.hallucinationClassifier =
                Objects.requireNonNull(
                        hallucinationClassifier);
    }

    public void printDryRun(
            String smokeId
    ) {
        SmokeDefinition definition =
                smokeCatalog.resolveDefinition(
                        smokeId);

        LlmReliabilityScenario scenario =
                smokeCatalog.resolveScenario(
                        definition);

        verifyScenarioFingerprint(
                scenario);

        GroqConfiguration configuration =
                GroqConfiguration
                        .frozenPreliminary();

        String request =
                new GroqRequestBuilder()
                        .build(
                                configuration,
                                scenario.evidence());

        verifyRequestLocally(
                request,
                scenario,
                configuration);

        System.out.println(
                "=== E6-D2 OFFLINE PRE-CALL VERIFICATION ===");

        System.out.println(
                "smoke ID = "
                        + definition.smokeId());

        System.out.println(
                "scenario ID = "
                        + definition.scenarioId());

        System.out.println(
                "evidence fingerprint = "
                        + scenario.evidenceFingerprint());

        System.out.println(
                "provider = groq");

        System.out.println(
                "requested model = "
                        + configuration.model());

        System.out.println(
                "prompt version = "
                        + SmokeResultExporter
                        .PROMPT_VERSION);

        System.out.println(
                "prompt SHA-256 = "
                        + GroqExperimentAssets
                        .FROZEN_PROMPT_SHA256);

        System.out.println(
                "temperature = "
                        + configuration.temperature());

        System.out.println(
                "top_p = "
                        + configuration.topP());

        System.out.println(
                "seed = "
                        + configuration.seed());

        System.out.println(
                "max_completion_tokens = "
                        + configuration
                        .maxCompletionTokens());

        System.out.println(
                "timeout = "
                        + configuration
                        .timeout()
                        .toSeconds()
                        + " seconds");

        System.out.println(
                "service tier = "
                        + configuration.serviceTier());

        System.out.println(
                "response format = json_schema");

        System.out.println(
                "schema name = "
                        + "contract_change_proposal");

        System.out.println(
                "strict mode = true");

        System.out.println(
                "request has model = true");

        System.out.println(
                "request has messages = true");

        System.out.println(
                "request has response_format = true");

        System.out.println(
                "request contains frozen prompt = true");

        System.out.println(
                "request contains bounded evidence = true");

        System.out.println(
                "provider invocation count = 0");

        System.out.println(
                "network call performed = false");
    }

    public SmokeExecutionResult executeRealAuthorizedSmoke(
            String smokeId
    ) throws IOException {

        SmokeDefinition definition =
                smokeCatalog.resolveDefinition(
                        smokeId);

        LlmReliabilityScenario scenario =
                smokeCatalog.resolveScenario(
                        definition);

        verifyScenarioFingerprint(
                scenario);

        GroqConfiguration configuration =
                GroqConfiguration
                        .frozenPreliminary();

        GroqRequestBuilder requestBuilder =
                new GroqRequestBuilder();

        String request =
                requestBuilder.build(
                        configuration,
                        scenario.evidence());

        verifyRequestLocally(
                request,
                scenario,
                configuration);

        SingleUseRecordingGroqTransport transport =
                new SingleUseRecordingGroqTransport(
                        new JdkGroqTransport());

        GroqExperimentClient client =
                new GroqExperimentClient(
                        configuration,
                        new GroqApiKeyResolver(),
                        requestBuilder,
                        transport,
                        new GroqProviderResponseParser(),
                        new ContractChangeProposalParser());

        SmokeExecutionResult result =
                executeExactlyOnce(
                        definition,
                        scenario,
                        client::generate,
                        transport);

        Path exported =
                exporter.export(result);

        printExecutionSummary(
                result,
                exported);

        return result;
    }

    SmokeExecutionResult executeOffline(
            String smokeId,
            Function<
                    com.ildcvf.llm.evidence
                            .GroundedEvidencePackage,
                    GroqProviderAttempt> provider,
            SingleUseRecordingGroqTransport transport
    ) {
        SmokeDefinition definition =
                smokeCatalog.resolveDefinition(
                        smokeId);

        LlmReliabilityScenario scenario =
                smokeCatalog.resolveScenario(
                        definition);

        verifyScenarioFingerprint(
                scenario);

        return executeExactlyOnce(
                definition,
                scenario,
                provider,
                transport);
    }

    private SmokeExecutionResult executeExactlyOnce(
            SmokeDefinition definition,
            LlmReliabilityScenario scenario,
            Function<
                    com.ildcvf.llm.evidence
                            .GroundedEvidencePackage,
                    GroqProviderAttempt> provider,
            SingleUseRecordingGroqTransport transport
    ) {
        Objects.requireNonNull(
                provider,
                "provider must not be null");

        Objects.requireNonNull(
                transport,
                "transport must not be null");

        GroqProviderAttempt attempt =
                provider.apply(
                        scenario.evidence());

        if (transport.invocationCount() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one transport "
                            + "invocation but observed "
                            + transport.invocationCount());
        }

        ProposalValidationResult validation = null;
        GroundTruthComparison comparison = null;
        HallucinationEvaluation hallucination = null;

        ProposalParseResult parse =
                attempt.proposalParseResult();

        if (parse != null
                && parse.proposal() != null) {

            ContractChangeProposal proposal =
                    parse.proposal();

            validation =
                    validator.validate(
                            scenario.evidence(),
                            proposal);

            comparison =
                    comparator.compare(
                            scenario.groundTruth(),
                            proposal);

            hallucination =
                    hallucinationClassifier.classify(
                            validation.errors());
        }

        return new SmokeExecutionResult(
                definition,
                scenario,
                attempt,
                transport.lastResponse(),
                validation,
                comparison,
                hallucination,
                transport.invocationCount());
    }

    private void verifyScenarioFingerprint(
            LlmReliabilityScenario scenario
    ) {
        String calculated =
                EvidenceFingerprint.sha256(
                        scenario.evidence());

        if (!calculated.equals(
                scenario.evidenceFingerprint())) {
            throw new IllegalStateException(
                    "Frozen scenario evidence "
                            + "fingerprint mismatch.");
        }
    }

    private void verifyRequestLocally(
            String request,
            LlmReliabilityScenario scenario,
            GroqConfiguration configuration
    ) {
        ObjectMapper mapper =
                new ObjectMapper();

        JsonNode root =
                mapper.readTree(request);

        if (root == null || !root.isObject()) {
            throw new IllegalStateException(
                    "Groq request root is invalid.");
        }

        if (root.get("model") == null
                || !configuration.model()
                .equals(
                        root.get("model")
                                .asText())) {
            throw new IllegalStateException(
                    "Groq request model mismatch.");
        }

        JsonNode messages =
                root.get("messages");

        if (messages == null
                || !messages.isArray()
                || messages.size() != 2) {
            throw new IllegalStateException(
                    "Groq request messages are invalid.");
        }

        String systemPrompt =
                messages.get(0)
                        .get("content")
                        .asText();

        String frozenPrompt =
                new GroqExperimentAssets()
                        .loadVerifiedPrompt();

        if (!frozenPrompt.equals(
                systemPrompt)) {
            throw new IllegalStateException(
                    "Frozen system prompt mismatch.");
        }

        String evidenceMessage =
                messages.get(1)
                        .get("content")
                        .asText();

        if (!evidenceMessage.contains(
                scenario.evidence()
                        .trafficEventId())
                || !evidenceMessage.contains(
                scenario.evidence()
                        .findingId())) {

            throw new IllegalStateException(
                    "Bounded evidence is absent "
                            + "from request.");
        }

        JsonNode responseFormat =
                root.get("response_format");

        if (responseFormat == null
                || !"json_schema".equals(
                responseFormat
                        .get("type")
                        .asText())) {

            throw new IllegalStateException(
                    "Structured output mode mismatch.");
        }

        JsonNode jsonSchema =
                responseFormat.get(
                        "json_schema");

        if (jsonSchema == null
                || !"contract_change_proposal"
                .equals(
                        jsonSchema
                                .get("name")
                                .asText())
                || !jsonSchema
                .get("strict")
                .asBoolean()) {

            throw new IllegalStateException(
                    "Frozen strict JSON Schema "
                            + "configuration mismatch.");
        }

        if (root.get("temperature").asDouble()
                != configuration.temperature()
                || root.get("top_p").asDouble()
                != configuration.topP()
                || root.get("seed").asLong()
                != configuration.seed()
                || root.get(
                        "max_completion_tokens")
                .asInt()
                != configuration
                .maxCompletionTokens()) {

            throw new IllegalStateException(
                    "Frozen generation configuration "
                            + "mismatch.");
        }
    }

    private void printExecutionSummary(
            SmokeExecutionResult result,
            Path exported
    ) {
        GroqProviderAttempt attempt =
                result.providerAttempt();

        System.out.println(
                "=== E6-D2 ONE-CALL RESULT ===");

        System.out.println(
                "smoke ID = "
                        + result.definition()
                        .smokeId());

        System.out.println(
                "scenario ID = "
                        + result.definition()
                        .scenarioId());

        System.out.println(
                "provider invocation count = "
                        + result.providerInvocationCount());

        System.out.println(
                "provider status = "
                        + attempt.status());

        System.out.println(
                "HTTP status = "
                        + attempt.metadata()
                        .httpStatus());

        System.out.println(
                "infrastructure PASS = "
                        + result.infrastructurePassed());

        System.out.println(
                "proposal parse status = "
                        + (attempt
                        .proposalParseResult()
                        == null
                        ? null
                        : attempt
                        .proposalParseResult()
                        .status()));

        System.out.println(
                "validator accepted = "
                        + (result.validationResult()
                        == null
                        ? null
                        : result.validationResult()
                        .valid()));

        System.out.println(
                "ground truth exact = "
                        + (result
                        .groundTruthComparison()
                        == null
                        ? null
                        : result
                        .groundTruthComparison()
                        .exactMatch()));

        System.out.println(
                "hallucinated = "
                        + (result
                        .hallucinationEvaluation()
                        == null
                        ? null
                        : result
                        .hallucinationEvaluation()
                        .hallucinated()));

        System.out.println(
                "retry count = 0");

        System.out.println(
                "result directory = "
                        + exported);

        System.out.println(
                "STOP: E6-D2 provider execution "
                        + "is complete.");

        System.out.println(
                "DO NOT execute this command again.");

        System.out.println(
                "DO NOT execute Smoke B.");

        System.out.println(
                "DO NOT execute measured attempts.");
    }
}
