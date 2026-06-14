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
            List.of(new AiMessage("user",
                """
                Message chain to classify:

                [Andrey]: How can I connect a cheap model through API?
                [Karma]: 1. Register with a provider.
                2. Pick Gemini Flash or DeepSeek through a router.
                3. Connect endpoint https://openrouter.ai/api/v1 and verify with curl.
                """)),
            0.0,
            128
        ));

        assertThat(noisy.content()).contains("\"matched\": false");
        assertThat(useful.content()).contains("\"matched\": true");
    }

    @Test
    void mockClassifierRejectsPromoChatterButAcceptsStructuredReleaseSummary() {
        AiCompletionResponse promo = service.complete(new AiCompletionRequest(
            "mock://local",
            "test",
            "mock-guide-model",
            List.of(new AiMessage("user",
                """
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
            List.of(new AiMessage("user",
                """
                Message chain to classify:

                [Nova]: New model release notes.
                - Pricing dropped to $0.20 per 1M input tokens.
                - Context window is now 1M tokens.
                - API endpoint remains OpenAI-compatible.
                - Benchmark quality improved on coding and agent tasks.
                Docs: https://example.com/release
                """)),
            0.0,
            128
        ));

        assertThat(promo.content()).contains("\"matched\": false");
        assertThat(release.content()).contains("\"matched\": true");
    }

    @Test
    void mockClassifierIgnoresSystemPromptBoilerplate() {
        AiCompletionResponse response = service.complete(new AiCompletionRequest(
            "mock://local",
            "test",
            "mock-guide-model",
            List.of(
                new AiMessage("system", """
                    Return JSON with score, matched, reasoning.
                    Mention guide, benchmark, pricing, release, action items.
                    """),
                new AiMessage("user", """
                    Message chain to classify:

                    [User]: ok who already tried it?
                    """)
            ),
            0.0,
            128
        ));

        assertThat(response.content()).contains("\"matched\": false");
    }
}
