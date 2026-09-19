package com.ildcvf.contract.service;

import com.ildcvf.contract.model.ContractOperation;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ContractOperationMatcher {

    public MatchResult match(
            String runtimeMethod,
            String runtimePath,
            List<ContractOperation> operations) {

        if (runtimeMethod == null || runtimeMethod.isBlank()
                || runtimePath == null || runtimePath.isBlank()) {
            return MatchResult.undocumented();
        }

        String method = runtimeMethod.toUpperCase(Locale.ROOT);

        List<Candidate> candidates = operations.stream()
                .filter(operation -> operation.method().equals(method))
                .map(operation -> candidate(operation, runtimePath))
                .filter(Candidate::matches)
                .sorted(Comparator
                        .comparingInt(Candidate::staticSegments)
                        .reversed())
                .toList();

        if (candidates.isEmpty()) {
            return MatchResult.undocumented();
        }

        int bestSpecificity = candidates.getFirst().staticSegments();

        List<Candidate> best = candidates.stream()
                .filter(candidate ->
                        candidate.staticSegments() == bestSpecificity)
                .toList();

        if (best.size() > 1) {
            return MatchResult.ambiguous();
        }

        return MatchResult.matched(best.getFirst().operation());
    }

    private Candidate candidate(
            ContractOperation operation,
            String runtimePath) {

        if (operation.pathTemplate().equals(runtimePath)) {
            return new Candidate(
                    operation,
                    true,
                    segmentCount(operation.pathTemplate()));
        }

        String[] templateSegments = segments(operation.pathTemplate());
        String[] runtimeSegments = segments(runtimePath);

        if (templateSegments.length != runtimeSegments.length) {
            return Candidate.noMatch(operation);
        }

        int staticSegments = 0;

        for (int i = 0; i < templateSegments.length; i++) {
            String template = templateSegments[i];
            String runtime = runtimeSegments[i];

            if (isParameter(template)) {
                if (runtime.isEmpty()) {
                    return Candidate.noMatch(operation);
                }
                continue;
            }

            if (!template.equals(runtime)) {
                return Candidate.noMatch(operation);
            }

            staticSegments++;
        }

        return new Candidate(operation, true, staticSegments);
    }

    private boolean isParameter(String segment) {
        return segment.length() >= 3
                && segment.startsWith("{")
                && segment.endsWith("}");
    }

    private int segmentCount(String path) {
        return segments(path).length;
    }

    private String[] segments(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isEmpty())
                .toArray(String[]::new);
    }

    private record Candidate(
            ContractOperation operation,
            boolean matches,
            int staticSegments) {

        private static Candidate noMatch(ContractOperation operation) {
            return new Candidate(operation, false, -1);
        }
    }

    public enum MatchStatus {
        MATCHED,
        UNDOCUMENTED,
        AMBIGUOUS
    }

    public record MatchResult(
            MatchStatus status,
            ContractOperation operation) {

        public static MatchResult matched(ContractOperation operation) {
            return new MatchResult(MatchStatus.MATCHED, operation);
        }

        public static MatchResult undocumented() {
            return new MatchResult(MatchStatus.UNDOCUMENTED, null);
        }

        public static MatchResult ambiguous() {
            return new MatchResult(MatchStatus.AMBIGUOUS, null);
        }
    }
}