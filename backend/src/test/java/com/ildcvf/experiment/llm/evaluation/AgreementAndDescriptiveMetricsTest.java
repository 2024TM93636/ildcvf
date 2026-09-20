package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgreementAndDescriptiveMetricsTest {

    @Test
    void fourIdenticalStructuredProposalsProduceSixOfSixPairs() {
        LlmReliabilityScenario scenario =
                new LlmScenarioCatalog()
                        .scenarios()
                        .get(1);

        ContractChangeProposal first =
                proposal(
                        scenario,
                        "p1",
                        "rationale one");

        ContractChangeProposal second =
                proposal(
                        scenario,
                        "p2",
                        "different rationale");

        ContractChangeProposal third =
                proposal(
                        scenario,
                        "p3",
                        "third rationale");

        ContractChangeProposal fourth =
                proposal(
                        scenario,
                        "p4",
                        "fourth rationale");

        ScenarioAgreementMetrics metrics =
                new AgreementCalculator()
                        .calculate(
                                scenario.scenarioId(),
                                List.of(
                                        first,
                                        second,
                                        third,
                                        fourth));

        assertEquals(
                6,
                metrics
                        .exactStructuredPairwiseAgreement()
                        .denominator());

        assertEquals(
                6,
                metrics
                        .exactStructuredPairwiseAgreement()
                        .numerator());

        assertEquals(
                6,
                metrics
                        .changeKindAgreement()
                        .numerator());

        assertEquals(
                6,
                metrics
                        .targetFieldAgreement()
                        .numerator());

        assertEquals(
                6,
                metrics
                        .proposedValueAgreement()
                        .numerator());
    }

    @Test
    void oneParsedProposalMakesAgreementNotApplicable() {
        LlmReliabilityScenario scenario =
                new LlmScenarioCatalog()
                        .scenarios()
                        .get(1);

        ScenarioAgreementMetrics metrics =
                new AgreementCalculator()
                        .calculate(
                                scenario.scenarioId(),
                                List.of(
                                        proposal(
                                                scenario,
                                                "p1",
                                                "r1")));

        assertEquals(
                0,
                metrics
                        .exactStructuredPairwiseAgreement()
                        .denominator());

        assertNull(
                metrics
                        .exactStructuredPairwiseAgreement()
                        .rate());
    }

    private ContractChangeProposal proposal(
            LlmReliabilityScenario scenario,
            String proposalId,
            String rationale
    ) {
        ContractChangeProposal canonical =
                scenario.groundTruth()
                        .toCanonicalProposal(
                                scenario.scenarioId());

        return new ContractChangeProposal(
                proposalId,
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
                rationale
        );
    }
}
