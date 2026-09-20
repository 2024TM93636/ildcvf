package com.ildcvf.experiment.llm.evaluation;

import java.util.List;
import java.util.Objects;

public record CampaignAgreementMetrics(
        List<ScenarioAgreementMetrics> scenarios,
        AgreementMetric exactStructuredPairwiseAgreement,
        AgreementMetric changeKindAgreement,
        AgreementMetric targetFieldAgreement,
        AgreementMetric proposedValueAgreement
) {

    public CampaignAgreementMetrics {
        Objects.requireNonNull(
                scenarios,
                "scenarios must not be null");

        scenarios = List.copyOf(scenarios);

        Objects.requireNonNull(
                exactStructuredPairwiseAgreement);

        Objects.requireNonNull(
                changeKindAgreement);

        Objects.requireNonNull(
                targetFieldAgreement);

        Objects.requireNonNull(
                proposedValueAgreement);
    }
}
