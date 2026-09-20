package com.ildcvf.experiment.llm.groq;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class GroqApiKeyResolver {

    public static final String ENVIRONMENT_VARIABLE = "GROQ_API_KEY";

    private final Supplier<Map<String, String>> environmentSupplier;

    public GroqApiKeyResolver() {
        this(System::getenv);
    }

    GroqApiKeyResolver(
            Supplier<Map<String, String>> environmentSupplier
    ) {
        this.environmentSupplier =
                Objects.requireNonNull(
                        environmentSupplier,
                        "environmentSupplier must not be null");
    }

    public String resolve() {
        String value =
                environmentSupplier
                        .get()
                        .get(ENVIRONMENT_VARIABLE);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required Groq API credential is unavailable.");
        }

        return value;
    }
}