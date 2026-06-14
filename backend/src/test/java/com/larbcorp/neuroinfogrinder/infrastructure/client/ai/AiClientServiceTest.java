package com.larbcorp.neuroinfogrinder.infrastructure.client.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiClientServiceTest {

    private final AiClientService service = new AiClientService(new ObjectMapper());

    @Test
    void mockClassifierRejectsChattyNoiseAndKeepsStructuredAdvice() {
        AiCompletionResponse noisy = service.complete(new AiCompletionRequest(
            "mock://local",
            "test",
            "mock-guide-model",
            List.of(new AiMessage("user", "lol this model is trash for coding, anyone already tried it?")),
            0.0,
            128
        ));

        AiCompletionResponse useful = service.complete(new AiCompletionRequest(
            "mock://local",
            "test",
            "mock-guide-model",
            List.of(new AiMessage("user", """
                Context bundle to classify:

                - internalMessageId: 10
                  sender: Andrey
                  text: How can I connect a cheap model through API?

                - internalMessageId: 11
                  sender: Karma
                  text: 1. Register with a provider. 2. Pick Gemini Flash or DeepSeek through a router. 3. Connect endpoint https://openrouter.ai/api/v1 and verify with curl.
                """)),
            0.0,
            128
        ));

        assertThat(noisy.content()).contains("\"matched\": false");
        assertThat(useful.content()).contains("\"matched\": true");
        assertThat(useful.content()).contains("\"labels\":");
        assertThat(useful.content()).contains("\"guide_candidate\":");
    }

    @Test
    void mockClassifierRejectsPromoChatterButAcceptsStructuredReleaseSummary() {
        AiCompletionResponse promo = service.complete(new AiCompletionRequest(
            "mock://local",
            "test",
            "mock-guide-model",
            List.of(new AiMessage("user", """
                Free credits for Claude API, use my referral link and subscribe to the channel.
                https://t.me/somechannel
                """)),
            0.0,
            128
        ));

        AiCompletionResponse release = service.complete(new AiCompletionRequest(
            "mock://local",
            "test",
            "mock-guide-model",
            List.of(new AiMessage("user", """
                Context bundle to classify:

                - internalMessageId: 20
                  sender: Nova
                  text: New model release notes. Pricing dropped to $0.20 per 1M input tokens. Context window is now 1M tokens. API endpoint remains OpenAI-compatible. Benchmark quality improved on coding and agent tasks. Docs: https://example.com/release
                """)),
            0.0,
            128
        ));

        assertThat(promo.content()).contains("\"matched\": false");
        assertThat(release.content()).contains("\"matched\": true");
        assertThat(release.content()).contains("\"guide_candidate\":");
    }

    @Test
    void mockClassifierIgnoresSystemPromptBoilerplate() {
        AiCompletionResponse response = service.complete(new AiCompletionRequest(
            "mock://local",
            "test",
            "mock-guide-model",
            List.of(
                new AiMessage("system", """
                    Return JSON with score, matched, labels, guide_candidate, evidence_message_ids, reasoning.
                    Mention guide, benchmark, pricing, release, action items.
                    """),
                new AiMessage("user", """
                    Context bundle to classify:

                    - internalMessageId: 30
                      sender: User
                      text: ok who already tried it?
                    """)
            ),
            0.0,
            128
        ));

        assertThat(response.content()).contains("\"matched\": false");
    }

    @Test
    void mockClassifierKeepsAccessDemandAndPaymentSignals() {
        AiCompletionResponse accessDemand = service.complete(new AiCompletionRequest(
            "mock://local",
            "test",
            "mock-guide-model",
            List.of(new AiMessage("user", """
                Context bundle to classify:

                - internalMessageId: 40
                  sender: User
                  text: никто не знает где можно безлимитный апи клауда купить?
                """)),
            0.0,
            128
        ));

        AiCompletionResponse paymentWorkaround = service.complete(new AiCompletionRequest(
            "mock://local",
            "test",
            "mock-guide-model",
            List.of(new AiMessage("user", """
                Context bundle to classify:

                - internalMessageId: 41
                  sender: User
                  text: Сторонние китайские сервисы пополнения берут от 148 до 238 юаней в месяц
                """)),
            0.0,
            128
        ));

        assertThat(accessDemand.content()).contains("\"matched\": true");
        assertThat(accessDemand.content()).contains("\"labels\":");
        assertThat(accessDemand.content()).contains("\"evidence_message_ids\": [40]");
        assertThat(accessDemand.content()).contains("accessDemand=true");

        assertThat(paymentWorkaround.content()).contains("\"matched\": true");
        assertThat(paymentWorkaround.content()).contains("\"labels\":");
        assertThat(paymentWorkaround.content()).contains("\"evidence_message_ids\": [41]");
        assertThat(paymentWorkaround.content()).contains("paymentWorkaround=true");
    }
}
