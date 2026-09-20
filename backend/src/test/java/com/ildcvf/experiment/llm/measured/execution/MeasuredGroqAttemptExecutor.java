package com.ildcvf.experiment.llm.measured.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import com.ildcvf.experiment.llm.compatibility.TokenBudgetCompatibilityConfiguration;
import com.ildcvf.experiment.llm.evaluation.AttemptEvaluation;
import com.ildcvf.experiment.llm.evaluation.AttemptEvaluator;
import com.ildcvf.experiment.llm.evaluation.FieldComparison;
import com.ildcvf.experiment.llm.evaluation.GroundTruthComparison;
import com.ildcvf.experiment.llm.evaluation.HallucinationEvaluation;
import com.ildcvf.experiment.llm.evaluation.ValidatorEvaluation;
import com.ildcvf.experiment.llm.groq.GroqConfiguration;
import com.ildcvf.experiment.llm.groq.GroqHttpResponse;
import com.ildcvf.experiment.llm.groq.GroqProviderAttempt;
import com.ildcvf.experiment.llm.groq.GroqRequestBuilder;
import com.ildcvf.experiment.llm.groq.ProposalParseResult;
import com.ildcvf.experiment.llm.groq.ProviderAttemptMetadata;
import com.ildcvf.experiment.llm.measured.MeasuredAttempt;
import com.ildcvf.experiment.llm.measured.MeasuredAttemptResult;
import com.ildcvf.experiment.llm.measured.MeasuredProtocolConfiguration;
import com.ildcvf.llm.evidence.EvidenceFingerprint;
import com.ildcvf.llm.proposal.ContractChangeProposal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MeasuredGroqAttemptExecutor {

    private static final Pattern REASONING_TOKENS_PATTERN =
            Pattern.compile(
                    "\"reasoning_tokens\"\\s*:\\s*(\\d+)"
            );

    private final LlmScenarioCatalog scenarioCatalog;
    private final AttemptEvaluator evaluator;
    private final GroqRequestBuilder requestBuilder;
    private final GroqConfiguration configuration;
    private final Function<
            com.ildcvf.llm.evidence.GroundedEvidencePackage,
            GroqProviderAttempt> provider;
    private final MeasuredRecordingGroqTransport transport;
    private final ObjectMapper objectMapper;

    public MeasuredGroqAttemptExecutor(
            LlmScenarioCatalog scenarioCatalog,
            AttemptEvaluator evaluator,
            GroqRequestBuilder requestBuilder,
            GroqConfiguration configuration,
            Function<
                    com.ildcvf.llm.evidence.GroundedEvidencePackage,
                    GroqProviderAttempt> provider,
            MeasuredRecordingGroqTransport transport
    ) {
        this.scenarioCatalog =
                Objects.requireNonNull(
                        scenarioCatalog,
                        "scenarioCatalog must not be null"
                );

        this.evaluator =
                Objects.requireNonNull(
                        evaluator,
                        "evaluator must not be null"
                );

        this.requestBuilder =
                Objects.requireNonNull(
                        requestBuilder,
                        "requestBuilder must not be null"
                );

        this.configuration =
                Objects.requireNonNull(
                        configuration,
                        "configuration must not be null"
                );

        this.provider =
                Objects.requireNonNull(
                        provider,
                        "provider must not be null"
                );

        this.transport =
                Objects.requireNonNull(
                        transport,
                        "transport must not be null"
                );

        this.objectMapper = new ObjectMapper();

        verifyFrozenMeasuredConfiguration();
    }

    public static GroqConfiguration frozenMeasuredGroqConfiguration() {
        GroqConfiguration configuration =
                TokenBudgetCompatibilityConfiguration
                        .compatibility();

        if (configuration.maxCompletionTokens()
                != MeasuredProtocolConfiguration
                .MAX_COMPLETION_TOKENS) {

            throw new IllegalStateException(
                    "Measured Groq configuration must use "
                            + "2048 completion tokens."
            );
        }

        return configuration;
    }

    public LlmReliabilityScenario resolveScenario(
            MeasuredAttempt attempt
    ) {
        Objects.requireNonNull(
                attempt,
                "attempt must not be null"
        );

        LlmReliabilityScenario scenario =
                scenarioCatalog.scenarios()
                        .stream()
                        .filter(candidate ->
                                attempt.scenarioId().equals(
                                        candidate.scenarioId()
                                )
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Measured scenario is unavailable: "
                                                + attempt.scenarioId()
                                )
                        );

        if (!attempt.scenarioId().equals(
                scenario.scenarioId()
        )) {
            throw new IllegalStateException(
                    "Resolved scenario identity does not "
                            + "match measured attempt."
            );
        }

        String calculatedFingerprint =
                EvidenceFingerprint.sha256(
                        scenario.evidence()
                );

        if (!calculatedFingerprint.equals(
                scenario.evidenceFingerprint()
        )) {
            throw new IllegalStateException(
                    "Calculated evidence fingerprint "
                            + "does not match frozen scenario: "
                            + scenario.scenarioId()
            );
        }

        return scenario;
    }

    public String buildFrozenRequest(
            MeasuredAttempt attempt
    ) {
        LlmReliabilityScenario scenario =
                resolveScenario(attempt);

        return requestBuilder.build(
                configuration,
                scenario.evidence()
        );
    }

    public MeasuredAttemptResult executeExactlyOnce(
            MeasuredAttempt attempt
    ) {
        Objects.requireNonNull(
                attempt,
                "attempt must not be null"
        );

        if (transport.invocationCount() != 0) {
            throw new IllegalStateException(
                    "Measured transport has already been consumed."
            );
        }

        LlmReliabilityScenario scenario =
                resolveScenario(attempt);

        GroqProviderAttempt providerAttempt =
                provider.apply(
                        scenario.evidence()
                );

        if (transport.invocationCount() != 1) {
            throw new IllegalStateException(
                    "Measured provider execution must perform "
                            + "exactly one transport invocation; "
                            + "observed "
                            + transport.invocationCount()
                            + "."
            );
        }

        AttemptEvaluation evaluation =
                evaluator.evaluate(
                        scenario,
                        attempt.repetition(),
                        providerAttempt
                );

        return convert(
                attempt,
                scenario,
                providerAttempt,
                evaluation
        );
    }

    private MeasuredAttemptResult convert(
            MeasuredAttempt attempt,
            LlmReliabilityScenario scenario,
            GroqProviderAttempt providerAttempt,
            AttemptEvaluation evaluation
    ) {
        ProviderAttemptMetadata metadata =
                providerAttempt.metadata();

        ProposalParseResult parse =
                providerAttempt.proposalParseResult();

        ValidatorEvaluation validator =
                evaluation.validator();

        GroundTruthComparison comparison =
                evaluation.groundTruth()
                        .comparison();

        HallucinationEvaluation hallucination =
                evaluation.hallucination();

        GroqHttpResponse rawResponse =
                transport.lastResponse();

        String rawProviderResponse =
                rawResponse == null
                        ? providerAttempt.envelope() == null
                        ? null
                        : providerAttempt.envelope()
                        .rawProviderResponse()
                        : rawResponse.body();

        String assistantContent =
                providerAttempt.envelope() == null
                        ? null
                        : providerAttempt.envelope()
                        .assistantContent();

        ContractChangeProposal proposal =
                parse == null
                        ? null
                        : parse.proposal();

        return new MeasuredAttemptResult(
                attempt.attemptId(),
                attempt.scenarioId(),
                attempt.repetition(),
                scenario.evidenceFingerprint(),

                MeasuredProtocolConfiguration.PROTOCOL_ID,
                MeasuredProtocolConfiguration
                        .CONFIGURATION_LABEL,

                MeasuredProtocolConfiguration.PROMPT_VERSION,
                MeasuredProtocolConfiguration.PROMPT_SHA256,
                MeasuredProtocolConfiguration.SCHEMA_SHA256,

                metadata.provider(),
                metadata.requestedModel(),
                metadata.returnedModel(),
                metadata.systemFingerprint(),
                metadata.serviceTier(),

                metadata.temperature(),
                metadata.topP(),
                metadata.seed(),
                metadata.maxCompletionTokens(),
                Math.toIntExact(
                        configuration.timeout()
                                .toMillis()
                ),

                transport.invocationCount(),
                0,

                metadata.httpStatus(),
                providerAttempt.status().name(),
                providerAttempt.diagnostic(),
                providerAttempt.transportDiagnostic(),

                toJsonNode(rawProviderResponse),
                assistantContent,

                parse == null
                        ? null
                        : parse.status().name(),
                proposal == null
                        ? null
                        : objectMapper.valueToTree(
                        proposal
                ),

                validator.applicability().name(),
                validator.accepted(),
                validator.codes()
                        .stream()
                        .map(Enum::name)
                        .toList(),

                evaluation.groundTruth()
                        .applicability()
                        .name(),
                groundTruthFields(comparison),
                comparison == null
                        ? null
                        : comparison.exactMatch(),

                hallucination.applicability().name(),
                hallucination.hallucinated(),
                hallucination.reasonCodes()
                        .stream()
                        .map(Enum::name)
                        .toList(),

                metadata.latency() == null
                        ? null
                        : metadata.latency()
                        .toNanos()
                        / 1_000_000.0d,

                metadata.inputTokens(),
                metadata.outputTokens(),
                extractReasoningTokens(
                        rawProviderResponse
                ),
                metadata.totalTokens(),

                metadata.finishReason(),
                metadata.timestamp(),

                true
        );
    }

    private Map<String, Boolean> groundTruthFields(
            GroundTruthComparison comparison
    ) {
        if (comparison == null) {
            return Map.of();
        }

        Map<String, Boolean> result =
                new LinkedHashMap<>();

        for (FieldComparison field
                : comparison.fields()) {

            result.put(
                    switch (field.field()) {
                        case SOURCE_TRAFFIC_EVENT_ID ->
                                "sourceTrafficEventId";
                        case SOURCE_FINDING_ID ->
                                "sourceFindingId";
                        case DRIFT_TYPE ->
                                "driftType";
                        case CHANGE_KIND ->
                                "changeKind";
                        case METHOD ->
                                "method";
                        case RUNTIME_PATH ->
                                "runtimePath";
                        case MATCHED_OPEN_API_PATH ->
                                "matchedOpenApiPath";
                        case TARGET_FIELD_PATH ->
                                "targetFieldPath";
                        case EXPECTED ->
                                "expected";
                        case OBSERVED ->
                                "observed";
                        case PROPOSED_VALUE ->
                                "proposedValue";
                    },
                    field.match()
            );
        }

        List<String> expectedOrder =
                MeasuredProtocolConfiguration
                        .SCORED_FIELDS;

        if (!List.copyOf(result.keySet())
                .equals(expectedOrder)) {
            throw new IllegalStateException(
                    "Ground-truth comparison does not "
                            + "contain the frozen 11 fields "
                            + "in the frozen order."
            );
        }

        return Map.copyOf(result);
    }

    private JsonNode toJsonNode(
            String value
    ) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper
                    .getNodeFactory()
                    .textNode(value);
        }
    }

    private Long extractReasoningTokens(
            String rawProviderResponse
    ) {
        if (rawProviderResponse == null
                || rawProviderResponse.isBlank()) {
            return null;
        }

        Matcher matcher =
                REASONING_TOKENS_PATTERN.matcher(
                        rawProviderResponse
                );

        if (!matcher.find()) {
            return null;
        }

        return Long.parseLong(
                matcher.group(1)
        );
    }

    private void verifyFrozenMeasuredConfiguration() {
        if (!MeasuredProtocolConfiguration.PROVIDER.equals(
                "groq"
        )) {
            throw new IllegalStateException(
                    "Frozen provider changed."
            );
        }

        if (!configuration.model().equals(
                MeasuredProtocolConfiguration
                        .REQUESTED_MODEL
        )) {
            throw new IllegalStateException(
                    "Frozen measured model changed."
            );
        }

        if (Double.compare(
                configuration.temperature(),
                MeasuredProtocolConfiguration
                        .TEMPERATURE
        ) != 0) {
            throw new IllegalStateException(
                    "Frozen temperature changed."
            );
        }

        if (Double.compare(
                configuration.topP(),
                MeasuredProtocolConfiguration.TOP_P
        ) != 0) {
            throw new IllegalStateException(
                    "Frozen top_p changed."
            );
        }

        if (configuration.seed()
                != MeasuredProtocolConfiguration.SEED) {
            throw new IllegalStateException(
                    "Frozen seed changed."
            );
        }

        if (configuration.maxCompletionTokens()
                != MeasuredProtocolConfiguration
                .MAX_COMPLETION_TOKENS) {
            throw new IllegalStateException(
                    "Frozen completion-token budget changed."
            );
        }

        if (!configuration.serviceTier()
                .equals(
                        MeasuredProtocolConfiguration
                                .SERVICE_TIER
                )) {
            throw new IllegalStateException(
                    "Frozen service tier changed."
            );
        }

        if (configuration.timeout().toMillis()
                != MeasuredProtocolConfiguration
                .TIMEOUT_MILLISECONDS) {
            throw new IllegalStateException(
                    "Frozen timeout changed."
            );
        }
    }
}