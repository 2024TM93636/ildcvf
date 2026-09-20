package com.ildcvf.experiment.llm.smoke;

import com.ildcvf.experiment.llm.groq.GroqHttpResponse;
import com.ildcvf.experiment.llm.groq.GroqTransport;
import com.ildcvf.experiment.llm.groq.GroqTransportException;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class SingleUseRecordingGroqTransport
        implements GroqTransport {

    private final GroqTransport delegate;

    private final AtomicInteger invocationCount =
            new AtomicInteger();

    private volatile GroqHttpResponse lastResponse;

    public SingleUseRecordingGroqTransport(
            GroqTransport delegate
    ) {
        this.delegate =
                Objects.requireNonNull(
                        delegate,
                        "delegate must not be null");
    }

    @Override
    public GroqHttpResponse post(
            URI endpoint,
            String authorizationSecret,
            String requestBody,
            Duration timeout
    ) throws GroqTransportException {

        int invocation =
                invocationCount.incrementAndGet();

        if (invocation != 1) {
            throw new IllegalStateException(
                    "E6-D2 permits exactly one "
                            + "provider transport invocation.");
        }

        GroqHttpResponse response =
                delegate.post(
                        endpoint,
                        authorizationSecret,
                        requestBody,
                        timeout);

        lastResponse = response;

        return response;
    }

    public int invocationCount() {
        return invocationCount.get();
    }

    public GroqHttpResponse lastResponse() {
        return lastResponse;
    }
}
