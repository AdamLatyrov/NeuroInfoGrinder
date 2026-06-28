package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideUsefulnessScorerTest {

    private final GuideUsefulnessScorer scorer = new GuideUsefulnessScorer();

    @Test
    void clampsScoreToRange() {
        GuideEntity guide = guide("Setup API", "Configure the /api endpoint with token and deploy from GitHub.", 1.4);
        ClassifierResult result = new ClassifierResult(
            0.95,
            true,
            List.of(ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE),
            true,
            List.of(1L),
            "practical"
        );

        assertThat(scorer.score(guide, null, result)).isBetween(0, 100);
    }

    @Test
    void badJsonTitleGetsLowScore() {
        GuideEntity guide = guide("{\"bad\":true}", "Normal guide content with enough text to pass short-content guard.", 0.9);

        assertThat(scorer.score(guide, null, null)).isLessThanOrEqualTo(20);
    }

    @Test
    void shortContentGetsLowScore() {
        GuideEntity guide = guide("Short", "Шаги", 0.9);

        assertThat(scorer.score(guide, null, null)).isLessThanOrEqualTo(20);
    }

    @Test
    void practicalTechnicalGuideGetsHighScore() {
        GuideEntity guide = guide(
            "Cloudflare Worker proxy setup",
            "Step 1: create a Cloudflare Worker. Step 2: route /api to the proxy. Source: https://example.com/setup",
            0.72
        );
        MessageEntity source = new MessageEntity();
        source.setText("GitHub repo contains setup steps for Claude API proxy.");
        ClassifierResult result = new ClassifierResult(
            0.92,
            true,
            List.of(ClassificationLabels.PRACTICAL_GUIDE_CANDIDATE, ClassificationLabels.SOLUTION_MENTION),
            true,
            List.of(1L),
            "technical how-to"
        );

        assertThat(scorer.score(guide, source, result)).isGreaterThanOrEqualTo(70);
    }

    @Test
    void spamOfferWithoutPracticalValueDoesNotScoreHigh() {
        GuideEntity guide = guide("Claude sale", "Promo offer. Buy account cheap today. No setup details.", 0.95);
        ClassifierResult result = new ClassifierResult(
            0.95,
            true,
            List.of(ClassificationLabels.SPAM_OR_AD),
            true,
            List.of(1L),
            "offer"
        );

        assertThat(scorer.score(guide, null, result)).isLessThanOrEqualTo(35);
    }

    @Test
    void explicitNonGuideCandidateCapsScore() {
        GuideEntity guide = guide("API setup", "Step 1: configure API proxy. Step 2: deploy from GitHub.", 0.9);
        ClassifierResult result = new ClassifierResult(
            0.9,
            true,
            List.of(ClassificationLabels.AI_TOOL_OR_PROVIDER),
            false,
            List.of(1L),
            "not a guide"
        );

        assertThat(scorer.score(guide, null, result)).isLessThanOrEqualTo(30);
    }

    private GuideEntity guide(String title, String content, Double confidence) {
        GuideEntity guide = new GuideEntity();
        guide.setTitle(title);
        guide.setContent(content);
        guide.setContentMarkdown(content);
        guide.setConfidence(confidence);
        return guide;
    }
}
