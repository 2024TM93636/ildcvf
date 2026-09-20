package com.ildcvf.experiment.llm.measured;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;

public final class MeasuredCampaignAuthorization {

    public static final String API_KEY_ENVIRONMENT_VARIABLE =
            "GROQ_API_KEY";

    public static final String AUTHORIZATION_ENVIRONMENT_VARIABLE =
            "ILDCVF_E6_MEASURED_AUTHORIZATION";

    private MeasuredCampaignAuthorization() {
    }

    public static boolean isAuthorized(
            Map<String, String> environment,
            String expectedControllerAuthorization
    ) {
        Objects.requireNonNull(environment, "environment");

        String apiKey =
                environment.get(API_KEY_ENVIRONMENT_VARIABLE);

        String suppliedAuthorization =
                environment.get(AUTHORIZATION_ENVIRONMENT_VARIABLE);

        if (isBlank(apiKey)
                || isBlank(suppliedAuthorization)
                || isBlank(expectedControllerAuthorization)) {
            return false;
        }

        return constantTimeEquals(
                suppliedAuthorization,
                expectedControllerAuthorization
        );
    }

    public static void requireAuthorized(
            Map<String, String> environment,
            String expectedControllerAuthorization
    ) {
        if (!isAuthorized(
                environment,
                expectedControllerAuthorization
        )) {
            throw new SecurityException(
                    "Measured campaign execution is not authorized."
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean constantTimeEquals(
            String left,
            String right
    ) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}