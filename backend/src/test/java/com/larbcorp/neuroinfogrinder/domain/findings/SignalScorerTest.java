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

    @Test
    void keepsAiAccessDemandMessagesAboveSkipThreshold() {
        assertUsefulLead("где покупать гифты клода по норм цене", "AI_ACCESS_DEMAND", "PROVIDER_MENTION");
        assertUsefulLead("никто не знает где можно безлимитный апи клауда купить?", "AI_ACCESS_DEMAND", "PAIN_LIMITS");
        assertUsefulLead("подскажите, где сейчас дешевле всего купить чат гпт плюс?", "AI_ACCESS_DEMAND", "PROVIDER_MENTION");
        assertUsefulLead("Вот эту попробуй, она пока что бесплатная https://openrouter.ai/nex-agi/nex-n2-pro:free",
            "PROVIDER_MENTION", "OFFER_OR_SPAM");
        assertUsefulLead("На фанпее купи ак за 300 на год", "PAYMENT_WORKAROUND", "OFFER_OR_SPAM");
        assertUsefulLead("Сторонние китайские сервисы пополнения берут от 148 до 238 юаней в месяц",
            "PAYMENT_WORKAROUND");
        assertUsefulLead("я очень быстро эти лимиты убиваю", "PAIN_LIMITS");
    }

    @Test
    void keepsShortNoiseLowAndMarkedNotUseful() {
        assertNotUseful("Всем");
        assertNotUseful("Да");
        assertNotUseful("ок");
        assertNotUseful("Сайт");
    }

    @Test
    void keepsContextOnlyChatterOutOfUsefulSignals() {
        MessageEntity message = new MessageEntity();
        message.setText("гигакодер спит");
        message.setIsBot(false);
        message.setReplyToMessageId(11L);
        message.setTopicId(7L);

        SignalScore score = signalScorer.score(message);

        assertThat(score.score()).isLessThan(0.3);
        assertThat(score.labels()).isEmpty();
        assertThat(score.matchedSignals()).isEmpty();
        assertThat(score.classificationReason()).isEqualTo("No AI access/payment/provider demand signals");
    }

    private void assertUsefulLead(String text, String... expectedLabels) {
        MessageEntity message = new MessageEntity();
        message.setText(text);
        message.setIsBot(false);

        SignalScore score = signalScorer.score(message);

        assertThat(score.score()).isGreaterThanOrEqualTo(0.3);
        assertThat(score.classificationReason()).isNotBlank();
        assertThat(score.matchedSignals()).isNotEmpty();
        assertThat(score.labels()).contains(expectedLabels);
        assertThat(score.labels()).doesNotContain("NOT_USEFUL");
    }

    private void assertNotUseful(String text) {
        MessageEntity message = new MessageEntity();
        message.setText(text);
        message.setIsBot(false);

        SignalScore score = signalScorer.score(message);

        assertThat(score.score()).isLessThan(0.3);
        assertThat(score.labels()).contains("NOT_USEFUL");
    }
}
