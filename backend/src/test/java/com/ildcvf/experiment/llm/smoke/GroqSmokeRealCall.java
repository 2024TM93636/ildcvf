package com.ildcvf.experiment.llm.smoke;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class GroqSmokeRealCall {

    @Test
    void executeFirstAuthorizedSmokeExactlyOnce()
            throws Exception {

        GroqSmokeRunner runner =
                new GroqSmokeRunner();

        SmokeExecutionResult result =
                runner.executeRealAuthorizedSmoke(
                        SmokeCatalog.AUTHORIZED_SMOKE_ID);

        assertEquals(
                1,
                result.providerInvocationCount());
    }
}
