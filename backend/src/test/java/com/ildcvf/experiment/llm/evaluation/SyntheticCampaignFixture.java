package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import com.ildcvf.experiment.llm.groq.GroqProviderAttempt;
import com.ildcvf.experiment.llm.groq.ProposalParseResult;
import com.ildcvf.experiment.llm.groq.ProposalParseStatus;
import com.ildcvf.experiment.llm.groq.ProviderAttemptMetadata;
import com.ildcvf.experiment.llm.groq.ProviderCallStatus;
import com.ildcvf.llm.proposal.ContractChangeProposal;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class SyntheticCampaignFixture {

    private final List<LlmReliabilityScenario> scenarios =
            new LlmScenarioCatalog()
                    .scenarios();

    private final List<AttemptIdentity> schedule =
            new CampaignSchedule()
                    .create(scenarios);

    List<AttemptEvaluation> evaluations() {
        AttemptEvaluator evaluator =
                new AttemptEvaluator();

        List<AttemptEvaluation> results =
                new ArrayList<>(40);

        for (int index = 0;
             index < schedule.size();
             index++) {

            AttemptIdentity identity =
                    schedule.get(index);

            LlmReliabilityScenario scenario =
                    scenario(identity.scenarioId());

            GroqProviderAttempt attempt =
                    switch (index) {
                        case 0, 1, 2, 3,
                             4, 5, 6, 7,
                             8, 9, 10, 11,
                             12, 13, 14, 15,
                             16, 17, 18, 19,
                             20, 21, 22, 23 ->
                                exactAttempt(
                                        scenario,
                                        index);

                        case 24, 25, 26, 27 ->
                                hallucinatedAttempt(
                                        scenario,
                                        index);

                        case 28, 29, 30, 31 ->
                                structuralAttempt(
                                        scenario,
                                        index);

                        case 32, 33, 34, 35 ->
                                parseFailureAttempt(
                                        scenario,
                                        index);

                        case 36, 37 ->
                                providerFailure(
                                        scenario,
                                        index,
                                        ProviderCallStatus.HTTP_5XX);

                        case 38 ->
                                providerFailure(
                                        scenario,
                                        index,
                                        ProviderCallStatus.TIMEOUT);

                        case 39 ->
                                providerFailure(
                                        scenario,
                                        index,
                                        ProviderCallStatus
                                                .TRANSPORT_FAILURE);

                        default ->
                                throw new IllegalStateException();
                    };

            results.add(
                    evaluator.evaluate(
                            scenario,
                            identity.repetition(),
                            attempt));
        }

        return List.copyOf(results);
    }

    private GroqProviderAttempt exactAttempt(
            LlmReliabilityScenario scenario,
            int index
    ) {
        return parsed(
                scenario,
                canonical(scenario),
                index);
    }

    private GroqProviderAttempt hallucinatedAttempt(
            LlmReliabilityScenario scenario,
            int index
    ) {
        ContractChangeProposal canonical =
                canonical(scenario);

        ContractChangeProposal altered =
                new ContractChangeProposal(
                        canonical.proposalId(),
                        canonical.sourceTrafficEventId(),
                        canonical.sourceFindingId(),
                        canonical.driftType(),
                        canonical.changeKind(),
                        "INVENTED_METHOD",
                        canonical.runtimePath(),
                        canonical.matchedOpenApiPath(),
                        canonical.targetFieldPath(),
                        canonical.expected(),
                        canonical.observed(),
                        canonical.proposedValue(),
                        canonical.rationale());

        return parsed(
                scenario,
                altered,
                index);
    }

    private GroqProviderAttempt structuralAttempt(
            LlmReliabilityScenario scenario,
            int index
    ) {
        ContractChangeProposal canonical =
                canonical(scenario);

        ContractChangeProposal altered =
                new ContractChangeProposal(
                        "",
                        canonical.sourceTrafficEventId(),
                        canonical.sourceFindingId(),
                        canonical.driftType(),
                        canonical.changeKind(),
                        canonical.method(),
                        canonical.runtimePath(),
                        canonical.matchedOpenApiPath(),
                        canonical.targetFieldPath(),
                        canonical.expected(),
                        canonical.observed(),
                        canonical.proposedValue(),
                        canonical.rationale());

        return parsed(
                scenario,
                altered,
                index);
    }

    private GroqProviderAttempt parsed(
            LlmReliabilityScenario scenario,
            ContractChangeProposal proposal,
            int index
    ) {
        return new GroqProviderAttempt(
                ProviderCallStatus.SUCCESS,
                metadata(
                        scenario,
                        index,
                        true),
                null,
                ProposalParseResult.success(
                        proposal),
                null
        );
    }

    private GroqProviderAttempt parseFailureAttempt(
            LlmReliabilityScenario scenario,
            int index
    ) {
        return new GroqProviderAttempt(
                ProviderCallStatus.PROPOSAL_PARSE_FAILURE,
                metadata(
                        scenario,
                        index,
                        true),
                null,
                ProposalParseResult.failure(
                        ProposalParseStatus.MALFORMED_JSON,
                        "synthetic malformed JSON"),
                "synthetic proposal parse failure"
        );
    }

    private GroqProviderAttempt providerFailure(
            LlmReliabilityScenario scenario,
            int index,
            ProviderCallStatus status
    ) {
        return new GroqProviderAttempt(
                status,
                metadata(
                        scenario,
                        index,
                        false),
                null,
                null,
                "synthetic provider failure"
        );
    }

    private ProviderAttemptMetadata metadata(
            LlmReliabilityScenario scenario,
            int index,
            boolean usageObserved
    ) {
        long ordinal = index + 1L;

        return new ProviderAttemptMetadata(
                "groq",
                "openai/gpt-oss-20b",
                usageObserved
                        ? "openai/gpt-oss-20b"
                        : null,
                usageObserved
                        ? "synthetic-fingerprint"
                        : null,
                "on_demand",
                0.0d,
                1.0d,
                20260919L,
                512,
                "synthetic-prompt",
                scenario.evidenceFingerprint(),
                usageObserved
                        ? 200
                        : null,
                Duration.ofMillis(ordinal),
                usageObserved
                        ? 100L + ordinal
                        : null,
                usageObserved
                        ? 20L + ordinal
                        : null,
                usageObserved
                        ? 120L + (2L * ordinal)
                        : null,
                usageObserved
                        ? "stop"
                        : null,
                Instant.EPOCH.plusSeconds(ordinal)
        );
    }

    private ContractChangeProposal canonical(
            LlmReliabilityScenario scenario
    ) {
        return scenario.groundTruth()
                .toCanonicalProposal(
                        scenario.scenarioId());
    }

    private LlmReliabilityScenario scenario(
            String scenarioId
    ) {
        return scenarios.stream()
                .filter(scenario ->
                        scenario.scenarioId()
                                .equals(scenarioId))
                .findFirst()
                .orElseThrow();
    }
}
