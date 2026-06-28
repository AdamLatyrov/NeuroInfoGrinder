package com.larbcorp.neuroinfogrinder2.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RawMessagesReplayService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DatasetReplayService datasetReplayService;

    public RawMessagesReplayService(JdbcTemplate jdbc, ObjectMapper objectMapper, DatasetReplayService datasetReplayService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.datasetReplayService = datasetReplayService;
    }

    @Transactional
    public DatasetReplayService.ReplayRunDto run(RawMessagesReplayRequest request) {
        int limit = request.limit() == null ? 500 : Math.max(1, Math.min(request.limit(), 500));
        String runName = request.runName() == null || request.runName().isBlank()
                ? "real telegram snapshot " + limit
                : request.runName();
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("mode", request.mode() == null ? "RAW_MESSAGES_REPLAY" : request.mode());
        metadata.put("accountId", request.accountId());
        metadata.put("limit", limit);
        metadata.put("autoPublish", Boolean.TRUE.equals(request.autoPublish()));
        long datasetId = insertDataset(runName, metadata);
        int inserted = snapshotRawMessages(datasetId, request, limit);
        jdbc.update("UPDATE datasets SET message_count = ? WHERE id = ?", inserted, datasetId);
        return datasetReplayService.replay(new DatasetReplayService.ReplayRequest(datasetId, runName));
    }

    private long insertDataset(String runName, ObjectNode metadata) {
        return jdbc.queryForObject("""
                INSERT INTO datasets (name, source, source_kind, description, created_by, metadata_json)
                VALUES (?, 'RAW_MESSAGES', 'RAW_MESSAGES_REPLAY', 'Snapshot from production raw_messages for real Telegram replay', 'operator', ?::jsonb)
                RETURNING id
                """, Long.class, runName, json(metadata));
    }

    private int snapshotRawMessages(long datasetId, RawMessagesReplayRequest request, int limit) {
        String chatFilter = request.chatIds() == null || request.chatIds().isEmpty() ? "" : " AND m.telegram_chat_id = ANY (?::bigint[])";
        String sql = """
                INSERT INTO dataset_messages (
                    dataset_id, source_message_id, account_id, telegram_chat_id, telegram_message_id, telegram_topic_id,
                    chat_title, sender_id, sender_name, sender_username, message_date, reply_to_message_id,
                    text, caption, content_type, raw_json, entities_json, media_json
                )
                SELECT ?,
                       m.account_id || ':' || m.telegram_chat_id || ':' || m.telegram_message_id,
                       m.account_id,
                       m.telegram_chat_id,
                       m.telegram_message_id,
                       m.telegram_topic_id,
                       c.title,
                       m.sender_id,
                       m.sender_name,
                       m.sender_username,
                       m.message_date,
                       m.reply_to_message_id,
                       m.text,
                       m.caption,
                       m.content_type,
                       m.raw_json,
                       '[]'::jsonb,
                       COALESCE((SELECT jsonb_agg(mm.raw_media_json) FROM raw_message_media mm WHERE mm.message_id = m.id), '[]'::jsonb)
                FROM raw_messages m
                JOIN telegram_chats c ON c.account_id = m.account_id AND c.telegram_chat_id = m.telegram_chat_id
                WHERE m.account_id = ?
                """ + chatFilter + """
                ORDER BY m.message_date DESC NULLS LAST, m.id DESC
                LIMIT ?
                """;
        if (chatFilter.isBlank()) {
            return jdbc.update(sql, datasetId, request.accountId(), limit);
        }
        Long[] chatIds = request.chatIds().toArray(Long[]::new);
        return jdbc.update(sql, datasetId, request.accountId(), chatIds, limit);
    }

    private String json(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize raw replay metadata", error);
        }
    }

    public record RawMessagesReplayRequest(
            String mode,
            long accountId,
            List<Long> chatIds,
            String from,
            String to,
            Integer limit,
            String runName,
            Integer maxProviderCalls,
            Double maxEstimatedCostUsd,
            Boolean autoPublish
    ) {
    }
}
