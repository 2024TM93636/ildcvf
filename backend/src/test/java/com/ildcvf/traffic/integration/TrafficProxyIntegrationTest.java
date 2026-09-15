package com.ildcvf.traffic.integration;

import com.ildcvf.traffic.domain.TrafficEvent;
import com.ildcvf.traffic.repository.TrafficEventRepository;
import com.ildcvf.traffic.service.TrafficEventIdGenerator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment =
                SpringBootTest.WebEnvironment.RANDOM_PORT
)
class TrafficProxyIntegrationTest {

    private static final HttpServer UPSTREAM_SERVER;

    static {
        try {
            UPSTREAM_SERVER =
                    HttpServer.create(
                            new InetSocketAddress(
                                    "localhost",
                                    0
                            ),
                            0
                    );

            UPSTREAM_SERVER.createContext(
                    "/api/residents",
                    TrafficProxyIntegrationTest
                            ::handleResidentRequest
            );

            UPSTREAM_SERVER.start();

        } catch (IOException exception) {
            throw new ExceptionInInitializerError(
                    exception
            );
        }
    }

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "ildcvf.targets.resident.base-url",
                () ->
                        "http://localhost:"
                                + UPSTREAM_SERVER
                                .getAddress()
                                .getPort()
        );

        registry.add(
                "ildcvf.targets.resident.service-id",
                () -> "resident-api"
        );

        registry.add(
                "ildcvf.traffic.max-capture-bytes",
                () -> 65536
        );
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TrafficEventRepository repository;

    @Autowired
    private TrafficEventIdGenerator idGenerator;

    private HttpClient client;

    @BeforeEach
    void setUp() {
        repository.clear();
        idGenerator.reset();

        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopUpstreamServer() {
        UPSTREAM_SERVER.stop(0);
    }

    @Test
    void shouldForwardGetAndCaptureTrafficEvent()
            throws Exception {

        HttpResponse<String> response =
                send(
                        HttpRequest.newBuilder()
                                .uri(proxyUri(
                                        "/api/residents/1"
                                ))
                                .GET()
                                .build()
                );

        assertThat(response.statusCode())
                .isEqualTo(200);

        assertThat(response.body())
                .contains("\"firstName\":\"Asha\"");

        TrafficEvent event = onlyEvent();

        assertThat(event.eventId())
                .isEqualTo("traffic-00000001");

        assertThat(event.sequence())
                .isEqualTo(1);

        assertThat(event.method())
                .isEqualTo("GET");

        assertThat(event.requestPath())
                .isEqualTo("/api/residents/1");

        assertThat(event.responseStatus())
                .isEqualTo(200);

        assertThat(event.responseBody())
                .contains("\"firstName\":\"Asha\"");

        assertThat(event.durationMs())
                .isGreaterThanOrEqualTo(0);

        assertThat(event.upstreamReached())
                .isTrue();
    }

    @Test
    void shouldForwardPostPreserve201AndLocationAndCaptureBodies()
            throws Exception {

        String requestBody =
                """
                {
                  "firstName":"Asha",
                  "lastName":"Nair",
                  "email":"asha@example.com",
                  "dateOfBirth":"1994-05-20",
                  "status":"ACTIVE"
                }
                """;

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(proxyUri(
                                "/api/residents"
                        ))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(
                                                requestBody
                                        )
                        )
                        .build();

        HttpResponse<String> response =
                send(request);

        assertThat(response.statusCode())
                .isEqualTo(201);

        assertThat(
                response.headers()
                        .firstValue("Location")
        ).contains(
                "/api/residents/1"
        );

        TrafficEvent event = onlyEvent();

        assertThat(event.method())
                .isEqualTo("POST");

        assertThat(event.requestPath())
                .isEqualTo("/api/residents");

        assertThat(event.requestBody())
                .contains(
                        "\"firstName\":\"Asha\""
                );

        assertThat(event.responseStatus())
                .isEqualTo(201);

        assertThat(event.responseBody())
                .contains("\"id\":1");

        assertThat(event.responseHeaders())
                .containsKey("Location");

        assertThat(
                event.responseHeaders()
                        .get("Location")
        ).contains(
                "/api/residents/1"
        );
    }

    @Test
    void shouldPreserve204Response()
            throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(proxyUri(
                                "/api/residents/1"
                        ))
                        .DELETE()
                        .build();

        HttpResponse<String> response =
                send(request);

        assertThat(response.statusCode())
                .isEqualTo(204);

        assertThat(response.body())
                .isEmpty();

        TrafficEvent event = onlyEvent();

        assertThat(event.responseStatus())
                .isEqualTo(204);

        assertThat(event.responseBody())
                .isNull();

        assertThat(event.responseBodySizeBytes())
                .isZero();
    }

    @Test
    void shouldPreserve400Response()
            throws Exception {

        String body =
                """
                {"firstName":""}
                """;

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(proxyUri(
                                "/api/residents"
                        ))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(body)
                        )
                        .build();

        HttpResponse<String> response =
                send(request);

        assertThat(response.statusCode())
                .isEqualTo(400);

        TrafficEvent event = onlyEvent();

        assertThat(event.responseStatus())
                .isEqualTo(400);

        assertThat(event.responseBody())
                .contains(
                        "VALIDATION_FAILED"
                );
    }

    @Test
    void shouldPreserve404Response()
            throws Exception {

        HttpResponse<String> response =
                send(
                        HttpRequest.newBuilder()
                                .uri(proxyUri(
                                        "/api/residents/9999"
                                ))
                                .GET()
                                .build()
                );

        assertThat(response.statusCode())
                .isEqualTo(404);

        TrafficEvent event = onlyEvent();

        assertThat(event.responseStatus())
                .isEqualTo(404);

        assertThat(event.responseBody())
                .contains(
                        "RESIDENT_NOT_FOUND"
                );
    }

    @Test
    void shouldCaptureQueryStringAndParameters()
            throws Exception {

        HttpResponse<String> response =
                send(
                        HttpRequest.newBuilder()
                                .uri(proxyUri(
                                        "/api/residents?status=ACTIVE&tag=a&tag=b"
                                ))
                                .GET()
                                .build()
                );

        assertThat(response.statusCode())
                .isEqualTo(200);

        TrafficEvent event = onlyEvent();

        assertThat(event.queryString())
                .isEqualTo(
                        "status=ACTIVE&tag=a&tag=b"
                );

        assertThat(
                event.queryParameters()
                        .get("status")
        ).containsExactly("ACTIVE");

        assertThat(
                event.queryParameters()
                        .get("tag")
        ).containsExactly(
                "a",
                "b"
        );
    }

    @Test
    void shouldNotStoreSensitiveRequestHeaders()
            throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(proxyUri(
                                "/api/residents"
                        ))
                        .header(
                                "Authorization",
                                "Bearer super-secret"
                        )
                        .header(
                                "Cookie",
                                "session=secret"
                        )
                        .header(
                                "X-API-Key",
                                "secret-key"
                        )
                        .header(
                                "Accept",
                                "application/json"
                        )
                        .GET()
                        .build();

        send(request);

        TrafficEvent event = onlyEvent();

        assertThat(event.requestHeaders())
                .containsKey("Accept");

        assertThat(event.requestHeaders())
                .doesNotContainKeys(
                        "Authorization",
                        "Cookie",
                        "X-API-Key"
                );

        assertThat(
                event.requestHeaders()
                        .toString()
        ).doesNotContain(
                "super-secret",
                "session=secret",
                "secret-key"
        );
    }

    @Test
    void debugApiShouldReturnCapturedEventWithoutCreatingAnotherEvent()
            throws Exception {

        send(
                HttpRequest.newBuilder()
                        .uri(proxyUri(
                                "/api/residents/1"
                        ))
                        .GET()
                        .build()
        );

        assertThat(repository.findAll())
                .hasSize(1);

        HttpResponse<String> debugResponse =
                send(
                        HttpRequest.newBuilder()
                                .uri(
                                        URI.create(
                                                "http://localhost:"
                                                        + port
                                                        + "/api/traffic"
                                        )
                                )
                                .GET()
                                .build()
                );

        assertThat(debugResponse.statusCode())
                .isEqualTo(200);

        assertThat(debugResponse.body())
                .contains(
                        "traffic-00000001"
                );

        assertThat(repository.findAll())
                .hasSize(1);
    }

    @Test
    void shouldForwardPutAndCaptureRequest()
            throws Exception {

        String body =
                """
                {
                  "firstName":"Asha",
                  "lastName":"Nair",
                  "email":"updated@example.com",
                  "dateOfBirth":"1994-05-20",
                  "status":"INACTIVE",
                  "roomNumber":"B-202"
                }
                """;

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(proxyUri(
                                "/api/residents/1"
                        ))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .PUT(
                                HttpRequest.BodyPublishers
                                        .ofString(body)
                        )
                        .build();

        HttpResponse<String> response =
                send(request);

        assertThat(response.statusCode())
                .isEqualTo(200);

        TrafficEvent event = onlyEvent();

        assertThat(event.method())
                .isEqualTo("PUT");

        assertThat(event.requestPath())
                .isEqualTo(
                        "/api/residents/1"
                );

        assertThat(event.requestBody())
                .contains(
                        "updated@example.com"
                );

        assertThat(event.responseBody())
                .contains(
                        "\"status\":\"INACTIVE\""
                );
    }

    @Test
    void shouldStoreEventsInSequenceOrder()
            throws Exception {

        send(
                HttpRequest.newBuilder()
                        .uri(proxyUri(
                                "/api/residents/1"
                        ))
                        .GET()
                        .build()
        );

        send(
                HttpRequest.newBuilder()
                        .uri(proxyUri(
                                "/api/residents"
                        ))
                        .GET()
                        .build()
        );

        List<TrafficEvent> events =
                repository.findAll();

        assertThat(events).hasSize(2);

        assertThat(events.get(0).eventId())
                .isEqualTo(
                        "traffic-00000001"
                );

        assertThat(events.get(1).eventId())
                .isEqualTo(
                        "traffic-00000002"
                );

        assertThat(events.get(0).sequence())
                .isLessThan(
                        events.get(1).sequence()
                );
    }

    private TrafficEvent onlyEvent() {
        List<TrafficEvent> events =
                repository.findAll();

        assertThat(events).hasSize(1);

        return events.getFirst();
    }

    private URI proxyUri(String target) {
        return URI.create(
                "http://localhost:"
                        + port
                        + "/proxy"
                        + target
        );
    }

    private HttpResponse<String> send(
            HttpRequest request
    ) throws Exception {
        return client.send(
                request,
                HttpResponse.BodyHandlers
                        .ofString()
        );
    }

    private static void handleResidentRequest(
            HttpExchange exchange
    ) throws IOException {

        String method =
                exchange.getRequestMethod();

        String path =
                exchange.getRequestURI()
                        .getPath();

        if ("POST".equals(method)
                && "/api/residents"
                .equals(path)) {

            String requestBody =
                    new String(
                            exchange.getRequestBody()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            if (requestBody.contains(
                    "\"firstName\":\"\""
            )) {
                sendJson(
                        exchange,
                        400,
                        """
                        {"status":400,"code":"VALIDATION_FAILED","message":"Request validation failed"}
                        """
                );
                return;
            }

            exchange.getResponseHeaders()
                    .add(
                            "Location",
                            "/api/residents/1"
                    );

            sendJson(
                    exchange,
                    201,
                    """
                    {"id":1,"firstName":"Asha","lastName":"Nair","email":"asha@example.com","dateOfBirth":"1994-05-20","phoneNumber":null,"status":"ACTIVE","roomNumber":null}
                    """
            );
            return;
        }

        if ("DELETE".equals(method)
                && "/api/residents/1"
                .equals(path)) {

            exchange.sendResponseHeaders(
                    204,
                    -1
            );

            exchange.close();
            return;
        }

        if ("GET".equals(method)
                && "/api/residents/9999"
                .equals(path)) {

            sendJson(
                    exchange,
                    404,
                    """
                    {"status":404,"code":"RESIDENT_NOT_FOUND","message":"Resident not found with id: 9999"}
                    """
            );
            return;
        }

        if ("GET".equals(method)
                && "/api/residents/1"
                .equals(path)) {

            sendJson(
                    exchange,
                    200,
                    """
                    {"id":1,"firstName":"Asha","lastName":"Nair","email":"asha@example.com","dateOfBirth":"1994-05-20","phoneNumber":null,"status":"ACTIVE","roomNumber":null}
                    """
            );
            return;
        }

        if ("GET".equals(method)
                && "/api/residents"
                .equals(path)) {

            sendJson(
                    exchange,
                    200,
                    "[]"
            );
            return;
        }

        if ("PUT".equals(method)
                && "/api/residents/1"
                .equals(path)) {

            sendJson(
                    exchange,
                    200,
                    """
                    {"id":1,"firstName":"Asha","lastName":"Nair","email":"updated@example.com","dateOfBirth":"1994-05-20","phoneNumber":null,"status":"INACTIVE","roomNumber":"B-202"}
                    """
            );
            return;
        }

        sendJson(
                exchange,
                404,
                """
                {"status":404,"code":"RESIDENT_NOT_FOUND"}
                """
        );
    }

    private static void sendJson(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {

        byte[] bytes =
                body.trim()
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "application/json"
                );

        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        exchange.getResponseBody()
                .write(bytes);

        exchange.close();
    }
}