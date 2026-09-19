package com.ildcvf.drift.service;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.RequestValidationResult;
import com.ildcvf.contract.model.ResponseStatusValidationResult;
import com.ildcvf.contract.model.ResponseBodyValidationResult;
import com.ildcvf.contract.service.ResponseBodyContractValidator;
import com.ildcvf.contract.service.RequestContractValidator;
import com.ildcvf.contract.service.ResponseStatusContractValidator;
import com.ildcvf.contract.service.ContractBaselineLoader;
import com.ildcvf.contract.service.ContractOperationMatcher;
import com.ildcvf.contract.service.ContractOperationMatcher.MatchResult;
import com.ildcvf.contract.service.ContractOperationMatcher.MatchStatus;
import com.ildcvf.contract.service.RequestContractValidator;
import com.ildcvf.contract.service.ResponseStatusContractValidator;
import com.ildcvf.drift.domain.DriftAnalysisResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.drift.domain.EvaluationStatus;
import com.ildcvf.traffic.domain.TrafficEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DriftAnalysisService {

    private final ContractBaseline baseline;
    private final ContractOperationMatcher matcher;
    private final RequestContractValidator requestContractValidator;
    private final ResponseStatusContractValidator responseStatusContractValidator;
    private final ResponseBodyContractValidator responseBodyContractValidator;

    public DriftAnalysisService(
            ContractBaselineLoader baselineLoader,
            ContractOperationMatcher matcher,
            RequestContractValidator requestContractValidator,
            ResponseStatusContractValidator responseStatusContractValidator,
            ResponseBodyContractValidator responseBodyContractValidator) {

        this.baseline = baselineLoader.getBaseline();
        this.matcher = matcher;
        this.requestContractValidator = requestContractValidator;
        this.responseStatusContractValidator = responseStatusContractValidator;
        this.responseBodyContractValidator = responseBodyContractValidator;
    }

    public DriftAnalysisResult analyze(TrafficEvent event) {

        if (!event.upstreamReached()) {
            return new DriftAnalysisResult(
                    event.eventId(),
                    event.sequence(),
                    EvaluationStatus.NOT_EVALUATED,
                    event.method(),
                    event.requestPath(),
                    null,
                    List.of(),
                    "Target service was not reached; contract evaluation was not performed.");
        }

        MatchResult match = matcher.match(
                event.method(),
                event.requestPath(),
                baseline.operations());

        if (match.status() == MatchStatus.AMBIGUOUS) {
            return new DriftAnalysisResult(
                    event.eventId(),
                    event.sequence(),
                    EvaluationStatus.NOT_EVALUATED,
                    event.method(),
                    event.requestPath(),
                    null,
                    List.of(),
                    "Multiple equally specific OpenAPI operations matched the runtime request.");
        }

        if (match.status() == MatchStatus.MATCHED) {

            RequestValidationResult requestValidation =
                    requestContractValidator.validate(
                            event,
                            match.operation(),
                            baseline);

            ResponseStatusValidationResult responseValidation =
                    responseStatusContractValidator.validate(
                            event,
                            match.operation(),
                            baseline);

            ResponseBodyValidationResult responseBodyValidation =
                    responseBodyContractValidator.validate(
                            event,
                            match.operation(),
                            baseline);

            List<DriftFinding> findings = new ArrayList<>();

            findings.addAll(requestValidation.findings());
            findings.addAll(responseValidation.findings());
            findings.addAll(responseBodyValidation.findings());
            findings.sort(FINDING_ORDER);

            EvaluationStatus evaluationStatus =
                    composeEvaluationStatus(
                            requestValidation.evaluationStatus(),
                            responseValidation.evaluationStatus(),
                            responseBodyValidation.evaluationStatus());

            String evaluationMessage =
                    composeEvaluationMessage(
                            requestValidation.evaluationMessage(),
                            responseValidation.evaluationMessage(),
                            responseBodyValidation.evaluationMessage());

            return new DriftAnalysisResult(
                    event.eventId(),
                    event.sequence(),
                    evaluationStatus,
                    event.method(),
                    event.requestPath(),
                    match.operation().pathTemplate(),
                    findings,
                    evaluationMessage);
        }

        DriftFinding finding = undocumentedOperationFinding(event);

        return new DriftAnalysisResult(
                event.eventId(),
                event.sequence(),
                EvaluationStatus.EVALUATED,
                event.method(),
                event.requestPath(),
                null,
                List.of(finding),
                null);
    }

    private DriftFinding undocumentedOperationFinding(TrafficEvent event) {
        return new DriftFinding(
                event.eventId()
                        + ":"
                        + DriftType.UNDOCUMENTED_OPERATION.name()
                        + ":1",
                event.eventId(),
                event.sequence(),
                DriftType.UNDOCUMENTED_OPERATION,
                DriftSeverity.WARNING,
                event.method(),
                event.requestPath(),
                null,
                null,
                "documented OpenAPI operation",
                event.method() + " " + event.requestPath(),
                "Runtime operation is not documented in the OpenAPI baseline.");
    }

    private EvaluationStatus composeEvaluationStatus(
            EvaluationStatus requestStatus,
            EvaluationStatus responseStatus,
            EvaluationStatus responseBodyStatus) {

        if (requestStatus == EvaluationStatus.PARTIALLY_EVALUATED
                || responseStatus == EvaluationStatus.PARTIALLY_EVALUATED
                || responseBodyStatus == EvaluationStatus.PARTIALLY_EVALUATED) {

            return EvaluationStatus.PARTIALLY_EVALUATED;
        }

        return EvaluationStatus.EVALUATED;
    }

    private String composeEvaluationMessage(
            String requestMessage,
            String responseMessage,
            String responseBodyMessage){

        List<String> messages = new ArrayList<>();

        if (requestMessage != null
                && !requestMessage.isBlank()) {
            messages.add(requestMessage);
        }

        if (responseMessage != null
                && !responseMessage.isBlank()) {
            messages.add(responseMessage);
        }

        if (responseBodyMessage != null
                && !responseBodyMessage.isBlank()) {
            messages.add(responseBodyMessage);
        }

        return messages.isEmpty()
                ? null
                : String.join(" | ", messages);
    }

    private static final Comparator<DriftFinding> FINDING_ORDER =
            Comparator.comparing(
                            (DriftFinding finding) ->
                                    finding.type().name())
                    .thenComparing(
                            finding -> finding.fieldPath() == null
                                    ? ""
                                    : finding.fieldPath())
                    .thenComparing(DriftFinding::findingId);
}