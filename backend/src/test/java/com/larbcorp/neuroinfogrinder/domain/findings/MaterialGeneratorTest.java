package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialGeneratorTest {

    private final MaterialGenerator generator = new MaterialGenerator();

    @Test
    void bypassMaterialUsesRiskFormatAndRedactsOperationalDetails() {
        ContentRoutingDecision decision = decision(
            "Полезное: Риски обхода ограничений Claude Code",
            "Риски обхода ограничений Claude Code: Список полезных сервисов: "
                + "Универсальный генератор: https://example-generator.test/check "
                + "Генератор банковских карт: https://card-generator.test "
                + "Создание временной почты: https://temp-mail.test "
                + "Сервис для SMS-активаций: https://sms-activation.test "
                + "Регистрация через GitLab и Google."
        );

        GuideContent content = generator.generate(
            decision,
            List.of(message(10L, "Claude Code bypass: генератор банковских карт, временная почта и SMS-активации"))
        );

        assertThat(content.title()).startsWith("Риск:");
        assertThat(content.title()).doesNotContain("Полезное:");
        assertThat(content.contentMarkdown()).contains("## Коротко");
        assertThat(content.contentMarkdown()).contains("## Почему важно");
        assertThat(content.contentMarkdown()).contains("## Что проверить");
        assertThat(content.contentMarkdown()).contains("## Источники");
        assertThat(content.contentMarkdown()).doesNotContain("Список полезных сервисов");
        assertThat(content.contentMarkdown()).doesNotContain("example-generator.test");
        assertThat(content.contentMarkdown()).doesNotContain("card-generator.test");
        assertThat(content.contentMarkdown()).doesNotContain("temp-mail.test");
        assertThat(content.contentMarkdown()).doesNotContain("sms-activation.test");
        assertThat(content.contentMarkdown()).doesNotContain("Генератор банковских карт");
        assertThat(content.tags()).contains("risk", "bypass");
    }

    @Test
    void usefulMaterialSplitsLongSummaryIntoReadableSections() {
        ContentRoutingDecision decision = new ContentRoutingDecision(
            ContentType.USEFUL_INFO,
            "SHORT_INSIGHT",
            "CRM onboarding feedback",
            "Пользователи теряются на первом экране CRM. Команда обсуждает онбординг и сбор обратной связи.",
            "Полезное: CRM onboarding feedback",
            "Пользователи теряются на первом экране CRM. Команда обсуждает онбординг и сбор обратной связи. Нужно проверить первые шаги и точки выхода.",
            "crm_onboarding",
            "Ключевой вывод из обсуждения",
            true,
            false,
            0.82,
            75,
            45,
            40,
            60,
            10,
            10,
            "NORMAL",
            "MATERIAL",
            "test",
            List.of(),
            List.of(),
            null,
            null
        );

        GuideContent content = generator.generate(decision, List.of(message(11L, "В CRM все теряются на первом экране")));

        assertThat(content.contentMarkdown()).contains("## Коротко");
        assertThat(content.contentMarkdown()).contains("## Детали");
        assertThat(content.contentMarkdown()).contains("- Команда обсуждает онбординг и сбор обратной связи.");
        assertThat(content.contentMarkdown()).doesNotContain("## Главный вывод\nПользователи теряются на первом экране CRM. Команда обсуждает онбординг и сбор обратной связи. Нужно проверить первые шаги и точки выхода.");
    }

    @Test
    void usefulMaterialPreservesSourceLinksInDedicatedSection() {
        ContentRoutingDecision decision = new ContentRoutingDecision(
            ContentType.USEFUL_INFO,
            "SHORT_INSIGHT",
            "RuFlow voice-to-code",
            "Roma адаптировал голосовой ввод RuFlow под Windows для работы с Codex API.",
            "Полезное: RuFlow voice-to-code",
            "Roma адаптировал голосовой ввод RuFlow под Windows для работы с Codex API.",
            "ruflow_voice_to_code",
            "Ключевой вывод из обсуждения",
            true,
            false,
            0.82,
            75,
            45,
            40,
            60,
            10,
            10,
            "NORMAL",
            "MATERIAL",
            "test",
            List.of(),
            List.of(),
            null,
            null
        );
        MessageEntity source = message(
            12L,
            "Нашёл RuFlow для голосового ввода под Windows. Репо: https://github.com/example/ruflow и whispertocode: https://github.com/example/whispertocode"
        );

        GuideContent content = generator.generate(decision, List.of(source));

        assertThat(content.contentMarkdown()).contains("## Ссылки");
        assertThat(content.contentMarkdown()).contains("https://github.com/example/ruflow");
        assertThat(content.contentMarkdown()).contains("https://github.com/example/whispertocode");
        assertThat(content.contentMarkdown()).doesNotContain("[ссылка]");
    }

    private ContentRoutingDecision decision(String title, String summary) {
        return new ContentRoutingDecision(
            ContentType.USEFUL_INFO,
            "BYPASS",
            "Риски обхода ограничений Claude Code",
            summary,
            title,
            summary,
            "claude_code_bypass",
            "Ключевой вывод из обсуждения",
            true,
            false,
            0.82,
            80,
            35,
            40,
            70,
            90,
            10,
            "BYPASS",
            "MATERIAL",
            "test",
            List.of(),
            List.of(),
            null,
            null
        );
    }

    private MessageEntity message(Long id, String text) {
        MessageEntity message = new MessageEntity();
        message.setId(id);
        message.setText(text);
        message.setMessageDate(Instant.parse("2026-06-21T10:00:00Z"));
        return message;
    }
}
