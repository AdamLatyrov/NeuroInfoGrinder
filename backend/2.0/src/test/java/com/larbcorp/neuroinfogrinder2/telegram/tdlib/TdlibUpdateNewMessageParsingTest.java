package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.drinkless.tdlib.TdApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TdlibUpdateNewMessageParsingTest {
    private final TdlibMessageMapper mapper = new TdlibMessageMapper(new ObjectMapper());

    @Test
    void mapsTdlibMessageTextToLocalIngestInput() {
        TdApi.Message message = new TdApi.Message();
        message.id = 200;
        message.chatId = 100;
        message.senderId = new TdApi.MessageSenderUser(42);
        message.date = 1_782_128_800;
        message.content = new TdApi.MessageText(
                new TdApi.FormattedText("hello https://example.com", new TdApi.TextEntity[]{
                        new TdApi.TextEntity(6, 19, new TdApi.TextEntityTypeUrl())
                }),
                null,
                null
        );

        var input = mapper.toLocalMessageInput(1, message, "Alice", "alice", false);

        assertThat(input.accountId()).isEqualTo(1);
        assertThat(input.telegramChatId()).isEqualTo(100);
        assertThat(input.telegramMessageId()).isEqualTo(200);
        assertThat(input.text()).isEqualTo("hello https://example.com");
        assertThat(input.entities()).hasSize(1);
    }
}
