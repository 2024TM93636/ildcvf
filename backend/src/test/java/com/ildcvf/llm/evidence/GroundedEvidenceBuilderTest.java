package com.ildcvf.llm.evidence;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.ContractOperation;
import com.ildcvf.drift.domain.DriftAnalysisResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.support.M7TestFixtures;
import org.junit.jupiter.api.Test;
import io.swagger.v3.oas.models.OpenAPI;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroundedEvidenceBuilderTest {

    private final GroundedEvidenceBuilder builder =
            new GroundedEvidenceBuilder();

    @Test
    void buildsGroundedEvidenceForValidDocumentedFinding() {
        DriftFinding finding =
                M7TestFixtures.documentedFinding();

        DriftAnalysisResult analysis =
                M7TestFixtures.analysis(finding);

        GroundedEvidencePackage evidence =
                builder.build(
                        analysis,
                        finding,
                        M7TestFixtures.baseline()
                );

        assertEquals(
                M7TestFixtures.EVENT_ID,
                evidence.trafficEventId()
        );
        assertEquals(
                M7TestFixtures.SEQUENCE,
                evidence.trafficSequence()
        );
        assertEquals(
                finding.findingId(),
                evidence.findingId()
        );
        assertEquals(
                finding.type(),
                evidence.driftType()
        );
        assertEquals(
                finding.severity(),
                evidence.severity()
        );
        assertEquals(
                M7TestFixtures.METHOD,
                evidence.method()
        );
        assertEquals(
                M7TestFixtures.RUNTIME_PATH,
                evidence.runtimePath()
        );
        assertEquals(
                M7TestFixtures.MATCHED_PATH,
                evidence.matchedOpenApiPath()
        );
        assertEquals(
                M7TestFixtures.FIELD_PATH,
                evidence.fieldPath()
        );
        assertEquals(
                M7TestFixtures.EXPECTED,
                evidence.expected()
        );
        assertEquals(
                M7TestFixtures.OBSERVED,
                evidence.observed()
        );

        RelevantContractContext context =
                evidence.relevantContractContext();

        assertEquals("3.1.0", context.openApiVersion());
        assertEquals(M7TestFixtures.METHOD, context.method());
        assertEquals(
                M7TestFixtures.MATCHED_PATH,
                context.pathTemplate()
        );
        assertEquals(
                M7TestFixtures.OPERATION_ID,
                context.operationId()
        );
    }

    @Test
    void inheritsAuthoritativeAnalysisMatchedPathWhenFindingPathIsNull() {
        DriftFinding finding = M7TestFixtures.finding(
                DriftType.RESPONSE_TYPE_MISMATCH,
                DriftSeverity.BREAKING,
                null,
                "$.id",
                "integer",
                "string"
        );

        DriftAnalysisResult analysis =
                M7TestFixtures.analysis(finding);

        GroundedEvidencePackage evidence =
                builder.build(
                        analysis,
                        finding,
                        M7TestFixtures.baseline()
                );

        assertEquals(
                M7TestFixtures.MATCHED_PATH,
                evidence.matchedOpenApiPath()
        );
        assertEquals(
                M7TestFixtures.MATCHED_PATH,
                evidence.relevantContractContext().pathTemplate()
        );
        assertEquals(
                M7TestFixtures.OPERATION_ID,
                evidence.relevantContractContext().operationId()
        );
    }

    @Test
    void rejectsContradictoryMatchedPaths() {
        DriftFinding finding = M7TestFixtures.finding(
                DriftType.UNEXPECTED_REQUEST_FIELD,
                DriftSeverity.WARNING,
                "/api/other",
                M7TestFixtures.FIELD_PATH,
                M7TestFixtures.EXPECTED,
                M7TestFixtures.OBSERVED
        );

        DriftAnalysisResult analysis =
                M7TestFixtures.analysis(finding);

        assertThrows(
                IllegalStateException.class,
                () -> builder.build(
                        analysis,
                        finding,
                        M7TestFixtures.baseline()
                )
        );
    }

    @Test
    void rejectsFindingThatIsNotCompleteMemberOfAnalysis() {
        DriftFinding member =
                M7TestFixtures.documentedFinding();

        DriftFinding substituted = new DriftFinding(
                member.findingId(),
                member.trafficEventId(),
                member.trafficSequence(),
                member.type(),
                member.severity(),
                member.method(),
                member.runtimePath(),
                member.matchedOpenApiPath(),
                member.fieldPath(),
                member.expected(),
                "different-observed-value",
                member.message()
        );

        DriftAnalysisResult analysis =
                M7TestFixtures.analysis(member);

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.build(
                        analysis,
                        substituted,
                        M7TestFixtures.baseline()
                )
        );
    }

    @Test
    void rejectsTrustedEventIdentityContradiction() {
        DriftFinding finding =
                M7TestFixtures.documentedFinding();

        DriftAnalysisResult analysis =
                M7TestFixtures.analysis(
                        finding,
                        M7TestFixtures.MATCHED_PATH,
                        "traffic-different",
                        M7TestFixtures.SEQUENCE,
                        M7TestFixtures.METHOD,
                        M7TestFixtures.RUNTIME_PATH
                );

        assertThrows(
                IllegalStateException.class,
                () -> builder.build(
                        analysis,
                        finding,
                        M7TestFixtures.baseline()
                )
        );
    }

    @Test
    void rejectsDocumentedFindingWhenNoOperationMatches() {
        DriftFinding finding =
                M7TestFixtures.documentedFinding();

        DriftAnalysisResult analysis =
                M7TestFixtures.analysis(finding);

        ContractBaseline baseline = new ContractBaseline(
                "3.1.0",
                List.of(
                        new ContractOperation(
                                "/api/other",
                                M7TestFixtures.METHOD,
                                "otherOperation"
                        )
                ),
                new OpenAPI()
        );

        assertThrows(
                IllegalStateException.class,
                () -> builder.build(
                        analysis,
                        finding,
                        baseline
                )
        );
    }

    @Test
    void rejectsDocumentedFindingWhenMultipleOperationsMatch() {
        DriftFinding finding =
                M7TestFixtures.documentedFinding();

        DriftAnalysisResult analysis =
                M7TestFixtures.analysis(finding);

        ContractOperation first =
                M7TestFixtures.documentedOperation();

        ContractOperation second = new ContractOperation(
                M7TestFixtures.MATCHED_PATH,
                M7TestFixtures.METHOD,
                "duplicateCreateResident"
        );

        ContractBaseline baseline = new ContractBaseline(
                "3.1.0",
                List.of(first, second),
                new OpenAPI()
        );

        assertThrows(
                IllegalStateException.class,
                () -> builder.build(
                        analysis,
                        finding,
                        baseline
                )
        );
    }

    @Test
    void buildsUndocumentedOperationWithoutFabricatedContractOperation() {
        DriftFinding finding = M7TestFixtures.finding(
                DriftType.UNDOCUMENTED_OPERATION,
                DriftSeverity.WARNING,
                null,
                null,
                "operation not documented",
                M7TestFixtures.METHOD
                        + " "
                        + M7TestFixtures.RUNTIME_PATH
        );

        DriftAnalysisResult analysis =
                M7TestFixtures.analysis(
                        finding,
                        null,
                        M7TestFixtures.EVENT_ID,
                        M7TestFixtures.SEQUENCE,
                        M7TestFixtures.METHOD,
                        M7TestFixtures.RUNTIME_PATH
                );

        GroundedEvidencePackage evidence =
                builder.build(
                        analysis,
                        finding,
                        M7TestFixtures.baseline()
                );

        assertNull(evidence.matchedOpenApiPath());
        assertNull(evidence.fieldPath());
        assertNull(
                evidence.relevantContractContext().pathTemplate()
        );
        assertNull(
                evidence.relevantContractContext().operationId()
        );
        assertEquals(
                M7TestFixtures.METHOD,
                evidence.relevantContractContext().method()
        );
    }

    @Test
    void rejectsDocumentedFindingWithoutMatchedOperationPath() {
        DriftFinding finding = M7TestFixtures.finding(
                DriftType.REQUEST_TYPE_MISMATCH,
                DriftSeverity.BREAKING,
                null,
                "$.status",
                "integer",
                "string"
        );

        DriftAnalysisResult analysis =
                M7TestFixtures.analysis(
                        finding,
                        null,
                        M7TestFixtures.EVENT_ID,
                        M7TestFixtures.SEQUENCE,
                        M7TestFixtures.METHOD,
                        M7TestFixtures.RUNTIME_PATH
                );

        assertThrows(
                IllegalStateException.class,
                () -> builder.build(
                        analysis,
                        finding,
                        M7TestFixtures.baseline()
                )
        );
    }
}