package com.ildcvf.drift.web;

import com.ildcvf.drift.domain.DriftAnalysisResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.service.DriftAnalysisService;
import com.ildcvf.traffic.domain.TrafficEvent;
import com.ildcvf.traffic.repository.TrafficEventRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/drift")
public class DriftController {

    private static final int EVENT_LIMIT = 10;

    private final TrafficEventRepository repository;
    private final DriftAnalysisService driftAnalysisService;

    public DriftController(
            TrafficEventRepository repository,
            DriftAnalysisService driftAnalysisService
    ) {
        this.repository = repository;
        this.driftAnalysisService = driftAnalysisService;
    }

    @GetMapping
    public DriftResponse getRecentDrift() {
        List<TrafficEvent> events =
                repository.findRecent(EVENT_LIMIT);

        List<DriftAnalysisResult> results = events.stream()
                .map(driftAnalysisService::analyze)
                .toList();

        int findingCount = results.stream()
                .mapToInt(result -> result.findings().size())
                .sum();

        return new DriftResponse(
                EVENT_LIMIT,
                "LATEST_SEQUENCE_DESC",
                results.size(),
                findingCount,
                results
        );
    }

    public record DriftResponse(
            int selectionLimit,
            String selectionMethod,
            int analyzedEvents,
            int totalFindings,
            List<DriftAnalysisResult> results
    ) {}
}
