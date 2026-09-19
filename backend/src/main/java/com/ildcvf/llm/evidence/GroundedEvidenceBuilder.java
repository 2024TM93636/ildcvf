package com.ildcvf.llm.evidence;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.ContractOperation;
import com.ildcvf.drift.domain.DriftAnalysisResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftType;

import java.util.List;
import java.util.Objects;

/**
 * Deterministically constructs the bounded evidence supplied to the future
 * LLM boundary from already-trusted M4 drift-analysis output and the frozen
 * contract baseline.
 *
 * <p>This builder does not inspect or expose TrafficEvent payloads, headers,
 * cookies, JWTs, or unrelated runtime evidence. Its responsibility is to
 * reconcile trusted deterministic analysis objects and fail fast if those
 * objects are internally inconsistent.</p>
 */
public final class GroundedEvidenceBuilder {

    /**
     * Builds one bounded evidence package for one selected deterministic
     * finding.
     *
     * @param analysis deterministic M4 analysis result containing the finding
     * @param finding selected deterministic finding
     * @param baseline frozen contract baseline used for operation context
     * @return grounded bounded evidence
     * @throws NullPointerException if a required trusted input is null
     * @throws IllegalArgumentException if the finding is not a complete member
     *                                  of the supplied analysis result
     * @throws IllegalStateException if trusted analysis/finding/baseline
     *                               evidence contradicts itself
     */
    public GroundedEvidencePackage build(
            DriftAnalysisResult analysis,
            DriftFinding finding,
            ContractBaseline baseline
    ) {
        Objects.requireNonNull(analysis, "analysis must not be null");
        Objects.requireNonNull(finding, "finding must not be null");
        Objects.requireNonNull(baseline, "baseline must not be null");

        requireFindingMembership(analysis, finding);
        requireEqual(
                "trafficEventId",
                analysis.trafficEventId(),
                finding.trafficEventId()
        );
        requireEqual(
                "trafficSequence",
                analysis.trafficSequence(),
                finding.trafficSequence()
        );
        requireEqual(
                "method",
                analysis.method(),
                finding.method()
        );
        requireEqual(
                "runtimePath",
                analysis.runtimePath(),
                finding.runtimePath()
        );

        String matchedOpenApiPath =
                reconcileMatchedOpenApiPath(analysis, finding);

        RelevantContractContext relevantContractContext =
                buildRelevantContractContext(
                        analysis,
                        finding,
                        baseline,
                        matchedOpenApiPath
                );

        return new GroundedEvidencePackage(
                analysis.trafficEventId(),
                analysis.trafficSequence(),
                finding.findingId(),
                finding.type(),
                finding.severity(),
                analysis.method(),
                analysis.runtimePath(),
                matchedOpenApiPath,
                finding.fieldPath(),
                finding.expected(),
                finding.observed(),
                finding.message(),
                relevantContractContext
        );
    }

    private void requireFindingMembership(
            DriftAnalysisResult analysis,
            DriftFinding finding
    ) {
        List<DriftFinding> findings = analysis.findings();

        if (findings == null || !findings.contains(finding)) {
            throw new IllegalArgumentException(
                    "Selected DriftFinding is not present as a complete value "
                            + "in DriftAnalysisResult.findings"
            );
        }
    }

    private String reconcileMatchedOpenApiPath(
            DriftAnalysisResult analysis,
            DriftFinding finding
    ) {
        String analysisPath = analysis.matchedOpenApiPath();
        String findingPath = finding.matchedOpenApiPath();

        if (analysisPath == null) {
            if (findingPath != null) {
                throw new IllegalStateException(
                        "Matched OpenAPI path contradiction: analysis path is "
                                + "null but finding path is "
                                + printable(findingPath)
                );
            }

            return null;
        }

        if (findingPath == null) {
            return analysisPath;
        }

        if (!analysisPath.equals(findingPath)) {
            throw new IllegalStateException(
                    "Matched OpenAPI path contradiction: analysis path "
                            + printable(analysisPath)
                            + " differs from finding path "
                            + printable(findingPath)
            );
        }

        return analysisPath;
    }

    private RelevantContractContext buildRelevantContractContext(
            DriftAnalysisResult analysis,
            DriftFinding finding,
            ContractBaseline baseline,
            String matchedOpenApiPath
    ) {
        if (finding.type() == DriftType.UNDOCUMENTED_OPERATION) {
            if (matchedOpenApiPath != null) {
                throw new IllegalStateException(
                        "UNDOCUMENTED_OPERATION must not have a matched "
                                + "OpenAPI path"
                );
            }

            return new RelevantContractContext(
                    baseline.openApiVersion(),
                    analysis.method(),
                    null,
                    null
            );
        }

        if (matchedOpenApiPath == null) {
            throw new IllegalStateException(
                    "Drift type "
                            + finding.type()
                            + " requires documented operation context, but "
                            + "DriftAnalysisResult.matchedOpenApiPath is null"
            );
        }

        ContractOperation operation = resolveOperation(
                baseline,
                matchedOpenApiPath,
                analysis.method()
        );

        return new RelevantContractContext(
                baseline.openApiVersion(),
                analysis.method(),
                matchedOpenApiPath,
                operation.operationId()
        );
    }

    private ContractOperation resolveOperation(
            ContractBaseline baseline,
            String matchedOpenApiPath,
            String method
    ) {
        List<ContractOperation> operations = baseline.operations();

        if (operations == null) {
            throw new IllegalStateException(
                    "ContractBaseline.operations must not be null"
            );
        }

        List<ContractOperation> matches = operations.stream()
                .filter(Objects::nonNull)
                .filter(operation ->
                        Objects.equals(
                                operation.pathTemplate(),
                                matchedOpenApiPath
                        )
                )
                .filter(operation ->
                        Objects.equals(
                                operation.method(),
                                method
                        )
                )
                .toList();

        if (matches.isEmpty()) {
            throw new IllegalStateException(
                    "No ContractOperation matches path "
                            + printable(matchedOpenApiPath)
                            + " and method "
                            + printable(method)
            );
        }

        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Multiple ContractOperations match path "
                            + printable(matchedOpenApiPath)
                            + " and method "
                            + printable(method)
                            + ": "
                            + matches.size()
            );
        }

        return matches.getFirst();
    }

    private void requireEqual(
            String fieldName,
            Object analysisValue,
            Object findingValue
    ) {
        if (!Objects.equals(analysisValue, findingValue)) {
            throw new IllegalStateException(
                    "Trusted evidence contradiction for "
                            + fieldName
                            + ": analysis="
                            + printable(analysisValue)
                            + ", finding="
                            + printable(findingValue)
            );
        }
    }

    private String printable(Object value) {
        return value == null ? "<null>" : "'" + value + "'";
    }
}
