package com.ildcvf.experiment.llm.groq;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransportDiagnosticClassifierTest {

    private final TransportDiagnosticClassifier classifier =
            new TransportDiagnosticClassifier();

    @Test
    void unknownHostIsDnsFailure() {
        assertCode(
                new UnknownHostException(
                        "secret.internal.example"
                ),
                TransportDiagnosticCode.DNS_FAILURE
        );
    }

    @Test
    void sslHandshakeIsTlsFailure() {
        assertCode(
                new SSLHandshakeException(
                        "sensitive TLS diagnostic"
                ),
                TransportDiagnosticCode.TLS_FAILURE
        );
    }

    @Test
    void connectExceptionIsConnectFailure() {
        assertCode(
                new ConnectException(
                        "sensitive host:443"
                ),
                TransportDiagnosticCode.CONNECT_FAILURE
        );
    }

    @Test
    void httpTimeoutIsRequestTimeout() {
        GroqTransportException exception =
                new GroqTransportException(
                        GroqTransportException.Kind.TIMEOUT,
                        "wrapper",
                        new HttpTimeoutException(
                                "sensitive timeout text"
                        )
                );

        TransportDiagnostic diagnostic =
                classifier.classify(exception);

        assertEquals(
                TransportDiagnosticCode.REQUEST_TIMEOUT,
                diagnostic.transportDiagnosticCode()
        );

        assertEquals(
                "TIMEOUT",
                diagnostic.transportFailureKind()
        );
    }

    @Test
    void interruptedIsInterrupted() {
        assertCode(
                new InterruptedException(
                        "sensitive interruption"
                ),
                TransportDiagnosticCode.INTERRUPTED
        );
    }

    @Test
    void socketExceptionIsSocketIoFailure() {
        assertCode(
                new SocketException(
                        "sensitive socket endpoint"
                ),
                TransportDiagnosticCode.SOCKET_IO_FAILURE
        );
    }

    @Test
    void genericIOExceptionIsOtherIoFailure() {
        assertCode(
                new IOException(
                        "sensitive filesystem/network text"
                ),
                TransportDiagnosticCode.OTHER_IO_FAILURE
        );
    }

    @Test
    void unknownCauseIsOtherTransportFailure() {
        GroqTransportException exception =
                new GroqTransportException(
                        GroqTransportException
                                .Kind.TRANSPORT_FAILURE,
                        "wrapper",
                        new IllegalStateException(
                                "secret-value-must-not-survive"
                        )
                );

        TransportDiagnostic diagnostic =
                classifier.classify(exception);

        assertEquals(
                TransportDiagnosticCode
                        .OTHER_TRANSPORT_FAILURE,
                diagnostic.transportDiagnosticCode()
        );
    }

    @Test
    void noCauseStillClassifiesDeterministically() {
        GroqTransportException exception =
                new GroqTransportException(
                        GroqTransportException
                                .Kind.TRANSPORT_FAILURE,
                        "sensitive wrapper text"
                );

        TransportDiagnostic diagnostic =
                classifier.classify(exception);

        assertNull(
                diagnostic.transportCauseClass()
        );

        assertEquals(
                TransportDiagnosticCode
                        .OTHER_TRANSPORT_FAILURE,
                diagnostic.transportDiagnosticCode()
        );
    }

    @Test
    void exceptionMessagesAreNeverPersisted() {
        String sensitive =
                "synthetic-sensitive-token-value "
                        + "synthetic-authorization-value "
                        + "private.internal.example";

        GroqTransportException exception =
                new GroqTransportException(
                        GroqTransportException
                                .Kind.TRANSPORT_FAILURE,
                        sensitive,
                        new IOException(sensitive)
                );

        TransportDiagnostic diagnostic =
                classifier.classify(exception);

        String retained =
                diagnostic.toString();

        assertFalse(retained.contains(sensitive));
        assertFalse(
                retained.contains(
                        "synthetic-sensitive-token-value"
                )
        );

        assertFalse(
                retained.contains(
                        "synthetic-authorization-value"
                )
        );
        assertFalse(
                retained.contains(
                        "private.internal.example"
                )
        );
    }

    private void assertCode(
            Throwable cause,
            TransportDiagnosticCode expected
    ) {
        GroqTransportException exception =
                new GroqTransportException(
                        GroqTransportException
                                .Kind.TRANSPORT_FAILURE,
                        "wrapper",
                        cause
                );

        TransportDiagnostic diagnostic =
                classifier.classify(exception);

        assertEquals(
                expected,
                diagnostic.transportDiagnosticCode()
        );

        assertEquals(
                GroqTransportException.class.getName(),
                diagnostic.transportExceptionClass()
        );

        assertEquals(
                cause.getClass().getName(),
                diagnostic.transportCauseClass()
        );
    }
}