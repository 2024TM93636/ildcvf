package com.ildcvf.contract.version;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.persistence.ProposalAttemptPersistenceService;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.review.ReviewDecision;
import com.ildcvf.llm.review.ReviewDecisionPersistenceService;
import com.ildcvf.llm.validation.ProposalValidationResult;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ContractVersionRestIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired ProposalAttemptPersistenceService attempts;
    @Autowired ReviewDecisionPersistenceService reviews;
    @Autowired ContractVersionPersistenceService versions;
    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager em;
    @Autowired ObjectMapper mapper;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final List<UUID> fixtureIds = new ArrayList<>();

    private String url(String path) {
        return "http://localhost:" + port
                + "/api/contract/versions" + path;
    }

    private HttpResponse<byte[]> request(
            String method, String path, String json)
            throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .timeout(Duration.ofSeconds(30));

        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            json, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }

        return client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        return request("GET", path, null);
    }

    private HttpResponse<byte[]> post(String json) throws Exception {
        return request("POST", "", json);
    }

    private JsonNode json(HttpResponse<byte[]> response) {
        return mapper.readTree(
                new String(response.body(), StandardCharsets.UTF_8));
    }

    private String createBody(UUID attemptId) {
        return "{\"proposalAttemptId\":\"" + attemptId + "\"}";
    }

    private UUID fixture(boolean approved, boolean supported) {
        String event = "m152c-" + UUID.randomUUID();
        String finding = event + ":RESPONSE_TYPE_MISMATCH:id";

        UUID attemptId = attempts.start(event, finding);
        fixtureIds.add(attemptId);

        ContractChangeProposal proposal = new ContractChangeProposal(
                "m152c-" + UUID.randomUUID(),
                event,
                finding,
                DriftType.RESPONSE_TYPE_MISMATCH,
                ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                "GET",
                "/api/residents/1",
                "/api/residents/{id}",
                supported ? "id" : "name",
                "integer",
                "string",
                "string",
                "Disposable M15.2C REST test fixture"
        );

        attempts.completeValidated(
                attemptId,
                proposal,
                ProposalValidationResult.validResult()
        );

        reviews.record(
                attemptId,
                approved ? ReviewDecision.APPROVED
                        : ReviewDecision.REJECTED,
                "m152c-test-reviewer",
                "Disposable REST integration fixture"
        );

        return attemptId;
    }

    private UUID fixtureWithoutReview() {
        String event = "m152c-" + UUID.randomUUID();
        String finding = event + ":RESPONSE_TYPE_MISMATCH:id";

        UUID attemptId = attempts.start(event, finding);
        fixtureIds.add(attemptId);

        attempts.completeValidated(
                attemptId,
                new ContractChangeProposal(
                        "m152c-" + UUID.randomUUID(),
                        event,
                        finding,
                        DriftType.RESPONSE_TYPE_MISMATCH,
                        ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                        "GET",
                        "/api/residents/1",
                        "/api/residents/{id}",
                        "id", "integer", "string", "string",
                        "Disposable missing-review fixture"
                ),
                ProposalValidationResult.validResult()
        );

        return attemptId;
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @AfterEach
    void cleanup() {
        if (fixtureIds.isEmpty()) return;

        List<UUID> ids = List.copyOf(fixtureIds);

        transactions.executeWithoutResult(tx -> {
            em.createNativeQuery(
                            "DELETE FROM contract_versions " +
                                    "WHERE proposal_attempt_id IN (:ids)")
                    .setParameter("ids", ids)
                    .executeUpdate();

            em.createNativeQuery(
                            "DELETE FROM llm_review_decisions " +
                                    "WHERE proposal_attempt_id IN (:ids)")
                    .setParameter("ids", ids)
                    .executeUpdate();

            em.createNativeQuery(
                            "DELETE FROM llm_proposal_attempts " +
                                    "WHERE id IN (:ids)")
                    .setParameter("ids", ids)
                    .executeUpdate();
        });

        fixtureIds.clear();
    }

    @Test
    void creationRetrievalDownloadAndEvidenceIntegrity()
            throws Exception {

        UUID attemptId = fixture(true, true);

        var originalAttempt = attempts.find(attemptId).orElseThrow();
        String proposalBefore = originalAttempt.proposalJson.toString();
        String validationBefore = originalAttempt.validationJson.toString();
        var reviewBefore = reviews.findByAttempt(attemptId).orElseThrow();

        Path baseline = Path.of(
                "../datasets/openapi/baseline/resident-api-v1.yaml");
        byte[] baselineBefore = Files.readAllBytes(baseline);

        var createdResponse = post(createBody(attemptId));
        assertThat(createdResponse.statusCode()).isEqualTo(201);

        JsonNode created = json(createdResponse);
        UUID versionId = UUID.fromString(
                created.get("id").asText());

        assertThat(created.get("proposalAttemptId").asText())
                .isEqualTo(attemptId.toString());
        assertThat(created.get("reviewDecisionId").asText())
                .isEqualTo(reviewBefore.reviewId().toString());
        assertThat(created.get("validationStatus").asText())
                .isEqualTo("VALID");

        assertThat(createdResponse.headers()
                .firstValue("Location"))
                .hasValueSatisfying(location ->
                        assertThat(location)
                                .endsWith("/api/contract/versions/"
                                        + versionId));

        var persisted = versions.find(versionId).orElseThrow();
        assertThat(persisted.proposalAttemptId())
                .isEqualTo(attemptId);

        var detailResponse = get("/" + versionId);
        assertThat(detailResponse.statusCode()).isEqualTo(200);

        JsonNode detail = json(detailResponse);
        assertThat(detail.get("id").asText())
                .isEqualTo(versionId.toString());
        assertThat(detail.get("documentSha256").asText())
                .isEqualTo(persisted.documentSha256());
        assertThat(detail.get("baselineSha256").asText())
                .isEqualTo(persisted.baselineSha256());
        assertThat(detail.has("openapiYaml")).isFalse();

        var download = get("/" + versionId + "/download");
        assertThat(download.statusCode()).isEqualTo(200);

        assertThat(download.headers()
                .firstValue("Content-Type").orElse(""))
                .contains("application/yaml");

        assertThat(download.headers()
                .firstValue("Content-Disposition").orElse(""))
                .contains("attachment")
                .contains(versionId.toString());

        assertThat(download.body()).isEqualTo(
                persisted.openapiYaml()
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(sha256(download.body()))
                .isEqualTo(persisted.documentSha256());

        assertThat(Files.readAllBytes(baseline))
                .isEqualTo(baselineBefore);

        assertThat(persisted.baselineSha256())
                .isEqualTo(sha256(baselineBefore));

        var attemptAfter = attempts.find(attemptId).orElseThrow();
        assertThat(attemptAfter.proposalJson.toString())
                .isEqualTo(proposalBefore);
        assertThat(attemptAfter.validationJson.toString())
                .isEqualTo(validationBefore);

        assertThat(reviews.findByAttempt(attemptId))
                .contains(reviewBefore);
    }

    @Test
    void historyIsNewestFirstAndBounded() throws Exception {
        UUID firstAttempt = fixture(true, true);
        var firstResponse = post(createBody(firstAttempt));
        assertThat(firstResponse.statusCode()).isEqualTo(201);
        UUID firstId = UUID.fromString(
                json(firstResponse).get("id").asText());

        UUID secondAttempt = fixture(true, true);
        var secondResponse = post(createBody(secondAttempt));
        assertThat(secondResponse.statusCode()).isEqualTo(201);
        UUID secondId = UUID.fromString(
                json(secondResponse).get("id").asText());

        var page0 = get("?page=0&size=1");
        var page1 = get("?page=1&size=1");

        assertThat(page0.statusCode()).isEqualTo(200);
        assertThat(page1.statusCode()).isEqualTo(200);

        JsonNode history = json(page0);

        assertThat(history.has("content")).isTrue();
        assertThat(history.has("page")).isTrue();
        assertThat(history.has("size")).isTrue();
        assertThat(history.has("totalElements")).isTrue();
        assertThat(history.has("totalPages")).isTrue();

        assertThat(history.get("page").asInt()).isZero();
        assertThat(history.get("size").asInt()).isEqualTo(1);
        assertThat(history.get("totalElements").asLong())
                .isGreaterThanOrEqualTo(2);
        assertThat(history.get("totalPages").asInt())
                .isGreaterThanOrEqualTo(2);

        assertThat(history.has("pageable")).isFalse();
        assertThat(history.has("sort")).isFalse();
        assertThat(history.has("numberOfElements")).isFalse();

        assertThat(json(page0).get("content").size())
                .isEqualTo(1);
        assertThat(json(page0).get("content")
                .get(0).get("id").asText())
                .isEqualTo(secondId.toString());

        assertThat(json(page1).get("content").size())
                .isEqualTo(1);
        assertThat(json(page1).get("content")
                .get(0).get("id").asText())
                .isEqualTo(firstId.toString());

        assertThat(get("?page=-1&size=20").statusCode())
                .isEqualTo(400);
        assertThat(get("?page=0&size=101").statusCode())
                .isEqualTo(400);
    }

    @Test
    void invalidAndUnknownRequestsReturnCorrectStatuses()
            throws Exception {

        assertThat(post("{}").statusCode()).isEqualTo(400);
        assertThat(post("{").statusCode()).isEqualTo(400);
        assertThat(post("{\"proposalAttemptId\":\"invalid\"}")
                .statusCode()).isEqualTo(400);

        UUID unknown = UUID.randomUUID();

        var missingProposal = post(createBody(unknown));
        assertThat(missingProposal.statusCode()).isEqualTo(404);
        assertThat(json(missingProposal).get("code").asText())
                .isEqualTo("PROPOSAL_NOT_FOUND");

        assertThat(get("/" + unknown).statusCode())
                .isEqualTo(404);
        assertThat(get("/" + unknown + "/download").statusCode())
                .isEqualTo(404);
        assertThat(get("/invalid-uuid").statusCode())
                .isEqualTo(400);
    }

    @Test
    void approvalAndMappingFailuresReturnConflictsOr422()
            throws Exception {

        UUID missingReview = fixtureWithoutReview();
        var missing = post(createBody(missingReview));
        assertThat(missing.statusCode()).isEqualTo(409);
        assertThat(json(missing).get("code").asText())
                .isEqualTo("REVIEW_MISSING");

        UUID rejected = fixture(false, true);
        var rejectedResponse = post(createBody(rejected));
        assertThat(rejectedResponse.statusCode()).isEqualTo(409);
        assertThat(json(rejectedResponse).get("code").asText())
                .isEqualTo("REVIEW_REJECTED");

        UUID unsupported = fixture(true, false);
        var unsupportedResponse = post(createBody(unsupported));
        assertThat(unsupportedResponse.statusCode())
                .isEqualTo(422);
        assertThat(json(unsupportedResponse).get("code").asText())
                .isEqualTo("UNSUPPORTED_PROPOSAL");

        UUID approved = fixture(true, true);
        assertThat(post(createBody(approved)).statusCode())
                .isEqualTo(201);

        var duplicate = post(createBody(approved));
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json(duplicate).get("code").asText())
                .isEqualTo("VERSION_EXISTS");
    }

    @Test
    void concurrentHttpCreationPersistsExactlyOneVersion()
            throws Exception {

        UUID attemptId = fixture(true, true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Integer> task = () -> {
                start.await();
                return post(createBody(attemptId)).statusCode();
            };

            Future<Integer> first = executor.submit(task);
            Future<Integer> second = executor.submit(task);

            start.countDown();

            int firstStatus = first.get(45, TimeUnit.SECONDS);
            int secondStatus = second.get(45, TimeUnit.SECONDS);

            assertThat(List.of(firstStatus, secondStatus))
                    .containsExactlyInAnyOrder(201, 409);

            var stored = versions.findByProposalAttempt(attemptId)
                    .orElseThrow();

            assertThat(stored.reviewDecisionId())
                    .isEqualTo(reviews.findByAttempt(attemptId)
                            .orElseThrow().reviewId());

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void byProposalReturnsPersistedVersionWithProvenance()
            throws Exception {

        UUID attemptId = fixture(true, true);
        var review = reviews.findByAttempt(attemptId).orElseThrow();

        var createdResponse = post(createBody(attemptId));
        assertThat(createdResponse.statusCode()).isEqualTo(201);

        JsonNode created = json(createdResponse);
        UUID versionId = UUID.fromString(created.get("id").asText());

        var persisted = versions.find(versionId).orElseThrow();

        var lookupResponse = get(
                "/by-proposal/" + attemptId);

        assertThat(lookupResponse.statusCode()).isEqualTo(200);

        JsonNode lookup = json(lookupResponse);

        assertThat(lookup.get("id").asText())
                .isEqualTo(versionId.toString());

        assertThat(lookup.get("proposalAttemptId").asText())
                .isEqualTo(attemptId.toString());

        assertThat(lookup.get("reviewDecisionId").asText())
                .isEqualTo(review.reviewId().toString());

        assertThat(lookup.get("baselineIdentifier").asText())
                .isEqualTo(persisted.baselineIdentifier());

        assertThat(lookup.get("baselineSha256").asText())
                .isEqualTo(persisted.baselineSha256());

        assertThat(lookup.get("documentSha256").asText())
                .isEqualTo(persisted.documentSha256());

        assertThat(lookup.get("validationStatus").asText())
                .isEqualTo(persisted.validationStatus());

        assertThat(lookup.get("validationStatus").asText())
                .isEqualTo("VALID");

        assertThat(lookup.get("createdAt").asText())
                .isNotBlank();

        assertThat(lookup.has("openapiYaml")).isFalse();

        assertThat(versions.findByProposalAttempt(attemptId))
                .contains(persisted);
    }

    @Test
    void byProposalReturns404WithoutCreatingVersion()
            throws Exception {

        UUID attemptId = fixture(true, true);

        assertThat(versions.findByProposalAttempt(attemptId))
                .isEmpty();

        var countBefore = versions.history(
                0, 1).getTotalElements();

        var lookupResponse = get(
                "/by-proposal/" + attemptId);

        assertThat(lookupResponse.statusCode()).isEqualTo(404);

        JsonNode error = json(lookupResponse);

        assertThat(error.get("code").asText())
                .isEqualTo("VERSION_NOT_FOUND");

        assertThat(error.get("message").asText())
                .isNotBlank();

        assertThat(versions.findByProposalAttempt(attemptId))
                .isEmpty();

        var countAfter = versions.history(
                0, 1).getTotalElements();

        assertThat(countAfter).isEqualTo(countBefore);
    }
}
