package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicClusterGuideCandidateEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TopicDiscussionClusterEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentQualityGateTest {

    private final ContentQualityGate gate = new ContentQualityGate();

    @Test
    void genericLabelBlocksMaterial() {
        ContentQualityGate.GateResult result = gate.evaluate(
            decision(ContentType.USEFUL_INFO, "Codex", "Specific note", "NORMAL", true, false),
            cluster(),
            null,
            messages()
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("generic topic label");
    }

    @Test
    void genericClusterGuideTopicBlocksNonGuideMaterial() {
        ContentQualityGate.GateResult result = gate.evaluate(
            decision(
                ContentType.RISK_INSIGHT,
                "Риск: Практический гайд по теме кластера",
                null,
                "NORMAL",
                true,
                false
            ),
            cluster(),
            null,
            messages()
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("generic material topic");
    }

    @Test
    void genericGuideAngleBlocksGuideGeneration() {
        TopicClusterGuideCandidateEntity candidate = new TopicClusterGuideCandidateEntity();
        candidate.setGuideAngle("Практический гайд по теме кластера");

        ContentQualityGate.GateResult result = gate.evaluate(
            decision(ContentType.GUIDE, "Codex logs sqlite", "Практический гайд по теме кластера", "NORMAL", true, true),
            cluster(),
            candidate,
            messages()
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("generic guide angle");
    }

    @Test
    void deferredDoesNotCreateMaterial() {
        ContentQualityGate.GateResult result = gate.evaluate(
            decision(ContentType.DEFERRED, "Смешанное обсуждение GLM", "Материал отложен", "NORMAL", false, false),
            cluster(),
            null,
            messages()
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("DEFERRED");
    }

    @Test
    void questionAnswerSubtypeDoesNotCreateMaterial() {
        ContentQualityGate.GateResult result = gate.evaluate(
            decision(ContentType.USEFUL_INFO, "Выбор модели для стратегии", "Краткий ответ", "NORMAL", true, false, "QUESTION_ANSWER"),
            cluster(),
            null,
            messages()
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("content subtype");
    }

    @Test
    void abuseGuideCanPassWithShortSingleSource() {
        ContentQualityGate.GateResult result = gate.evaluate(
            decision(
                ContentType.GUIDE,
                "Canva Business: абуз trial и лимитов",
                "Как разобрать абуз trial/лимитов и оценить риски",
                "ABUSE_OR_LIMIT_EXPLOIT",
                true,
                true,
                "ABUSE_OR_LIMIT_EXPLOIT",
                25
            ),
            cluster(),
            null,
            messages().subList(0, 1)
        );

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void bypassGuideIsBlockedBySafety() {
        ContentQualityGate.GateResult result = gate.evaluate(
            decision(ContentType.GUIDE, "Риски обхода Claude Code", "Как обходить Claude Code", "BYPASS", true, true),
            cluster(),
            null,
            messages()
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("safety category");
    }

    @Test
    void specificGuidePasses() {
        ContentQualityGate.GateResult result = gate.evaluate(
            decision(ContentType.GUIDE, "Проблемы с базой logs sqlite в Codex", "Как остановить рост logs sqlite в Codex", "NORMAL", true, true),
            cluster(),
            null,
            messages()
        );

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void broadMaterialWithTooManySourcesIsBlocked() {
        ContentQualityGate.GateResult result = gate.evaluate(
            decision(ContentType.USEFUL_INFO, "RuFlow voice-to-code", "Short material", "NORMAL", true, false),
            cluster(),
            null,
            manyMessages(18, "RuFlow voice-to-code link")
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("too many source messages");
    }

    @Test
    void riskMaterialWithoutRiskEvidenceIsBlocked() {
        ContentQualityGate.GateResult result = gate.evaluate(
            decision(ContentType.USEFUL_INFO, "Риски обхода ограничений Claude Code", "Voice-to-code note", "BYPASS", true, false, "BYPASS"),
            cluster(),
            null,
            List.of(message(1L, "RuFlow voice-to-code repository for Windows"))
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("risk material lacks matching source evidence");
    }

    private ContentRoutingDecision decision(
        ContentType type,
        String label,
        String angle,
        String safety,
        boolean shouldCreate,
        boolean shouldGuide
    ) {
        return new ContentRoutingDecision(
            type,
            null,
            label,
            label + " summary",
            label,
            label + " content summary",
            "key",
            angle,
            shouldCreate,
            shouldGuide,
            0.82,
            75,
            type == ContentType.GUIDE ? 80 : 40,
            40,
            70,
            "NORMAL".equals(safety) ? 10 : 85,
            20,
            safety,
            type == ContentType.GUIDE ? "GUIDE" : "MATERIAL",
            "test",
            List.of(),
            List.of(),
            null,
            null
        );
    }

    private ContentRoutingDecision decision(
        ContentType type,
        String label,
        String angle,
        String safety,
        boolean shouldCreate,
        boolean shouldGuide,
        String subtype,
        int evidenceScore
    ) {
        return new ContentRoutingDecision(
            type,
            subtype,
            label,
            label + " summary",
            label,
            label + " content summary",
            "key",
            angle,
            shouldCreate,
            shouldGuide,
            0.82,
            75,
            type == ContentType.GUIDE ? 80 : 40,
            40,
            evidenceScore,
            "NORMAL".equals(safety) ? 10 : 85,
            20,
            safety,
            type == ContentType.GUIDE ? "GUIDE" : "MATERIAL",
            "test",
            List.of(),
            List.of(),
            null,
            null
        );
    }

    private ContentRoutingDecision decision(
        ContentType type,
        String label,
        String angle,
        String safety,
        boolean shouldCreate,
        boolean shouldGuide,
        String subtype
    ) {
        return new ContentRoutingDecision(
            type,
            subtype,
            label,
            label + " summary",
            label,
            label + " content summary",
            "key",
            angle,
            shouldCreate,
            shouldGuide,
            0.82,
            75,
            type == ContentType.GUIDE ? 80 : 40,
            40,
            70,
            "NORMAL".equals(safety) ? 10 : 85,
            20,
            safety,
            type == ContentType.GUIDE ? "GUIDE" : "MATERIAL",
            "test",
            List.of(),
            List.of(),
            null,
            null
        );
    }

    private TopicDiscussionClusterEntity cluster() {
        TopicDiscussionClusterEntity cluster = new TopicDiscussionClusterEntity();
        cluster.setStartAt(Instant.parse("2026-06-19T10:00:00Z"));
        cluster.setEndAt(Instant.parse("2026-06-19T10:10:00Z"));
        return cluster;
    }

    private List<MessageEntity> messages() {
        MessageEntity first = new MessageEntity();
        first.setId(1L);
        first.setMessageDate(Instant.parse("2026-06-19T10:00:00Z"));
        first.setText("Codex logs sqlite grows quickly");
        MessageEntity second = new MessageEntity();
        second.setId(2L);
        second.setMessageDate(Instant.parse("2026-06-19T10:05:00Z"));
        second.setText("Stop process and validate database size");
        return List.of(first, second);
    }

    private List<MessageEntity> manyMessages(int count, String text) {
        java.util.ArrayList<MessageEntity> result = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(message((long) i + 1, text + " #" + i));
        }
        return result;
    }

    private MessageEntity message(Long id, String text) {
        MessageEntity message = new MessageEntity();
        message.setId(id);
        message.setMessageDate(Instant.parse("2026-06-19T10:00:00Z").plusSeconds(id));
        message.setText(text);
        return message;
    }
}
