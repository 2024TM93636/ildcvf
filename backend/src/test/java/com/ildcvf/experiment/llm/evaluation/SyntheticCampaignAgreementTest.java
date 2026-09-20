package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyntheticCampaignAgreementTest {

    @Test
    void pairwiseMetricsUseOnlySuccessfullyParsedProposals() {
        List<LlmReliabilityScenario> scenarios =
                new LlmScenarioCatalog()
                        .scenarios();

        AgreementCalculator calculator =
                new AgreementCalculator();

        List<ScenarioAgreementMetrics> results =
                new ArrayList<>();

        /*
         * Controlled agreement fixture:
         *
         * scenario 1: 4 canonical -> 6/6 all metrics
         * scenario 2: 3 canonical + 1 method mutation
         *             exact = 3/6
         *             other three metrics = 6/6
         * scenario 3: 1 parsed -> N/A
         */
        LlmReliabilityScenario first =
                scenarios.get(0);

        ContractChangeProposal firstCanonical =
                canonical(first);

        results.add(
                calculator.calculate(
                        first.scenarioId(),
                        List.of(
                                firstCanonical,
                                copy(firstCanonical, "p2"),
                                copy(firstCanonical, "p3"),
                                copy(firstCanonical, "p4"))));

        LlmReliabilityScenario second =
                scenarios.get(1);

        ContractChangeProposal secondCanonical =
                canonical(second);

        ContractChangeProposal methodMutation =
                new ContractChangeProposal(
                        "p4",
                        secondCanonical.sourceTrafficEventId(),
                        secondCanonical.sourceFindingId(),
                        secondCanonical.driftType(),
                        secondCanonical.changeKind(),
                        "INVENTED",
                        secondCanonical.runtimePath(),
                        secondCanonical.matchedOpenApiPath(),
                        secondCanonical.targetFieldPath(),
                        secondCanonical.expected(),
                        secondCanonical.observed(),
                        secondCanonical.proposedValue(),
                        "different rationale");

        results.add(
                calculator.calculate(
                        second.scenarioId(),
                        List.of(
                                secondCanonical,
                                copy(secondCanonical, "p2"),
                                copy(secondCanonical, "p3"),
                                methodMutation)));

        LlmReliabilityScenario third =
                scenarios.get(2);

        results.add(
                calculator.calculate(
                        third.scenarioId(),
                        List.of(canonical(third))));

        CampaignAgreementMetrics campaign =
                calculator.calculateCampaign(
                        results);

        assertEquals(
                9,
                campaign
                        .exactStructuredPairwiseAgreement()
                        .numerator());

        assertEquals(
                12,
                campaign
                        .exactStructuredPairwiseAgreement()
                        .denominator());

        assertEquals(
                12,
                campaign
                        .changeKindAgreement()
                        .numerator());

        assertEquals(
                12,
                campaign
                        .changeKindAgreement()
                        .denominator());

        assertEquals(
                12,
                campaign
                        .targetFieldAgreement()
                        .numerator());

        assertEquals(
                12,
                campaign
                        .proposedValueAgreement()
                        .numerator());
    }

    private ContractChangeProposal canonical(
            LlmReliabilityScenario scenario
    ) {
        return scenario.groundTruth()
                .toCanonicalProposal(
                        scenario.scenarioId());
    }

    private ContractChangeProposal copy(
            ContractChangeProposal source,
            String proposalId
    ) {
        return new ContractChangeProposal(
                proposalId,
                source.sourceTrafficEventId(),
                source.sourceFindingId(),
                source.driftType(),
                source.changeKind(),
                source.method(),
                source.runtimePath(),
                source.matchedOpenApiPath(),
                source.targetFieldPath(),
                source.expected(),
                source.observed(),
                source.proposedValue(),
                "different rationale"
        );
    }
}
