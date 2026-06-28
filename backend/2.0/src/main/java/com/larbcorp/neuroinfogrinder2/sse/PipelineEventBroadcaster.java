package com.larbcorp.neuroinfogrinder2.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class PipelineEventBroadcaster {
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final List<ObjectNode> publishedEvents = new CopyOnWriteArrayList<>();

    public PipelineEventBroadcaster(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    @Autowired
    public PipelineEventBroadcaster(ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        recordEvent("SSE_CLIENT_CONNECTED", null, null, null, null);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"connected\":true}"));
        } catch (IOException error) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void messageIngested(long messageId, long groupId, long telegramChatId, long telegramMessageId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "MESSAGE_INGESTED");
        payload.put("messageId", messageId);
        payload.put("groupId", groupId);
        payload.put("telegramChatId", telegramChatId);
        payload.put("telegramMessageId", telegramMessageId);
        publishPipeline(payload);
    }

    public void publishPipeline(ObjectNode payload) {
        publishedEvents.add(payload.deepCopy());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("pipeline").data(payload.toString()));
                recordEvent("SSE_DELIVERED", null, nullableLong(payload, "telegramChatId"), nullableLong(payload, "telegramMessageId"), null);
            } catch (IOException error) {
                emitters.remove(emitter);
            }
        }
    }

    public List<ObjectNode> publishedEvents() {
        return List.copyOf(publishedEvents);
    }

    private Long nullableLong(ObjectNode payload, String field) {
        return payload.has(field) && payload.get(field).canConvertToLong() ? payload.get(field).asLong() : null;
    }

    private void recordEvent(String eventType, Long accountId, Long telegramChatId, Long telegramMessageId, Long durationMs) {
        if (jdbc == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO telegram_runtime_events (event_type, account_id, telegram_chat_id, telegram_message_id, duration_ms)
                VALUES (?, ?, ?, ?, ?)
                """, eventType, accountId, telegramChatId, telegramMessageId, durationMs);
    }
}
