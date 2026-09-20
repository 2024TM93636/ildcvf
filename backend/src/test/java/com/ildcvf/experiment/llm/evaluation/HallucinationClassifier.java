package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.llm.validation.ProposalValidationCode;
import com.ildcvf.llm.validation.ProposalValidationError;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class HallucinationClassifier {

    public HallucinationEvaluation classify(
            List<ProposalValidationError> validationErrors
    ) {
        Objects.requireNonNull(
                validationErrors,
                "validationErrors must not be null");

        List<HallucinationReasonCode> reasons =
                new ArrayList<>();

        for (ProposalValidationError error
                : validationErrors) {

            HallucinationReasonCode reason =
                    map(error.code());

            if (reason != null
                    && !reasons.contains(reason)) {
                reasons.add(reason);
            }
        }

        return HallucinationEvaluation.applicable(
                reasons);
    }

    private HallucinationReasonCode map(
            ProposalValidationCode code
    ) {
        return switch (code) {
            case SOURCE_EVENT_MISMATCH,
                 SOURCE_FINDING_MISMATCH ->
                    HallucinationReasonCode
                            .SOURCE_IDENTITY_INVENTED_OR_REDIRECTED;

            case DRIFT_TYPE_MISMATCH ->
                    HallucinationReasonCode
                            .DRIFT_TYPE_UNSUPPORTED;

            case METHOD_MISMATCH ->
                    HallucinationReasonCode
                            .METHOD_UNSUPPORTED;

            case RUNTIME_PATH_MISMATCH ->
                    HallucinationReasonCode
                            .RUNTIME_PATH_UNSUPPORTED;

            case MATCHED_PATH_MISMATCH ->
                    HallucinationReasonCode
                            .MATCHED_PATH_UNSUPPORTED;

            case FIELD_PATH_MISMATCH ->
                    HallucinationReasonCode
                            .FIELD_PATH_UNSUPPORTED;

            case EXPECTED_VALUE_CONTRADICTION ->
                    HallucinationReasonCode
                            .EXPECTED_VALUE_CONTRADICTION;

            case OBSERVED_VALUE_CONTRADICTION ->
                    HallucinationReasonCode
                            .OBSERVED_VALUE_CONTRADICTION;

            case CHANGE_KIND_NOT_ALLOWED ->
                    HallucinationReasonCode
                            .CHANGE_KIND_UNSUPPORTED;

            case PROPOSED_VALUE_NOT_GROUNDED ->
                    HallucinationReasonCode
                            .PROPOSED_VALUE_UNSUPPORTED;

            case NULL_PROPOSAL,
                 MISSING_REQUIRED_VALUE ->
                    null;
        };
    }
}
