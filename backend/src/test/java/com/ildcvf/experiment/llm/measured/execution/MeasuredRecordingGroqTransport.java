package com.ildcvf.experiment.llm.measured.execution;

import com.ildcvf.experiment.llm.groq.GroqHttpResponse;
import com.ildcvf.experiment.llm.groq.GroqTransport;
import com.ildcvf.experiment.llm.groq.GroqTransportException;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public final class MeasuredRecordingGroqTransport
        implements GroqTransport {

    private final GroqTransport delegate;

    private int invocationCount;
    private GroqHttpResponse lastResponse;
    private String lastRequestBody;

    public MeasuredRecordingGroqTransport(
            GroqTransport delegate
    ) {
        this.delegate =
                Objects.requireNonNull(
                        delegate,
                        "delegate must not be null"
                );
    }

    @Override
    public synchronized GroqHttpResponse post(
            URI endpoint,
            String authorizationSecret,
            String requestBody,
            Duration timeout
    ) throws GroqTransportException {
        if (invocationCount != 0) {
            throw new IllegalStateException(
                    "Measured transport permits at most one "
                            + "provider invocation."
            );
        }

        invocationCount = 1;
        lastRequestBody = requestBody;

        GroqHttpResponse response =
                delegate.post(
                        endpoint,
                        authorizationSecret,
                        requestBody,
                        timeout
                );

        lastResponse = response;

        return response;
    }

    public synchronized int invocationCount() {
        return invocationCount;
    }

    public synchronized GroqHttpResponse lastResponse() {
        return lastResponse;
    }

    public synchronized String lastRequestBody() {
        return lastRequestBody;
    }
}
