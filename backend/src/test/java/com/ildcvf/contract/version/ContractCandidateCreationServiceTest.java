package com.ildcvf.contract.version;

import com.ildcvf.contract.generation.*;
import com.ildcvf.llm.persistence.*;
import com.ildcvf.llm.proposal.*;
import com.ildcvf.llm.review.*;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContractCandidateCreationServiceTest {

    @Test
    void generatorFailureMapsToStructuredFailureCode() {
        var proposals = mock(ProposalAttemptPersistenceService.class);
        var reviews = mock(ReviewDecisionPersistenceService.class);
        var generator = mock(ContractCandidateGenerator.class);
        var versions = mock(ContractVersionPersistenceService.class);
        var mapper = new ObjectMapper();

        UUID attemptId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        var attempt = new ProposalAttemptEntity(
                attemptId,
                "disposable-event",
                "disposable-finding",
                java.time.Instant.now()
        );

        attempt.status = ProposalAttemptStatus.VALIDATED;

        var proposal = new ContractChangeProposal(
                "disposable-proposal",
                "disposable-event",
                "disposable-finding",
                com.ildcvf.drift.domain.DriftType.RESPONSE_TYPE_MISMATCH,
                ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                "GET",
                "/api/residents/1",
                "/api/residents/{id}",
                "id",
                "integer",
                "string",
                "string",
                "Test fixture"
        );

        attempt.proposalJson = mapper.valueToTree(proposal);

        when(proposals.find(attemptId)).thenReturn(Optional.of(attempt));

        when(reviews.findByAttempt(attemptId)).thenReturn(
                Optional.of(new ReviewDecisionRecord(
                        reviewId,
                        attemptId,
                        ReviewDecision.APPROVED,
                        "test-reviewer",
                        "Test approval",
                        java.time.Instant.now()
                ))
        );

        when(versions.findByProposalAttempt(attemptId))
                .thenReturn(Optional.empty());

        when(generator.generate(any(ContractChangeProposal.class)))
                .thenThrow(new CandidateGenerationException(
                        "BASELINE_INVALID",
                        "Disposable generator failure"
                ));

        var service = new ContractCandidateCreationService(
                proposals, reviews, generator, versions, mapper
        );

        assertThatThrownBy(() -> service.create(attemptId))
                .isInstanceOf(ContractCandidateCreationException.class)
                .satisfies(error -> assertThat(
                        ((ContractCandidateCreationException) error).code()
                ).isEqualTo(
                        ContractCandidateCreationException.Code.GENERATED_DOCUMENT_INVALID
                ));

        verify(versions, never()).saveValidated(any());
    }
}
