package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageUsefulnessBenchmarkService {
    private static final int MIN_REVIEWED_CASES = 50;

    private final MessageUsefulnessClassifier classifier = new MessageUsefulnessClassifier();

    public MessageUsefulnessBenchmarkService(ObjectMapper ignored) {
    }

    public BenchmarkReport evaluateBundled() {
        return evaluate("bundled-seed-v1", 500, List.of(
                new BenchmarkCase(
                        "bundled-answer",
                        "BUNDLED",
                        "Что делать при API 429? Ответ: сначала проверь quota, затем включи exponential backoff и Retry-After для повторных запросов.",
                        "QUESTION_WITH_VALUABLE_ANSWER",
                        0.72,
                        true,
                        "SINGLE_MESSAGE",
                        "ANSWER",
                        "SAFE",
                        "BUNDLED_SEED"
                ),
                new BenchmarkCase(
                        "bundled-link-only",
                        "BUNDLED",
                        "https://example.com полезно, почитайте",
                        "RESOURCE_LINK_COLLECTION",
                        0.68,
                        true,
                        "REJECT",
                        null,
                        "SAFE",
                        "BUNDLED_SEED"
                )
        ));
    }

    public BenchmarkReport evaluate(String datasetVersion, int minimumTrainingCases, List<BenchmarkCase> cases) {
        int total = cases == null ? 0 : cases.size();
        int expectedCandidates = 0;
        int actualCandidates = 0;
        int correctCandidates = 0;
        int correctArtifactTypes = 0;
        int artifactTypeCases = 0;

        if (cases != null) {
            for (BenchmarkCase benchmarkCase : cases) {
                MessageUsefulnessResult result = classifier.classify(
                        benchmarkCase.text(),
                        benchmarkCase.topLabel(),
                        benchmarkCase.classifierConfidence(),
                        benchmarkCase.hardSignal()
                );
                boolean expectedCandidate = "SINGLE_MESSAGE".equals(benchmarkCase.expectedCandidateRoute());
                boolean actualCandidate = "SINGLE_MESSAGE".equals(result.candidateRoute());
                if (expectedCandidate) expectedCandidates++;
                if (actualCandidate) actualCandidates++;
                if (expectedCandidate && actualCandidate) correctCandidates++;
                if (benchmarkCase.expectedArtifactType() != null) {
                    artifactTypeCases++;
                    if (benchmarkCase.expectedArtifactType().equals(result.proposedMaterialType())) correctArtifactTypes++;
                }
            }
        }

        Metrics metrics = new Metrics(
                ratio(correctCandidates, actualCandidates),
                ratio(correctCandidates, expectedCandidates),
                ratio(correctArtifactTypes, artifactTypeCases)
        );
        int required = Math.max(MIN_REVIEWED_CASES, minimumTrainingCases);
        String status = total < required ? "INSUFFICIENT_HUMAN_REVIEWED_CASES" : "READY_FOR_TRAINING_EVALUATION";
        return new BenchmarkReport(datasetVersion, status, total, required, metrics);
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    public record BenchmarkCase(
            String id,
            String source,
            String text,
            String topLabel,
            double classifierConfidence,
            boolean hardSignal,
            String expectedCandidateRoute,
            String expectedArtifactType,
            String expectedSafetyClass,
            String reviewSource
    ) {}

    public record BenchmarkReport(
            String datasetVersion,
            String status,
            int totalCases,
            int requiredCases,
            Metrics metrics
    ) {}

    public record Metrics(
            double candidatePrecision,
            double candidateRecall,
            double artifactTypeAccuracy
    ) {}
}
