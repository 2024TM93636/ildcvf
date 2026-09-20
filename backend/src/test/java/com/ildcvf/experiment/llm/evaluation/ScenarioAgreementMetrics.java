package com.ildcvf.experiment.llm.evaluation;

import java.util.Objects;

public record ScenarioAgreementMetrics(
        String scenarioId,
        int parsedProposalCount,
        AgreementMetric exactStructuredPairwiseAgreement,
        AgreementMetric changeKindAgreement,
        AgreementMetric targetFieldAgreement,
        AgreementMetric proposedValueAgreement
) {

    public ScenarioAgreementMetrics {
        Objects.requireNonNull(
                scenarioId,
                "scenarioId must not be null");

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
