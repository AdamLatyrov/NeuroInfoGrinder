package com.larbcorp.neuroinfogrinder.domain.messages;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTextFormatterTest {

    @Test
    void restoresTelegramTextUrlEntityAsMarkdownLink() {
        String text = "Open here.";
        String entitiesJson = """
            [{"type":"textUrl","offset":5,"length":4,"url":"https://example.com","text":"here"}]
            """;

        assertThat(MessageTextFormatter.withMarkdownLinks(text, entitiesJson))
            .isEqualTo("Open [here](https://example.com).");
    }

    @Test
    void leavesAlreadyFormattedMarkdownTextUntouchedWhenEntityOffsetsNoLongerMatch() {
        String text = "Open [here](https://example.com).";
        String entitiesJson = """
            [{"type":"textUrl","offset":5,"length":4,"url":"https://example.com","text":"here"}]
            """;

        assertThat(MessageTextFormatter.withMarkdownLinks(text, entitiesJson))
            .isEqualTo(text);
    }

    @Test
    void exposesTextEntityLinksForMaterialLinkLists() {
        String entitiesJson = """
            [{"type":"textUrl","offset":5,"length":4,"url":"example.com/path","text":"here"}]
            """;

        assertThat(MessageTextFormatter.links("Open here.", entitiesJson))
            .containsExactly("https://example.com/path");
    }
}
