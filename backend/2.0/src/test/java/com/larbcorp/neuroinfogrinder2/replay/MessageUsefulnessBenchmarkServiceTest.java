package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageUsefulnessBenchmarkServiceTest {
    @Test
    void smallReviewedSeedReportsMetricsButRemainsInsufficientForTraining() {
        MessageUsefulnessBenchmarkService service = new MessageUsefulnessBenchmarkService(new ObjectMapper());
        List<MessageUsefulnessBenchmarkService.BenchmarkCase> cases = List.of(
            new MessageUsefulnessBenchmarkService.BenchmarkCase(
                "positive", "TEST", "Что делать при API 429? Ответ: сначала проверь quota, затем включи exponential backoff и Retry-After для повторных запросов.",
                "QUESTION_WITH_VALUABLE_ANSWER", 0.72, true, "SINGLE_MESSAGE", "ANSWER", "SAFE", "HUMAN_REVIEWED"
            ),
            new MessageUsefulnessBenchmarkService.BenchmarkCase(
                "negative", "TEST", "https://example.com полезно, почитайте", "RESOURCE_LINK_COLLECTION", 0.68, true,
                "REJECT", null, "SAFE", "HUMAN_REVIEWED"
            )
        );

        MessageUsefulnessBenchmarkService.BenchmarkReport report = service.evaluate("test-v1", 500, cases);

        assertEquals("INSUFFICIENT_HUMAN_REVIEWED_CASES", report.status());
        assertEquals(2, report.totalCases());
        assertEquals(1.0, report.metrics().candidatePrecision());
        assertEquals(1.0, report.metrics().candidateRecall());
        assertEquals(1.0, report.metrics().artifactTypeAccuracy());
    }
}
