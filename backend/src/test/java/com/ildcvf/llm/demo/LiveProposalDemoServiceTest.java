package com.ildcvf.llm.demo;

import com.ildcvf.contract.service.ContractBaselineLoader;
import com.ildcvf.drift.domain.DriftAnalysisResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.service.DriftAnalysisService;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.persistence.ProposalAttemptPersistenceService;
import java.util.UUID;
import com.ildcvf.llm.support.M7TestFixtures;
import com.ildcvf.llm.validation.ProposalValidationCode;
import com.ildcvf.traffic.domain.TrafficEvent;
import com.ildcvf.traffic.repository.TrafficEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LiveProposalDemoServiceTest {

    private TrafficEventRepository repository;
    private DriftAnalysisService driftService;
    private ContractBaselineLoader baselineLoader;
    private GroqDemoClient provider;
    private ProposalAttemptPersistenceService attempts;
    private LiveProposalDemoService service;

    private DriftFinding finding;
    private DriftAnalysisResult analysis;

    @BeforeEach
    void setUp() {
        repository = mock(TrafficEventRepository.class);
        driftService = mock(DriftAnalysisService.class);
        baselineLoader = mock(ContractBaselineLoader.class);
        provider = mock(GroqDemoClient.class);
        attempts = mock(ProposalAttemptPersistenceService.class);
        when(attempts.start(anyString(), anyString())).thenReturn(UUID.randomUUID());

        service = new LiveProposalDemoService(
                repository,
                driftService,
                baselineLoader,
                provider,
                attempts
        );

        finding = M7TestFixtures.documentedFinding();
        analysis = M7TestFixtures.analysis(finding);
    }

    @Test
    void rejectsMissingIdentifiersBeforeProviderInvocation() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generate(" ", finding.findingId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(repository, driftService, baselineLoader, provider);
    }

    @Test
    void rejectsUnknownEventBeforeProviderInvocation() {
        when(repository.findById("missing-event"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generate("missing-event", finding.findingId())
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(driftService, baselineLoader, provider);
    }

    @Test
    void rejectsUnknownFindingBeforeProviderInvocation() {
        arrangePersistedEvent();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generate(
                        M7TestFixtures.EVENT_ID,
                        "nonexistent-finding"
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(baselineLoader, provider);
    }

    @Test
    void validatesGroundedProposalFromSelectedFinding() {
        arrangePersistedEvent();

        when(baselineLoader.getBaseline())
                .thenReturn(M7TestFixtures.baseline());

        when(provider.generate(any(GroundedEvidencePackage.class)))
                .thenAnswer(invocation -> {
                    GroundedEvidencePackage evidence =
                            invocation.getArgument(0);

                    assertEquals(M7TestFixtures.EVENT_ID, evidence.trafficEventId());
                    assertEquals(finding.findingId(), evidence.findingId());
                    assertEquals(finding.type(), evidence.driftType());
                    assertEquals(finding.fieldPath(), evidence.fieldPath());

                    ContractChangeProposal proposal =
                            M7TestFixtures.validProposal(evidence);

                    return new GroqDemoClient.Outcome(
                            "PARSED",
                            "SUCCESS",
                            proposal,
                            null,
                            200,
                            null
                    );
                });

        LiveProposalDemoService.Response result = service.generate(
                M7TestFixtures.EVENT_ID,
                finding.findingId()
        );

        assertEquals("VALIDATED", result.status());
        assertEquals("SUCCESS", result.providerStatus());
        assertNotNull(result.proposal());
        assertNotNull(result.validation());
        assertTrue(result.validation().valid());

        verify(provider, times(1)).generate(any(GroundedEvidencePackage.class));
        verifyNoMoreInteractions(provider);
    }

    @Test
    void rejectsUngroundedProviderProposalDeterministically() {
        arrangePersistedEvent();

        when(baselineLoader.getBaseline())
                .thenReturn(M7TestFixtures.baseline());

        when(provider.generate(any(GroundedEvidencePackage.class)))
                .thenAnswer(invocation -> {
                    GroundedEvidencePackage evidence =
                            invocation.getArgument(0);

                    ContractChangeProposal valid =
                            M7TestFixtures.validProposal(evidence);

                    ContractChangeProposal invalid =
                            new ContractChangeProposal(
                                    valid.proposalId(),
                                    valid.sourceTrafficEventId(),
                                    valid.sourceFindingId(),
                                    valid.driftType(),
                                    valid.changeKind(),
                                    valid.method(),
                                    valid.runtimePath(),
                                    valid.matchedOpenApiPath(),
                                    valid.targetFieldPath(),
                                    valid.expected(),
                                    valid.observed(),
                                    "invented-value",
                                    valid.rationale()
                            );

                    return new GroqDemoClient.Outcome(
                            "PARSED",
                            "SUCCESS",
                            invalid,
                            null,
                            200,
                            null
                    );
                });

        LiveProposalDemoService.Response result = service.generate(
                M7TestFixtures.EVENT_ID,
                finding.findingId()
        );

        assertEquals("REJECTED", result.status());
        assertNotNull(result.validation());
        assertFalse(result.validation().valid());

        assertTrue(
                result.validation().errors().stream()
                        .anyMatch(error ->
                                error.code() ==
                                        ProposalValidationCode.PROPOSED_VALUE_NOT_GROUNDED)
        );

        verify(provider, times(1)).generate(any(GroundedEvidencePackage.class));
    }

    @Test
    void returnsProviderFailureWithoutRetry() {
        arrangePersistedEvent();

        when(baselineLoader.getBaseline())
                .thenReturn(M7TestFixtures.baseline());

        when(provider.generate(any(GroundedEvidencePackage.class)))
                .thenReturn(new GroqDemoClient.Outcome(
                        "PROVIDER_FAILURE",
                        "HTTP_ERROR",
                        null,
                        "Provider returned HTTP 503.",
                        503,
                        null
                ));

        LiveProposalDemoService.Response result = service.generate(
                M7TestFixtures.EVENT_ID,
                finding.findingId()
        );

        assertEquals("PROVIDER_FAILURE", result.status());
        assertEquals("HTTP_ERROR", result.providerStatus());
        assertEquals(503, result.providerHttpStatus());
        assertNull(result.proposal());
        assertNull(result.validation());

        verify(provider, times(1)).generate(any(GroundedEvidencePackage.class));
        verifyNoMoreInteractions(provider);
    }

    @Test
    void returnsTimeoutWithoutRetry() {
        arrangePersistedEvent();

        when(baselineLoader.getBaseline())
                .thenReturn(M7TestFixtures.baseline());

        when(provider.generate(any(GroundedEvidencePackage.class)))
                .thenReturn(new GroqDemoClient.Outcome(
                        "TIMEOUT",
                        "TIMEOUT",
                        null,
                        "Provider request timed out.",
                        null,
                        null
                ));

        LiveProposalDemoService.Response result = service.generate(
                M7TestFixtures.EVENT_ID,
                finding.findingId()
        );

        assertEquals("TIMEOUT", result.status());
        assertEquals("TIMEOUT", result.providerStatus());
        assertNull(result.proposal());

        verify(provider, times(1)).generate(any(GroundedEvidencePackage.class));
        verifyNoMoreInteractions(provider);
    }

    @Test
    void rejectsConcurrentActiveAttemptWithoutCallingProvider() {
        arrangePersistedEvent();
        when(baselineLoader.getBaseline()).thenReturn(M7TestFixtures.baseline());
        when(attempts.start(anyString(), anyString()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("unique active finding"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(M7TestFixtures.EVENT_ID, finding.findingId()));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verifyNoInteractions(provider);
    }

    @Test
    void persistsParseFailureWithoutExposingRawProviderResponse() {
        arrangePersistedEvent();
        when(baselineLoader.getBaseline()).thenReturn(M7TestFixtures.baseline());
        when(provider.generate(any(GroundedEvidencePackage.class)))
                .thenReturn(new GroqDemoClient.Outcome("PARSE_FAILURE", "SUCCESS", null,
                        "Parse failure", 200, "PRIVATE RAW PROVIDER BODY"));
        var result = service.generate(M7TestFixtures.EVENT_ID, finding.findingId());
        assertEquals("PARSE_FAILURE", result.status());
        assertNotNull(result.attemptId());
        verify(attempts).completeFailure(eq(result.attemptId()),
                eq(com.ildcvf.llm.persistence.ProposalAttemptStatus.PARSE_FAILURE),
                eq("SUCCESS"), eq(200), eq("Provider response or proposal could not be parsed."));
        assertFalse(result.message().contains("PRIVATE"));
    }

    @Test
    void persistsTimeoutAsTerminalOutcome() {
        arrangePersistedEvent();
        when(baselineLoader.getBaseline()).thenReturn(M7TestFixtures.baseline());
        when(provider.generate(any(GroundedEvidencePackage.class)))
                .thenReturn(new GroqDemoClient.Outcome("TIMEOUT", "TIMEOUT", null,
                        "Provider request timed out.", null, null));
        var result = service.generate(M7TestFixtures.EVENT_ID, finding.findingId());
        verify(attempts).completeFailure(eq(result.attemptId()),
                eq(com.ildcvf.llm.persistence.ProposalAttemptStatus.TIMEOUT),
                eq("TIMEOUT"), isNull(), eq("Provider request timed out."));
    }

    private void arrangePersistedEvent() {
        TrafficEvent event = mock(TrafficEvent.class);

        when(repository.findById(M7TestFixtures.EVENT_ID))
                .thenReturn(Optional.of(event));

        when(driftService.analyze(event))
                .thenReturn(analysis);
    }
}