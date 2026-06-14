package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignalScorerTest {

    private final SignalScorer signalScorer = new SignalScorer();

    @Test
    void scoresGuideLikeMessageHigherThanAcknowledgement() {
        MessageEntity guide = new MessageEntity();
        guide.setText("""
            Гайд по настройке агента
            1. Откройте проект
            2. Добавьте prompt
            3. Проверьте результат
            https://example.com/docs
            """);
        guide.setIsBot(false);
        guide.setReplyCount(3);

        MessageEntity ack = new MessageEntity();
        ack.setText("спасибо");
        ack.setIsBot(false);

        SignalScore guideScore = signalScorer.score(guide);
        SignalScore ackScore = signalScorer.score(ack);

        assertThat(guideScore.score()).isGreaterThan(0.45);
        assertThat(ackScore.score()).isLessThan(0.2);
        assertThat(guideScore.score()).isGreaterThan(ackScore.score());
    }

    @Test
    void scoresShortSourceSeekingQuestionAboveThreshold() {
        MessageEntity question = new MessageEntity();
        question.setText("Кто нашел где акки gpt покупать через китайцев, сайт есть у кого?");
        question.setIsBot(false);

        SignalScore questionScore = signalScorer.score(question);

        assertThat(questionScore.score()).isGreaterThanOrEqualTo(0.3);
    }
}
