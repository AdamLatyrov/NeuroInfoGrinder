package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.drinkless.tdlib.TdApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TdlibMediaMetadataMappingTest {
    private final TdlibMessageMapper mapper = new TdlibMessageMapper(new ObjectMapper());

    @Test
    void mapsDocumentFileMetadataForRawMediaPersistence() {
        TdApi.File file = new TdApi.File(
                12,
                345,
                400,
                null,
                new TdApi.RemoteFile("remote-file-id", "unique-file-id", false, true, 345)
        );
        TdApi.Message message = new TdApi.Message();
        message.id = 200;
        message.chatId = 100;
        message.senderId = new TdApi.MessageSenderUser(42);
        message.date = 1_782_128_800;
        message.content = new TdApi.MessageDocument(
                new TdApi.Document("report.pdf", "application/pdf", null, null, file),
                new TdApi.FormattedText("caption", null)
        );

        var input = mapper.toLocalMessageInput(1, message, "Alice", "alice", false);

        assertThat(input.media().path("file_id").asText()).isEqualTo("remote-file-id");
        assertThat(input.media().path("file_unique_id").asText()).isEqualTo("unique-file-id");
        assertThat(input.media().path("file_size").asLong()).isEqualTo(345);
        assertThat(input.media().path("file").path("id").asInt()).isEqualTo(12);
    }
}
