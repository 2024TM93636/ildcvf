package com.ildcvf.llm.review;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.persistence.*;
import com.ildcvf.llm.proposal.*;
import com.ildcvf.llm.validation.ProposalValidationResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ReviewDecisionHttpVerificationTest {

    @LocalServerPort
    int port;

    @Autowired
    ProposalAttemptPersistenceService attempts;

    @Autowired
    ProposalAttemptRepository attemptRepository;

    @Autowired
    ReviewDecisionRepository reviewRepository;

    @Autowired
    ReviewDecisionPersistenceService reviews;

    @Test
    void verifiesRealHttpPostGetAndDuplicate() {

        String eventId = "m142-http-" +
                UUID.randomUUID().toString().substring(0, 8);

        String findingId =
                eventId + ":RESPONSE_TYPE_MISMATCH:id";

        UUID attemptId = attempts.start(eventId, findingId);

        try {
            ContractChangeProposal proposal =
                    new ContractChangeProposal(
                            "m142-http-verification",
                            eventId,
                            findingId,
                            DriftType.RESPONSE_TYPE_MISMATCH,
                            ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                            "GET",
                            "/api/residents/1",
                            "/api/residents/{id}",
                            "id",
                            "integer",
                            "string",
                            "string",
                            "Controlled HTTP verification"
                    );

            attempts.completeValidated(
                    attemptId,
                    proposal,
                    ProposalValidationResult.validResult()
            );

            var before = attempts.find(attemptId).orElseThrow();

            String originalProposal =
                    before.proposalJson.toString();

            String originalValidation =
                    before.validationJson.toString();

            String baseUrl =
                    "http://localhost:" + port + "/api/llm/reviews";

            RestClient client = RestClient.create();

            Map<String, Object> request = Map.of(
                    "proposalAttemptId", attemptId.toString(),
                    "decision", "APPROVED",
                    "reviewerId", "test-reviewer",
                    "justification",
                    "Controlled M14.2 HTTP verification"
            );

            // 1. Actual HTTP POST
            ResponseEntity<ReviewDecisionRecord> created =
                    client.post()
                            .uri(baseUrl)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .toEntity(ReviewDecisionRecord.class);

            assertThat(created.getStatusCode().value())
                    .isEqualTo(201);

            assertThat(created.getBody()).isNotNull();

            assertThat(created.getBody().proposalAttemptId())
                    .isEqualTo(attemptId);

            assertThat(created.getBody().decision())
                    .isEqualTo(ReviewDecision.APPROVED);

            System.out.println("HTTP POST: 201 Created");

            // 2. Actual HTTP GET
            ResponseEntity<ReviewDecisionRecord> fetched =
                    client.get()
                            .uri(baseUrl + "/" + attemptId)
                            .retrieve()
                            .toEntity(ReviewDecisionRecord.class);

            assertThat(fetched.getStatusCode().value())
                    .isEqualTo(200);

            assertThat(fetched.getBody()).isNotNull();

            assertThat(fetched.getBody().reviewId())
                    .isEqualTo(created.getBody().reviewId());

            System.out.println("HTTP GET: 200 OK");

            // 3. Repeated POST must return 409
            int duplicateStatus;

            try {
                client.post()
                        .uri(baseUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();

                duplicateStatus = 201;

            } catch (
                    org.springframework.web.client
                            .HttpClientErrorException ex) {

                duplicateStatus =
                        ex.getStatusCode().value();
            }

            assertThat(duplicateStatus).isEqualTo(409);

            System.out.println(
                    "Duplicate HTTP POST: 409 Conflict"
            );

            // 4. Persistence and evidence integrity
            var persisted =
                    reviews.findByAttempt(attemptId).orElseThrow();

            assertThat(persisted.reviewId())
                    .isEqualTo(created.getBody().reviewId());

            assertThat(persisted.decision())
                    .isEqualTo(ReviewDecision.APPROVED);

            var after = attempts.find(attemptId).orElseThrow();

            assertThat(after.status)
                    .isEqualTo(ProposalAttemptStatus.VALIDATED);

            assertThat(after.proposalJson.toString())
                    .isEqualTo(originalProposal);

            assertThat(after.validationJson.toString())
                    .isEqualTo(originalValidation);

            System.out.println(
                    "Persistence and original evidence: PASS"
            );

        } finally {
            // Remove only records created by this test.
            reviewRepository
                    .findByProposalAttempt_Id(attemptId)
                    .ifPresent(reviewRepository::delete);

            attemptRepository.deleteById(attemptId);
        }
    }
}
