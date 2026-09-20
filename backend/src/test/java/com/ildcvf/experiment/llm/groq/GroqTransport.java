package com.ildcvf.experiment.llm.groq;

import java.net.URI;
import java.time.Duration;

public interface GroqTransport {

    GroqHttpResponse post(
            URI endpoint,
            String authorizationSecret,
            String requestBody,
            Duration timeout
    ) throws GroqTransportException;
}