package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.groq.GroqProviderAttempt;
import com.ildcvf.experiment.llm.groq.ProposalParseResult;
import com.ildcvf.experiment.llm.groq.ProposalParseStatus;
import com.ildcvf.experiment.llm.groq.ProviderCallStatus;
import com.ildcvf.llm.evidence.EvidenceFingerprint;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.validation.ContractChangeProposalValidator;
import com.ildcvf.llm.validation.ProposalValidationResult;

import java.util.Objects;

public final class AttemptEvaluator {

    private final ContractChangeProposalValidator validator;
    private final GroundTruthComparator comparator;
    private final HallucinationClassifier hallucinationClassifier;

    public AttemptEvaluator() {
        this(
                new ContractChangeProposalValidator(),
                new GroundTruthComparator(),
                new HallucinationClassifier()
        );
    }

    AttemptEvaluator(
            ContractChangeProposalValidator validator,
            GroundTruthComparator comparator,
            HallucinationClassifier hallucinationClassifier
    ) {
        this.validator =
                Objects.requireNonNull(validator);

        this.comparator =
                Objects.requireNonNull(comparator);

        this.hallucinationClassifier =
                Objects.requireNonNull(
                        hallucinationClassifier);
    }

    public AttemptEvaluation evaluate(
            LlmReliabilityScenario scenario,
            int repetition,
            GroqProviderAttempt providerAttempt
    ) {
        Objects.requireNonNull(
                scenario,
                "scenario must not be null");

        Objects.requireNonNull(
                providerAttempt,
                "providerAttempt must not be null");

        AttemptIdentity identity =
                new AttemptIdentity(
                        scenario.scenarioId(),
                        repetition);

        String calculatedFingerprint =
                EvidenceFingerprint.sha256(
                        scenario.evidence());

        if (!calculatedFingerprint.equals(
                scenario.evidenceFingerprint())) {
            throw new IllegalStateException(
                    "Scenario evidence fingerprint mismatch: "
                            + scenario.scenarioId());
        }

        String providerFingerprint =
                providerAttempt.metadata()
                        .evidenceSha256();

        if (providerFingerprint == null
                || !calculatedFingerprint.equals(
                providerFingerprint)) {
            throw new IllegalStateException(
                    "Provider attempt evidence fingerprint "
                            + "does not match scenario: "
                            + scenario.scenarioId());
        }

        if (providerAttempt.status()
                != ProviderCallStatus.SUCCESS) {

            ProposalParseStatus parseStatus =
                    providerAttempt.proposalParseResult()
                            == null
                            ? null
                            : providerAttempt
                            .proposalParseResult()
                            .status();

            return notApplicable(
                    identity,
                    calculatedFingerprint,
                    providerAttempt,
                    parseStatus);
        }

        ProposalParseResult parseResult =
                providerAttempt.proposalParseResult();

        if (parseResult == null
                || parseResult.status()
                != ProposalParseStatus.SUCCESS
                || parseResult.proposal() == null) {

            ProposalParseStatus parseStatus =
                    parseResult == null
                            ? null
                            : parseResult.status();

            return notApplicable(
                    identity,
                    calculatedFingerprint,
                    providerAttempt,
                    parseStatus);
        }

        ContractChangeProposal proposal =
                parseResult.proposal();

        ProposalValidationResult validation =
                validator.validate(
                        scenario.evidence(),
                        proposal);

        ValidatorEvaluation validatorEvaluation =
                ValidatorEvaluation.applicable(
                        validation.valid(),
                        validation.errors());

        GroundTruthComparison comparison =
                comparator.compare(
                        scenario.groundTruth(),
                        proposal);

        HallucinationEvaluation hallucination =
                hallucinationClassifier.classify(
                        validation.errors());

        return new AttemptEvaluation(
                identity,
                calculatedFingerprint,
                providerAttempt.status(),
                parseResult.status(),
                validatorEvaluation,
                GroundTruthEvaluation.applicable(
                        comparison),
                hallucination,
                providerAttempt.metadata()
        );
    }

    private AttemptEvaluation notApplicable(
            AttemptIdentity identity,
            String evidenceFingerprint,
            GroqProviderAttempt providerAttempt,
            ProposalParseStatus parseStatus
    ) {
        return new AttemptEvaluation(
                identity,
                evidenceFingerprint,
                providerAttempt.status(),
                parseStatus,
                ValidatorEvaluation.notApplicable(),
                GroundTruthEvaluation.notApplicable(),
                HallucinationEvaluation.notApplicable(),
                providerAttempt.metadata()
        );
    }
}
