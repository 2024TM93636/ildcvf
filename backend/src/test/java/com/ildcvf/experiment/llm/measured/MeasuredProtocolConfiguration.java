package com.ildcvf.experiment.llm.measured;

import java.util.List;

public final class MeasuredProtocolConfiguration {

    public static final String PROTOCOL_ID = "e6-measured-v1";
    public static final String CONFIGURATION_LABEL =
            "groq-gpt-oss-20b-token2048-v1";

    public static final String PROVIDER = "groq";
    public static final String ENDPOINT =
            "https://api.groq.com/openai/v1/chat/completions";
    public static final String REQUESTED_MODEL = "openai/gpt-oss-20b";

    public static final double TEMPERATURE = 0.0;
    public static final double TOP_P = 1.0;
    public static final long SEED = 20260919L;
    public static final int MAX_COMPLETION_TOKENS = 2048;
    public static final boolean STREAM = false;
    public static final String SERVICE_TIER = "on_demand";
    public static final int TIMEOUT_MILLISECONDS = 30000;

    public static final String RESPONSE_FORMAT = "json_schema";
    public static final boolean STRICT = true;

    public static final String PROMPT_VERSION = "proposal-v1";
    public static final String PROMPT_SHA256 =
            "d9ee37eddddff37d75ecdeb933639c356054966f70e2e76ba5694efc7c655947";
    public static final String SCHEMA_SHA256 =
            "7247e79e1cb5be50e7baeb28ac185caeab68fbc8336736f9c8c212d091768ea4";

    public static final int SCENARIO_COUNT = 10;
    public static final int REPETITIONS_PER_SCENARIO = 4;
    public static final int EXPECTED_ATTEMPT_COUNT = 40;
    public static final int AUTOMATIC_RETRY_COUNT = 0;

    public static final boolean MEASURED_CAMPAIGN_ATTEMPT = true;
    public static final String CREATED_OR_FROZEN_DATE = "2026-09-20";

    public static final List<String> SCENARIO_ORDER = List.of(
            "llm-001-undocumented-operation",
            "llm-002-request-add-field",
            "llm-003-request-relax-required",
            "llm-004-request-change-type",
            "llm-005-request-add-enum",
            "llm-006-response-add-status",
            "llm-007-response-relax-required",
            "llm-008-response-change-type",
            "llm-009-response-add-enum",
            "llm-010-response-add-field"
    );

    public static final List<String> SCORED_FIELDS = List.of(
            "sourceTrafficEventId",
            "sourceFindingId",
            "driftType",
            "changeKind",
            "method",
            "runtimePath",
            "matchedOpenApiPath",
            "targetFieldPath",
            "expected",
            "observed",
            "proposedValue"
    );

    private MeasuredProtocolConfiguration() {
    }
}