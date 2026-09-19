package com.ildcvf.evaluation;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.drift.domain.EvaluationStatus;

import java.util.List;

public final class ResidentDriftEvaluationDataset {

    private ResidentDriftEvaluationDataset() {
    }

    public static List<DriftEvaluationScenario> scenarios() {
        return List.of(
                baselineValidPost(),
                baselineValidGetItem(),
                baselineValidGetCollection(),

                undocumentedOperation(),
                requestUnexpectedField(),
                requestMissingRequiredField(),
                requestTypeMismatch(),
                requestEnumMismatch(),

                responseUndocumentedStatus(),
                responseMissingRequiredField(),
                responseTypeMismatch(),
                responseEnumMismatch(),

                collectionItemTypeMismatch(),

                requestTruncated(),
                responseTruncated(),
                malformedRequestJson(),
                malformedResponseJson(),

                upstreamNotReached()
        );
    }

    private static DriftEvaluationScenario baselineValidPost() {
        return evaluated(
                "evaluation-001",
                "Valid documented POST request and response.",
                ResidentTrafficEventFixtures.baselineValidPost());
    }

    private static DriftEvaluationScenario baselineValidGetItem() {
        return evaluated(
                "evaluation-002",
                "Valid documented GET item request and response.",
                ResidentTrafficEventFixtures.baselineValidGetItem());
    }

    private static DriftEvaluationScenario baselineValidGetCollection() {
        return evaluated(
                "evaluation-003",
                "Valid documented GET collection request and response.",
                ResidentTrafficEventFixtures.baselineValidGetCollection());
    }

    private static DriftEvaluationScenario undocumentedOperation() {
        return evaluated(
                "evaluation-004",
                "Runtime operation is not documented.",
                ResidentTrafficEventFixtures.undocumentedOperation(),
                key(
                        DriftType.UNDOCUMENTED_OPERATION,
                        null));
    }

    private static DriftEvaluationScenario requestUnexpectedField() {
        return evaluated(
                "evaluation-005",
                "Request contains an undocumented field.",
                ResidentTrafficEventFixtures.requestUnexpectedField(),
                key(
                        DriftType.UNEXPECTED_REQUEST_FIELD,
                        "/unexpectedField"));
    }

    private static DriftEvaluationScenario requestMissingRequiredField() {
        return evaluated(
                "evaluation-006",
                "Request omits a required field.",
                ResidentTrafficEventFixtures.requestMissingRequiredField(),
                key(
                        DriftType.MISSING_REQUIRED_REQUEST_FIELD,
                        "/firstName"));
    }

    private static DriftEvaluationScenario requestTypeMismatch() {
        return evaluated(
                "evaluation-007",
                "Request field has the wrong JSON type.",
                ResidentTrafficEventFixtures.requestTypeMismatch(),
                key(
                        DriftType.REQUEST_TYPE_MISMATCH,
                        "/firstName"));
    }

    private static DriftEvaluationScenario requestEnumMismatch() {
        return evaluated(
                "evaluation-008",
                "Request enum value is outside the documented enum.",
                ResidentTrafficEventFixtures.requestEnumMismatch(),
                key(
                        DriftType.REQUEST_ENUM_MISMATCH,
                        "/status"));
    }

    private static DriftEvaluationScenario responseUndocumentedStatus() {
        return evaluated(
                "evaluation-009",
                "Response uses a status code absent from the documented response contract.",
                ResidentTrafficEventFixtures.responseUndocumentedStatus(),
                key(
                        DriftType.UNDOCUMENTED_RESPONSE_STATUS,
                        null));
    }

    private static DriftEvaluationScenario responseMissingRequiredField() {
        return evaluated(
                "evaluation-010",
                "Response omits a required field.",
                ResidentTrafficEventFixtures.responseMissingRequiredField(),
                key(
                        DriftType.MISSING_REQUIRED_RESPONSE_FIELD,
                        "id"));
    }

    private static DriftEvaluationScenario responseTypeMismatch() {
        return evaluated(
                "evaluation-011",
                "Response field has the wrong JSON type.",
                ResidentTrafficEventFixtures.responseTypeMismatch(),
                key(
                        DriftType.RESPONSE_TYPE_MISMATCH,
                        "id"));
    }

    private static DriftEvaluationScenario responseEnumMismatch() {
        return evaluated(
                "evaluation-012",
                "Response enum value is outside the documented enum.",
                ResidentTrafficEventFixtures.responseEnumMismatch(),
                key(
                        DriftType.RESPONSE_ENUM_MISMATCH,
                        "status"));
    }

    private static DriftEvaluationScenario collectionItemTypeMismatch() {
        return evaluated(
                "evaluation-013",
                "Collection response contains an item with a type mismatch.",
                ResidentTrafficEventFixtures.collectionItemTypeMismatch(),
                key(
                        DriftType.RESPONSE_TYPE_MISMATCH,
                        "[0].id"));
    }

    private static DriftEvaluationScenario requestTruncated() {
        return new DriftEvaluationScenario(
                "evaluation-014",
                "Request body is truncated and cannot be completely evaluated.",
                ResidentTrafficEventFixtures.requestTruncated(),
                EvaluationStatus.PARTIALLY_EVALUATED,
                List.of());
    }

    private static DriftEvaluationScenario responseTruncated() {
        return new DriftEvaluationScenario(
                "evaluation-015",
                "Response body is truncated and cannot be completely evaluated.",
                ResidentTrafficEventFixtures.responseTruncated(),
                EvaluationStatus.PARTIALLY_EVALUATED,
                List.of());
    }

    private static DriftEvaluationScenario malformedRequestJson() {
        return new DriftEvaluationScenario(
                "evaluation-016",
                "Request body contains malformed JSON.",
                ResidentTrafficEventFixtures.malformedRequestJson(),
                EvaluationStatus.PARTIALLY_EVALUATED,
                List.of());
    }

    private static DriftEvaluationScenario malformedResponseJson() {
        return new DriftEvaluationScenario(
                "evaluation-017",
                "Response body contains malformed JSON.",
                ResidentTrafficEventFixtures.malformedResponseJson(),
                EvaluationStatus.PARTIALLY_EVALUATED,
                List.of());
    }

    private static DriftEvaluationScenario upstreamNotReached() {
        return new DriftEvaluationScenario(
                "evaluation-018",
                "Upstream service was not reached.",
                ResidentTrafficEventFixtures.upstreamNotReached(),
                EvaluationStatus.NOT_EVALUATED,
                List.of());
    }

    private static DriftEvaluationScenario evaluated(
            String scenarioId,
            String description,
            com.ildcvf.traffic.domain.TrafficEvent event,
            DriftFindingKey... keys) {

        return new DriftEvaluationScenario(
                scenarioId,
                description,
                event,
                EvaluationStatus.EVALUATED,
                List.of(keys));
    }

    private static DriftFindingKey key(
            DriftType type,
            String fieldPath) {

        return new DriftFindingKey(
                type,
                fieldPath);
    }
}
