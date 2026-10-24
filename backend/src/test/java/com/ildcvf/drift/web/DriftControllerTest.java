package com.ildcvf.drift.web;

import com.ildcvf.drift.domain.DriftAnalysisResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.drift.domain.EvaluationStatus;
import com.ildcvf.drift.service.DriftAnalysisService;
import com.ildcvf.traffic.domain.TrafficEvent;
import com.ildcvf.traffic.repository.TrafficEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriftControllerTest {

    private TrafficEventRepository repository;
    private DriftAnalysisService analysisService;
    private DriftController controller;

    @BeforeEach
    void setUp() {
        repository = mock(TrafficEventRepository.class);
        analysisService = mock(DriftAnalysisService.class);
        controller = new DriftController(repository, analysisService);
    }

    @Test
    void emptyRepositoryReturnsEmptyResponse() {
        when(repository.findRecent(10)).thenReturn(List.of());

        var response = controller.getRecentDrift();

        assertEquals(10, response.selectionLimit());
        assertEquals("LATEST_SEQUENCE_DESC", response.selectionMethod());
        assertEquals(0, response.analyzedEvents());
        assertEquals(0, response.totalFindings());
        assertTrue(response.results().isEmpty());

        verifyNoInteractions(analysisService);
    }

    @Test
    void analyzesLatestTwentyEvents() {
        List<TrafficEvent> events = java.util.stream.LongStream
                .rangeClosed(1, 10)
                .mapToObj(sequence -> mockEvent(sequence))
                .toList();

        when(repository.findRecent(10)).thenReturn(events);

        for (TrafficEvent event : events) {
            DriftAnalysisResult analysisResult = result(event, List.of());

            when(analysisService.analyze(event))
                    .thenReturn(analysisResult);
        }

        var response = controller.getRecentDrift();

        assertEquals(10, response.analyzedEvents());
        assertEquals(10, response.results().size());

        verify(repository).findRecent(10);
        verify(analysisService, times(10)).analyze(any(TrafficEvent.class));
    }

    @Test
    void aggregatesFindingCounts() {
        TrafficEvent first = mockEvent(1);
        TrafficEvent second = mockEvent(2);

        when(repository.findRecent(10))
                .thenReturn(List.of(first, second));

        DriftFinding finding = new DriftFinding(
                "finding-1",
                "event-1",
                1,
                DriftType.UNDOCUMENTED_OPERATION,
                DriftSeverity.WARNING,
                "GET",
                "/api/unknown",
                null,
                null,
                "documented operation",
                "GET /api/unknown",
                "Undocumented operation"
        );

        DriftAnalysisResult firstResult = result(first, List.of(finding));
        DriftAnalysisResult secondResult = result(second, List.of());

        when(analysisService.analyze(first))
                .thenReturn(firstResult);

        when(analysisService.analyze(second))
                .thenReturn(secondResult);

        var response = controller.getRecentDrift();

        assertEquals(2, response.analyzedEvents());
        assertEquals(1, response.totalFindings());
    }

    @Test
    void propagatesRepositoryFailure() {
        when(repository.findRecent(10))
                .thenThrow(new IllegalStateException("Repository unavailable"));

        assertThrows(
                IllegalStateException.class,
                controller::getRecentDrift
        );

        verifyNoInteractions(analysisService);
    }

    private TrafficEvent mockEvent(long sequence) {
        TrafficEvent event = mock(TrafficEvent.class);

        when(event.sequence()).thenReturn(sequence);
        when(event.eventId()).thenReturn("event-" + sequence);

        return event;
    }

    private DriftAnalysisResult result(
            TrafficEvent event,
            List<DriftFinding> findings
    ) {
        return new DriftAnalysisResult(
                event.eventId(),
                event.sequence(),
                EvaluationStatus.EVALUATED,
                "GET",
                "/api/residents",
                "/api/residents",
                findings,
                null
        );
    }
}
