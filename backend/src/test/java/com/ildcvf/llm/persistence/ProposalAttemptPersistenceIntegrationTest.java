package com.ildcvf.llm.persistence;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.validation.ProposalValidationCode;
import com.ildcvf.llm.validation.ProposalValidationError;
import com.ildcvf.llm.validation.ProposalValidationResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/** Integration tests against the configured PostgreSQL database; no provider calls. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposalAttemptPersistenceIntegrationTest {
    private static final String EVENT = "m13c-" + UUID.randomUUID().toString().substring(0, 12);
    private static final String FINDING = EVENT + ":RESPONSE_TYPE_MISMATCH:id";
    private static UUID restartId;

    @Autowired ProposalAttemptPersistenceService service;
    @Autowired ProposalAttemptRepository repository;
    @Autowired ObjectMapper mapper;

    private ContractChangeProposal proposal(String id) {
        return new ContractChangeProposal(id, EVENT, FINDING,
            DriftType.RESPONSE_TYPE_MISMATCH, ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
            "GET", "/api/residents/2", "/api/residents/{id}", "id",
            "integer", "string", "string", "Observed id type differs from documented type.");
    }

    @Test @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void createCompleteAndRecoverAfterNewApplicationContext() {
        restartId = service.start(EVENT, FINDING);
        var started = service.find(restartId).orElseThrow();
        assertThat(started.status).isEqualTo(ProposalAttemptStatus.IN_PROGRESS);
        assertThat(started.completedAt).isNull();
        service.completeValidated(restartId, proposal("m13c-proposal"), ProposalValidationResult.validResult());
        var completed = service.find(restartId).orElseThrow();
        assertThat(completed.status).isEqualTo(ProposalAttemptStatus.VALIDATED);
        assertThat(completed.completedAt).isNotNull();
        assertThat(completed.proposalJson.get("proposedValue").asText()).isEqualTo("string");
        assertThat(completed.validationJson.get("errors").isArray()).isTrue();
        assertThat(completed.validationJson.get("errors").size()).isZero();
        assertThat(mapper.treeToValue(completed.proposalJson, ContractChangeProposal.class))
            .isEqualTo(proposal("m13c-proposal"));
    }

    @Test @Order(2)
    void previouslySavedRecordSurvivesFreshSpringContext() {
        assertThat(restartId).isNotNull();
        var recovered = service.find(restartId).orElseThrow();
        assertThat(recovered.status).isEqualTo(ProposalAttemptStatus.VALIDATED);
        assertThat(recovered.proposalJson.get("proposalId").asText()).isEqualTo("m13c-proposal");
    }

    @Test @Order(3)
    void rejectedFailureTimeoutAndHistoryRoundTrip() {
        UUID rejected = service.start(EVENT, FINDING);
        var invalid = ProposalValidationResult.invalid(List.of(
            new ProposalValidationError(ProposalValidationCode.PROPOSED_VALUE_NOT_GROUNDED, "Unsupported type")));
        service.completeRejected(rejected, proposal("rejected"), invalid);
        var r = service.find(rejected).orElseThrow();
        assertThat(r.status).isEqualTo(ProposalAttemptStatus.REJECTED);
        assertThat(r.validationJson.get("errors").size()).isEqualTo(1);
        assertThat(r.validationJson.get("errors").get(0).get("code").asText())
            .isEqualTo("PROPOSED_VALUE_NOT_GROUNDED");

        UUID failed = service.start(EVENT, FINDING);
        service.completeFailure(failed, ProposalAttemptStatus.PROVIDER_FAILURE, "HTTP_ERROR", 400, "Sanitized provider failure");
        var f = service.find(failed).orElseThrow();
        assertThat(f.status).isEqualTo(ProposalAttemptStatus.PROVIDER_FAILURE);
        assertThat(f.providerHttpStatus).isEqualTo(400);
        assertThat(f.failureMessage).isEqualTo("Sanitized provider failure");
        assertThat(f.proposalJson).isNull();

        UUID timeout = service.start(EVENT, FINDING);
        service.completeFailure(timeout, ProposalAttemptStatus.TIMEOUT, "TIMEOUT", null, "Request timed out");
        assertThat(service.find(timeout).orElseThrow().status).isEqualTo(ProposalAttemptStatus.TIMEOUT);

        assertThat(service.byFinding(EVENT, FINDING, 100)).hasSize(4);
        assertThat(service.byEvent(EVENT, 2)).hasSize(2);
        assertThat(service.history(1)).hasSize(1);
        assertThatThrownBy(() -> service.history(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.history(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @Order(4)
    void activeDuplicateRejectedAndStaleIdentified() {
        UUID active = service.start(EVENT, FINDING);
        assertThatThrownBy(() -> service.start(EVENT, FINDING))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.stale(Duration.ofNanos(1), 100))
            .extracting(a -> a.id).contains(active);
        assertThat(service.stale(Duration.ofDays(365), 100))
            .extracting(a -> a.id).doesNotContain(active);
        service.completeFailure(active, ProposalAttemptStatus.PARSE_FAILURE, "PARSE_FAILURE", null, "Invalid JSON");
        assertThat(service.start(EVENT, FINDING)).isNotNull();
    }

    @AfterAll
    static void cleanup(@Autowired ProposalAttemptRepository repository) {
        repository.deleteAll(repository.findByTrafficEventIdOrderByCreatedAtDescIdDesc(
            EVENT, org.springframework.data.domain.PageRequest.of(0, 100)));
    }
}
