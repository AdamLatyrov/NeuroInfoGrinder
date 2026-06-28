package com.larbcorp.neuroinfogrinder2.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageIngestedSseTest {
    @Test
    void publishesMessageIngestedPayloadForFrontendInvalidation() {
        PipelineEventBroadcaster broadcaster = new PipelineEventBroadcaster(new ObjectMapper());

        broadcaster.messageIngested(10, 20, 100, 200);

        assertThat(broadcaster.publishedEvents()).hasSize(1);
        assertThat(broadcaster.publishedEvents().get(0).path("type").asText()).isEqualTo("MESSAGE_INGESTED");
        assertThat(broadcaster.publishedEvents().get(0).path("messageId").asLong()).isEqualTo(10);
        assertThat(broadcaster.publishedEvents().get(0).path("groupId").asLong()).isEqualTo(20);
    }
}
