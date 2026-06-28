package com.larbcorp.neuroinfogrinder2.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseMessageIngestedEventTest {
    @Test
    void eventPayloadMatchesFrontendInvalidationContract() {
        PipelineEventBroadcaster broadcaster = new PipelineEventBroadcaster(new ObjectMapper());

        broadcaster.messageIngested(10, 20, 100, 200);

        var event = broadcaster.publishedEvents().get(0);
        assertThat(event.path("type").asText()).isEqualTo("MESSAGE_INGESTED");
        assertThat(event.path("groupId").asLong()).isEqualTo(20);
        assertThat(event.path("messageId").asLong()).isEqualTo(10);
    }
}
