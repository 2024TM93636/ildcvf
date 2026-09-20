package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttemptIdentityAndGroundTruthTest {

    @Test
    void scoredFieldSetIsFrozenAtExactlyEleven() {
        assertEquals(
                11,
                ScoredProposalField.values().length);
    }

    @Test
    void deterministicAttemptIdentityUsesScenarioAndRepetition() {
        AttemptIdentity identity =
                new AttemptIdentity(
                        "llm-001-undocumented-operation",
                        1);

        assertEquals(
                "llm-001-undocumented-operation-r1",
                identity.attemptId());
    }

    @Test
    void unknownScenarioAndInvalidRepetitionAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AttemptIdentity(
                        "not-a-frozen-scenario",
                        1));

        assertThrows(
                IllegalArgumentException.class,
                () -> new AttemptIdentity(
                        "llm-001-undocumented-operation",
                        0));

        assertThrows(
                IllegalArgumentException.class,
                () -> new AttemptIdentity(
                        "llm-001-undocumented-operation",
                        5));
    }

    @Test
    void exactCanonicalProposalMatchesAllElevenFields() {
        LlmReliabilityScenario scenario =
                new LlmScenarioCatalog()
                        .scenarios()
                        .getFirst();

        ContractChangeProposal proposal =
                scenario.groundTruth()
                        .toCanonicalProposal(
                                scenario.scenarioId());

        GroundTruthComparison comparison =
                new GroundTruthComparator()
                        .compare(
                                scenario.groundTruth(),
                                proposal);

        assertEquals(
                11,
                comparison.matchedFieldCount());

        assertEquals(
                11,
                comparison.totalFieldCount());

        assertEquals(
                1.0d,
                comparison.fieldAccuracy());

        assertTrue(
                comparison.exactMatch());
    }

    @Test
    void comparisonIsStrictAndDoesNotTrimOrNormalize() {
        LlmReliabilityScenario scenario =
                new LlmScenarioCatalog()
                        .scenarios()
                        .get(1);

        ContractChangeProposal canonical =
                scenario.groundTruth()
                        .toCanonicalProposal(
                                scenario.scenarioId());

        ContractChangeProposal altered =
                new ContractChangeProposal(
                        canonical.proposalId(),
                        canonical.sourceTrafficEventId(),
                        canonical.sourceFindingId(),
                        canonical.driftType(),
                        canonical.changeKind(),
                        canonical.method().toLowerCase(),
                        canonical.runtimePath(),
                        canonical.matchedOpenApiPath(),
                        canonical.targetFieldPath(),
                        canonical.expected(),
                        canonical.observed(),
                        canonical.proposedValue(),
                        canonical.rationale());

        GroundTruthComparison comparison =
                new GroundTruthComparator()
                        .compare(
                                scenario.groundTruth(),
                                altered);

        assertEquals(
                10,
                comparison.matchedFieldCount());

        assertFalse(
                comparison.exactMatch());

        FieldComparison method =
                comparison.fields()
                        .stream()
                        .filter(field ->
                                field.field()
                                        == ScoredProposalField.METHOD)
                        .findFirst()
                        .orElseThrow();

        assertFalse(method.match());
    }

    @Test
    void campaignScheduleContainsExactlyFortyUniqueAttempts() {
        List<AttemptIdentity> schedule =
                new CampaignSchedule()
                        .create(
                                new LlmScenarioCatalog()
                                        .scenarios());

        assertEquals(
                40,
                schedule.size());

        assertEquals(
                40,
                schedule.stream()
                        .map(AttemptIdentity::attemptId)
                        .distinct()
                        .count());

        assertEquals(
                10,
                schedule.stream()
                        .map(AttemptIdentity::scenarioId)
                        .distinct()
                        .count());

        for (String scenarioId
                : schedule.stream()
                .map(AttemptIdentity::scenarioId)
                .distinct()
                .toList()) {

            assertEquals(
                    4,
                    schedule.stream()
                            .filter(attempt ->
                                    attempt.scenarioId()
                                            .equals(scenarioId))
                            .count());
        }
    }
}
