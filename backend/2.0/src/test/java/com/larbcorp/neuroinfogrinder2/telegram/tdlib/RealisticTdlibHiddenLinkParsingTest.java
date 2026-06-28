package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder2.links.LinkExtractor;
import org.drinkless.tdlib.TdApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RealisticTdlibHiddenLinkParsingTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TdlibMessageMapper mapper = new TdlibMessageMapper(objectMapper);
    private final LinkExtractor extractor = new LinkExtractor(objectMapper);

    @Test
    void preservesHiddenTextUrlFromTdlibFormattedText() {
        TdApi.Message message = new TdApi.Message();
        message.id = 201;
        message.chatId = 100;
        message.date = 1_782_128_860;
        message.content = new TdApi.MessageText(
                new TdApi.FormattedText("Нажми тут", new TdApi.TextEntity[]{
                        new TdApi.TextEntity(6, 3, new TdApi.TextEntityTypeTextUrl("https://example.com/hidden"))
                }),
                null,
                null
        );

        var input = mapper.toLocalMessageInput(1, message, "Alice", "alice", false);
        var links = extractor.extract(input.text(), objectMapper.valueToTree(input.entities()), "TEXT");

        assertThat(links).hasSize(1);
        assertThat(links.get(0).anchorText()).isEqualTo("тут");
        assertThat(links.get(0).url()).isEqualTo("https://example.com/hidden");
        assertThat(links.get(0).hidden()).isTrue();
    }
}
