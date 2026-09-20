package com.ildcvf.experiment.llm;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.service.ContractBaselineLoader;
import com.ildcvf.contract.service.ContractOperationMatcher;
import com.ildcvf.contract.service.RequestContractValidator;
import com.ildcvf.contract.service.ResponseBodyContractValidator;
import com.ildcvf.contract.service.ResponseStatusContractValidator;
import com.ildcvf.drift.domain.DriftAnalysisResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.drift.service.DriftAnalysisService;
import com.ildcvf.evaluation.ResidentTrafficEventFixtures;
import com.ildcvf.llm.evidence.EvidenceFingerprint;
import com.ildcvf.llm.evidence.GroundedEvidenceBuilder;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.evidence.RelevantContractContext;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.traffic.domain.TrafficEvent;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class LlmScenarioCatalog {

    private static final Path RESIDENT_BASELINE_PATH =
            Path.of(
                    "..",
                    "datasets",
                    "openapi",
                    "baseline",
                    "resident-api-v1.yaml");

    private final ContractBaselineLoader baselineLoader;
    private final DriftAnalysisService driftAnalysisService;
    private final GroundedEvidenceBuilder evidenceBuilder;

    public LlmScenarioCatalog() {
        this.baselineLoader =
                new ContractBaselineLoader(
                        RESIDENT_BASELINE_PATH.toString());

        ObjectMapper objectMapper = new ObjectMapper();

        this.driftAnalysisService =
                new DriftAnalysisService(
                        baselineLoader,
                        new ContractOperationMatcher(),
                        new RequestContractValidator(objectMapper),
                        new ResponseStatusContractValidator(),
                        new ResponseBodyContractValidator(objectMapper));

        this.evidenceBuilder =
                new GroundedEvidenceBuilder();
    }

    public List<LlmReliabilityScenario> scenarios() {
        return List.of(
                residentScenario(
                        "llm-001-undocumented-operation",
                        "Undocumented runtime operation.",
                        ResidentTrafficEventFixtures
                                .undocumentedOperation(),
                        DriftType.UNDOCUMENTED_OPERATION,
                        ContractChangeKind.ADD_OPERATION),

                residentScenario(
                        "llm-002-request-add-field",
                        "Request contains an undocumented field.",
                        ResidentTrafficEventFixtures
                                .requestUnexpectedField(),
                        DriftType.UNEXPECTED_REQUEST_FIELD,
                        ContractChangeKind.ADD_REQUEST_FIELD),

                residentScenario(
                        "llm-003-request-relax-required",
                        "Request omits a documented required field.",
                        ResidentTrafficEventFixtures
                                .requestMissingRequiredField(),
                        DriftType.MISSING_REQUIRED_REQUEST_FIELD,
                        ContractChangeKind
                                .RELAX_REQUEST_REQUIRED_FIELD),

                residentScenario(
                        "llm-004-request-change-type",
                        "Request field uses a different runtime type.",
                        ResidentTrafficEventFixtures
                                .requestTypeMismatch(),
                        DriftType.REQUEST_TYPE_MISMATCH,
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE),

                residentScenario(
                        "llm-005-request-add-enum",
                        "Request contains an undocumented enum value.",
                        ResidentTrafficEventFixtures
                                .requestEnumMismatch(),
                        DriftType.REQUEST_ENUM_MISMATCH,
                        ContractChangeKind.ADD_REQUEST_ENUM_VALUE),

                residentScenario(
                        "llm-006-response-add-status",
                        "Response uses an undocumented status code.",
                        ResidentTrafficEventFixtures
                                .responseUndocumentedStatus(),
                        DriftType.UNDOCUMENTED_RESPONSE_STATUS,
                        ContractChangeKind.ADD_RESPONSE_STATUS),

                residentScenario(
                        "llm-007-response-relax-required",
                        "Response omits a documented required field.",
                        ResidentTrafficEventFixtures
                                .responseMissingRequiredField(),
                        DriftType.MISSING_REQUIRED_RESPONSE_FIELD,
                        ContractChangeKind
                                .RELAX_RESPONSE_REQUIRED_FIELD),

                residentScenario(
                        "llm-008-response-change-type",
                        "Response field uses a different runtime type.",
                        ResidentTrafficEventFixtures
                                .responseTypeMismatch(),
                        DriftType.RESPONSE_TYPE_MISMATCH,
                        ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE),

                residentScenario(
                        "llm-009-response-add-enum",
                        "Response contains an undocumented enum value.",
                        ResidentTrafficEventFixtures
                                .responseEnumMismatch(),
                        DriftType.RESPONSE_ENUM_MISMATCH,
                        ContractChangeKind.ADD_RESPONSE_ENUM_VALUE),

                syntheticUnexpectedResponseFieldScenario()
        );
    }

    public Path residentBaselinePath() {
        return RESIDENT_BASELINE_PATH;
    }

    private LlmReliabilityScenario residentScenario(
            String scenarioId,
            String description,
            TrafficEvent event,
            DriftType expectedDriftType,
            ContractChangeKind expectedChangeKind
    ) {
        DriftAnalysisResult analysis =
                driftAnalysisService.analyze(event);

        DriftFinding finding =
                selectSingleFinding(
                        analysis,
                        expectedDriftType);

        ContractBaseline baseline =
                baselineLoader.getBaseline();

        GroundedEvidencePackage evidence =
                evidenceBuilder.build(
                        analysis,
                        finding,
                        baseline);

        return scenario(
                scenarioId,
                description,
                false,
                evidence,
                expectedChangeKind);
    }

    private LlmReliabilityScenario
    syntheticUnexpectedResponseFieldScenario() {

        GroundedEvidencePackage evidence =
                new GroundedEvidencePackage(
                        "e6-synthetic-010",
                        10010L,
                        "e6-synthetic-010:"
                                + DriftType.UNEXPECTED_RESPONSE_FIELD
                                + ":experimentalField",
                        DriftType.UNEXPECTED_RESPONSE_FIELD,
                        DriftSeverity.WARNING,
                        "GET",
                        "/api/residents/123",
                        "/api/residents/{id}",
                        "experimentalField",
                        "field absent from documented response schema",
                        "string",
                        "Runtime response contains an additional field "
                                + "in an E6-only synthetic closed-response "
                                + "contract scenario.",
                        new RelevantContractContext(
                                "3.1.0",
                                "GET",
                                "/api/residents/{id}",
                                null)
                );

        return scenario(
                "llm-010-response-add-field",
                "E6-only synthetic closed-response scenario containing "
                        + "an undocumented response field.",
                true,
                evidence,
                ContractChangeKind.ADD_RESPONSE_FIELD);
    }

    private LlmReliabilityScenario scenario(
            String scenarioId,
            String description,
            boolean e6OnlySynthetic,
            GroundedEvidencePackage evidence,
            ContractChangeKind expectedChangeKind
    ) {
        String proposedValue =
                canonicalProposedValue(
                        evidence,
                        expectedChangeKind);

        LlmProposalGroundTruth groundTruth =
                LlmProposalGroundTruth.declare(
                        evidence,
                        expectedChangeKind,
                        proposedValue);

        return new LlmReliabilityScenario(
                scenarioId,
                description,
                e6OnlySynthetic,
                evidence,
                EvidenceFingerprint.sha256(evidence),
                groundTruth);
    }

    private String canonicalProposedValue(
            GroundedEvidencePackage evidence,
            ContractChangeKind changeKind
    ) {
        return switch (changeKind) {
            case ADD_OPERATION ->
                    evidence.method()
                            + " "
                            + evidence.runtimePath();

            case RELAX_REQUEST_REQUIRED_FIELD,
                 RELAX_RESPONSE_REQUIRED_FIELD ->
                    "optional";

            case ADD_REQUEST_FIELD,
                 CHANGE_REQUEST_FIELD_TYPE,
                 ADD_REQUEST_ENUM_VALUE,
                 ADD_RESPONSE_STATUS,
                 ADD_RESPONSE_FIELD,
                 CHANGE_RESPONSE_FIELD_TYPE,
                 ADD_RESPONSE_ENUM_VALUE ->
                    evidence.observed();
        };
    }

    private DriftFinding selectSingleFinding(
            DriftAnalysisResult analysis,
            DriftType expectedDriftType
    ) {
        Objects.requireNonNull(
                analysis,
                "analysis must not be null");

        List<DriftFinding> matches =
                analysis.findings()
                        .stream()
                        .filter(finding ->
                                finding.type()
                                        == expectedDriftType)
                        .toList();

        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one "
                            + expectedDriftType
                            + " finding but found "
                            + matches.size()
                            + " for event "
                            + analysis.trafficEventId());
        }

        return matches.getFirst();
    }
}