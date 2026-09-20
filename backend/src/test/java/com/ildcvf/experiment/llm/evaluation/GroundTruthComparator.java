package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.experiment.llm.LlmProposalGroundTruth;
import com.ildcvf.llm.proposal.ContractChangeProposal;

import java.util.List;
import java.util.Objects;

public final class GroundTruthComparator {

    public GroundTruthComparison compare(
            LlmProposalGroundTruth truth,
            ContractChangeProposal proposal
    ) {
        Objects.requireNonNull(
                truth,
                "truth must not be null");

        Objects.requireNonNull(
                proposal,
                "proposal must not be null");

        List<FieldComparison> fields =
                List.of(
                        FieldComparison.compare(
                                ScoredProposalField
                                        .SOURCE_TRAFFIC_EVENT_ID,
                                truth.sourceTrafficEventId(),
                                proposal.sourceTrafficEventId()),

                        FieldComparison.compare(
                                ScoredProposalField
                                        .SOURCE_FINDING_ID,
                                truth.sourceFindingId(),
                                proposal.sourceFindingId()),

                        FieldComparison.compare(
                                ScoredProposalField.DRIFT_TYPE,
                                truth.driftType(),
                                proposal.driftType()),

                        FieldComparison.compare(
                                ScoredProposalField.CHANGE_KIND,
                                truth.changeKind(),
                                proposal.changeKind()),

                        FieldComparison.compare(
                                ScoredProposalField.METHOD,
                                truth.method(),
                                proposal.method()),

                        FieldComparison.compare(
                                ScoredProposalField.RUNTIME_PATH,
                                truth.runtimePath(),
                                proposal.runtimePath()),

                        FieldComparison.compare(
                                ScoredProposalField
                                        .MATCHED_OPEN_API_PATH,
                                truth.matchedOpenApiPath(),
                                proposal.matchedOpenApiPath()),

                        FieldComparison.compare(
                                ScoredProposalField
                                        .TARGET_FIELD_PATH,
                                truth.targetFieldPath(),
                                proposal.targetFieldPath()),

                        FieldComparison.compare(
                                ScoredProposalField.EXPECTED,
                                truth.expected(),
                                proposal.expected()),

                        FieldComparison.compare(
                                ScoredProposalField.OBSERVED,
                                truth.observed(),
                                proposal.observed()),

                        FieldComparison.compare(
                                ScoredProposalField.PROPOSED_VALUE,
                                truth.proposedValue(),
                                proposal.proposedValue())
                );

        int matched =
                (int) fields.stream()
                        .filter(FieldComparison::match)
                        .count();

        return new GroundTruthComparison(
                fields,
                matched,
                GroundTruthComparison.FROZEN_FIELD_COUNT,
                matched
                        / (double)
                        GroundTruthComparison.FROZEN_FIELD_COUNT,
                matched
                        == GroundTruthComparison.FROZEN_FIELD_COUNT
        );
    }
}