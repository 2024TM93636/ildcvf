package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.llm.proposal.ContractChangeProposal;

import java.util.List;
import java.util.Objects;

public final class AgreementCalculator {

    public ScenarioAgreementMetrics calculate(
            String scenarioId,
            List<ContractChangeProposal> proposals
    ) {
        Objects.requireNonNull(
                scenarioId,
                "scenarioId must not be null");

        Objects.requireNonNull(
                proposals,
                "proposals must not be null");

        List<ContractChangeProposal> immutable =
                List.copyOf(proposals);

        long denominator =
                pairCount(immutable.size());

        long exact = 0;
        long changeKind = 0;
        long targetField = 0;
        long proposedValue = 0;

        for (int left = 0;
             left < immutable.size();
             left++) {

            for (int right = left + 1;
                 right < immutable.size();
                 right++) {

                ContractChangeProposal first =
                        immutable.get(left);

                ContractChangeProposal second =
                        immutable.get(right);

                if (structuredEqual(first, second)) {
                    exact++;
                }

                if (Objects.equals(
                        first.changeKind(),
                        second.changeKind())) {
                    changeKind++;
                }

                if (Objects.equals(
                        first.targetFieldPath(),
                        second.targetFieldPath())) {
                    targetField++;
                }

                if (Objects.equals(
                        first.proposedValue(),
                        second.proposedValue())) {
                    proposedValue++;
                }
            }
        }

        return new ScenarioAgreementMetrics(
                scenarioId,
                immutable.size(),
                AgreementMetric.of(
                        exact,
                        denominator),
                AgreementMetric.of(
                        changeKind,
                        denominator),
                AgreementMetric.of(
                        targetField,
                        denominator),
                AgreementMetric.of(
                        proposedValue,
                        denominator)
        );
    }

    public CampaignAgreementMetrics calculateCampaign(
            List<ScenarioAgreementMetrics> scenarios
    ) {
        Objects.requireNonNull(
                scenarios,
                "scenarios must not be null");

        List<ScenarioAgreementMetrics> immutable =
                List.copyOf(scenarios);

        return new CampaignAgreementMetrics(
                immutable,
                sum(
                        immutable,
                        MetricSelector.EXACT),
                sum(
                        immutable,
                        MetricSelector.CHANGE_KIND),
                sum(
                        immutable,
                        MetricSelector.TARGET_FIELD),
                sum(
                        immutable,
                        MetricSelector.PROPOSED_VALUE)
        );
    }

    private AgreementMetric sum(
            List<ScenarioAgreementMetrics> scenarios,
            MetricSelector selector
    ) {
        long numerator = 0;
        long denominator = 0;

        for (ScenarioAgreementMetrics scenario
                : scenarios) {

            AgreementMetric metric =
                    switch (selector) {
                        case EXACT ->
                                scenario
                                        .exactStructuredPairwiseAgreement();

                        case CHANGE_KIND ->
                                scenario
                                        .changeKindAgreement();

                        case TARGET_FIELD ->
                                scenario
                                        .targetFieldAgreement();

                        case PROPOSED_VALUE ->
                                scenario
                                        .proposedValueAgreement();
                    };

            numerator += metric.numerator();
            denominator += metric.denominator();
        }

        return AgreementMetric.of(
                numerator,
                denominator);
    }

    private boolean structuredEqual(
            ContractChangeProposal first,
            ContractChangeProposal second
    ) {
        return Objects.equals(
                first.sourceTrafficEventId(),
                second.sourceTrafficEventId())
                && Objects.equals(
                first.sourceFindingId(),
                second.sourceFindingId())
                && Objects.equals(
                first.driftType(),
                second.driftType())
                && Objects.equals(
                first.changeKind(),
                second.changeKind())
                && Objects.equals(
                first.method(),
                second.method())
                && Objects.equals(
                first.runtimePath(),
                second.runtimePath())
                && Objects.equals(
                first.matchedOpenApiPath(),
                second.matchedOpenApiPath())
                && Objects.equals(
                first.targetFieldPath(),
                second.targetFieldPath())
                && Objects.equals(
                first.expected(),
                second.expected())
                && Objects.equals(
                first.observed(),
                second.observed())
                && Objects.equals(
                first.proposedValue(),
                second.proposedValue());
    }

    private long pairCount(int size) {
        if (size < 2) {
            return 0;
        }

        return ((long) size * (size - 1)) / 2;
    }

    private enum MetricSelector {
        EXACT,
        CHANGE_KIND,
        TARGET_FIELD,
        PROPOSED_VALUE
    }
}
