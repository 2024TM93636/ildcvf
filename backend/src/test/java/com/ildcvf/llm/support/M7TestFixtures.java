package com.ildcvf.llm.support;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.ContractOperation;
import com.ildcvf.drift.domain.DriftAnalysisResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.drift.domain.EvaluationStatus;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.evidence.RelevantContractContext;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeKindMapping;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import io.swagger.v3.oas.models.OpenAPI;

import java.util.List;

public final class M7TestFixtures {

    public static final String EVENT_ID = "traffic-00000101";
    public static final long SEQUENCE = 101L;

    public static final String METHOD = "POST";
    public static final String RUNTIME_PATH = "/api/residents";
    public static final String MATCHED_PATH = "/api/residents";
    public static final String OPERATION_ID = "createResident";

    public static final String FIELD_PATH = "$.nickname";
    public static final String EXPECTED = "field not documented";
    public static final String OBSERVED = "string";

    private M7TestFixtures() {
    }

    public static ContractOperation documentedOperation() {
        return new ContractOperation(
                MATCHED_PATH,
                METHOD,
                OPERATION_ID
        );
    }

    public static ContractBaseline baseline() {
        return new ContractBaseline(
                "3.1.0",
                List.of(documentedOperation()),
                new OpenAPI()
        );
    }

    public static DriftFinding documentedFinding() {
        return finding(
                DriftType.UNEXPECTED_REQUEST_FIELD,
                DriftSeverity.WARNING,
                MATCHED_PATH,
                FIELD_PATH,
                EXPECTED,
                OBSERVED
        );
    }

    public static DriftFinding finding(
            DriftType type,
            DriftSeverity severity,
            String matchedOpenApiPath,
            String fieldPath,
            String expected,
            String observed
    ) {
        return new DriftFinding(
                EVENT_ID + ":" + type + ":" + String.valueOf(fieldPath),
                EVENT_ID,
                SEQUENCE,
                type,
                severity,
                METHOD,
                RUNTIME_PATH,
                matchedOpenApiPath,
                fieldPath,
                expected,
                observed,
                "Controlled M7 finding for " + type
        );
    }

    public static DriftAnalysisResult analysis(DriftFinding finding) {
        return analysis(
                finding,
                MATCHED_PATH,
                EVENT_ID,
                SEQUENCE,
                METHOD,
                RUNTIME_PATH
        );
    }

    public static DriftAnalysisResult analysis(
            DriftFinding finding,
            String matchedOpenApiPath,
            String eventId,
            long sequence,
            String method,
            String runtimePath
    ) {
        return new DriftAnalysisResult(
                eventId,
                sequence,
                EvaluationStatus.EVALUATED,
                method,
                runtimePath,
                matchedOpenApiPath,
                List.of(finding),
                "Controlled M7 analysis"
        );
    }

    public static GroundedEvidencePackage evidence(
            DriftType driftType
    ) {
        return evidence(
                driftType,
                defaultFieldPath(driftType),
                defaultExpected(driftType),
                defaultObserved(driftType)
        );
    }

    public static GroundedEvidencePackage evidence(
            DriftType driftType,
            String fieldPath,
            String expected,
            String observed
    ) {
        boolean undocumentedOperation =
                driftType == DriftType.UNDOCUMENTED_OPERATION;

        String matchedPath =
                undocumentedOperation ? null : MATCHED_PATH;

        RelevantContractContext context =
                new RelevantContractContext(
                        "3.1.0",
                        METHOD,
                        matchedPath,
                        undocumentedOperation ? null : OPERATION_ID
                );

        return new GroundedEvidencePackage(
                EVENT_ID,
                SEQUENCE,
                EVENT_ID + ":" + driftType + ":" + String.valueOf(fieldPath),
                driftType,
                defaultSeverity(driftType),
                METHOD,
                RUNTIME_PATH,
                matchedPath,
                fieldPath,
                expected,
                observed,
                "Controlled grounded evidence for " + driftType,
                context
        );
    }

    public static ContractChangeProposal validProposal(
            GroundedEvidencePackage evidence
    ) {
        ContractChangeKind changeKind =
                ContractChangeKindMapping.allowedChangeKind(
                        evidence.driftType()
                );

        return new ContractChangeProposal(
                "proposal-" + evidence.findingId(),
                evidence.trafficEventId(),
                evidence.findingId(),
                evidence.driftType(),
                changeKind,
                evidence.method(),
                evidence.runtimePath(),
                evidence.matchedOpenApiPath(),
                evidence.fieldPath(),
                evidence.expected(),
                evidence.observed(),
                proposedValue(evidence, changeKind),
                "Grounded deterministic M7 test proposal"
        );
    }

    public static String proposedValue(
            GroundedEvidencePackage evidence,
            ContractChangeKind changeKind
    ) {
        return switch (changeKind) {
            case ADD_OPERATION ->
                    evidence.method() + " " + evidence.runtimePath();

            case RELAX_REQUEST_REQUIRED_FIELD,
                 RELAX_RESPONSE_REQUIRED_FIELD ->
                    "optional";

            case ADD_REQUEST_FIELD,
                 ADD_RESPONSE_FIELD,
                 CHANGE_REQUEST_FIELD_TYPE,
                 CHANGE_RESPONSE_FIELD_TYPE,
                 ADD_REQUEST_ENUM_VALUE,
                 ADD_RESPONSE_ENUM_VALUE,
                 ADD_RESPONSE_STATUS ->
                    evidence.observed();
        };
    }

    private static String defaultFieldPath(DriftType type) {
        return switch (type) {
            case UNDOCUMENTED_OPERATION -> null;
            case UNDOCUMENTED_RESPONSE_STATUS -> "418";
            default -> "$.status";
        };
    }

    private static String defaultExpected(DriftType type) {
        return switch (type) {
            case UNDOCUMENTED_OPERATION ->
                    "operation not documented";

            case UNEXPECTED_REQUEST_FIELD,
                 UNEXPECTED_RESPONSE_FIELD ->
                    "field not documented";

            case MISSING_REQUIRED_REQUEST_FIELD,
                 MISSING_REQUIRED_RESPONSE_FIELD ->
                    "required";

            case REQUEST_TYPE_MISMATCH,
                 RESPONSE_TYPE_MISMATCH ->
                    "integer";

            case REQUEST_ENUM_MISMATCH,
                 RESPONSE_ENUM_MISMATCH ->
                    "ACTIVE|INACTIVE|DISCHARGED";

            case UNDOCUMENTED_RESPONSE_STATUS ->
                    "documented response status";
        };
    }

    private static String defaultObserved(DriftType type) {
        return switch (type) {
            case UNDOCUMENTED_OPERATION ->
                    METHOD + " " + RUNTIME_PATH;

            case UNEXPECTED_REQUEST_FIELD,
                 UNEXPECTED_RESPONSE_FIELD ->
                    "string";

            case MISSING_REQUIRED_REQUEST_FIELD,
                 MISSING_REQUIRED_RESPONSE_FIELD ->
                    "missing";

            case REQUEST_TYPE_MISMATCH,
                 RESPONSE_TYPE_MISMATCH ->
                    "string";

            case REQUEST_ENUM_MISMATCH,
                 RESPONSE_ENUM_MISMATCH ->
                    "PENDING";

            case UNDOCUMENTED_RESPONSE_STATUS ->
                    "418";
        };
    }

    private static DriftSeverity defaultSeverity(DriftType type) {
        return switch (type) {
            case UNDOCUMENTED_OPERATION,
                 UNEXPECTED_REQUEST_FIELD,
                 UNDOCUMENTED_RESPONSE_STATUS,
                 UNEXPECTED_RESPONSE_FIELD ->
                    DriftSeverity.WARNING;

            default ->
                    DriftSeverity.BREAKING;
        };
    }
}