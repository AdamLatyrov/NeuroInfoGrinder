package com.larbcorp.neuroinfogrinder2.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.links.ExtractedLink;
import com.larbcorp.neuroinfogrinder2.links.LinkExtractor;
import com.larbcorp.neuroinfogrinder2.pipeline.AutoPipelineService;
import com.larbcorp.neuroinfogrinder2.sse.PipelineEventBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class TelegramIngestionService {
    private static final Logger log = LoggerFactory.getLogger(TelegramIngestionService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final LinkExtractor linkExtractor;
    private final PipelineEventBroadcaster eventBroadcaster;
    private final AutoPipelineService autoPipelineService;
    private final DuplicateUpdateDetector duplicateUpdateDetector = new DuplicateUpdateDetector();

    public TelegramIngestionService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            LinkExtractor linkExtractor,
            PipelineEventBroadcaster eventBroadcaster,
            AutoPipelineService autoPipelineService
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.linkExtractor = linkExtractor;
        this.eventBroadcaster = eventBroadcaster;
        this.autoPipelineService = autoPipelineService;
    }

    @Transactional
    public IngestResult ingestLocalMessage(LocalMessageInput input) {
        long accountId = input.accountId() == null ? 1L : input.accountId();
        long telegramChatId = required(input.telegramChatId(), "telegramChatId");
        long telegramMessageId = required(input.telegramMessageId(), "telegramMessageId");
        String contentType = input.contentType() == null || input.contentType().isBlank()
                ? defaultContentType(input)
                : input.contentType();

        ensureAccount(accountId);
        long groupId = ensureChat(accountId, telegramChatId);
        if (input.telegramTopicId() != null) {
            recordRuntimeEvent("TOPIC_ID_EXTRACTED", accountId, telegramChatId, telegramMessageId, null);
        }

        ObjectNode rawJson = rawMessageJson(input, contentType);
        ArrayNode textEntities = toArray(input.entities());
        ArrayNode captionEntities = toArray(input.captionEntities());
        List<ExtractedLink> links = new ArrayList<>();
        links.addAll(linkExtractor.extract(input.text(), textEntities, "TEXT"));
        links.addAll(linkExtractor.extract(input.caption(), captionEntities, "CAPTION"));

        boolean detectorNew = duplicateUpdateDetector.markIfNew(
                accountId,
                "updateNewMessage",
                telegramChatId,
                telegramMessageId
        );
        int inboxInserted = writeInbox(accountId, telegramChatId, telegramMessageId, rawJson);
        boolean duplicateUpdate = !detectorNew || inboxInserted == 0;

        if (duplicateUpdate) {
            incrementDuplicateCount(accountId);
            log.info("duplicate skipped accountId={} chatId={} messageId={}", accountId, telegramChatId, telegramMessageId);
        }

        long messageId = upsertRawMessage(input, accountId, telegramChatId, telegramMessageId, contentType, rawJson, !links.isEmpty());
        replaceMedia(messageId, input);
        replaceLinks(messageId, accountId, telegramChatId, telegramMessageId, links);
        updateChatAfterMessage(groupId, telegramMessageId, input.messageDate());
        updateHealthAfterMessage(accountId, telegramChatId);
        markInboxPersisted(accountId, telegramChatId, telegramMessageId);

        recordRuntimeEvent("UPDATE_STORED", accountId, telegramChatId, telegramMessageId, null);
        recordRuntimeEvent("RAW_MESSAGE_STORED", accountId, telegramChatId, telegramMessageId, null);
        recordRuntimeEvent("MESSAGE_LINKS_EXTRACTED", accountId, telegramChatId, telegramMessageId, links.size());
        recordRuntimeEvent("MESSAGE_INGESTED_EVENT_CREATED", accountId, telegramChatId, telegramMessageId, null);
        writePipelineEvent("TDLIB_UPDATE_RECEIVED", "tdlib_update_inbox", null, rawJson);
        writePipelineEvent("RAW_MESSAGE_PERSISTED", "raw_messages", messageId, eventPayload(messageId, groupId, telegramChatId, telegramMessageId));
        writePipelineEvent("LINKS_EXTRACTED", "raw_messages", messageId, linksPayload(messageId, links.size()));
        writePipelineEvent("MESSAGE_INGESTED", "raw_messages", messageId, eventPayload(messageId, groupId, telegramChatId, telegramMessageId));
        autoPipelineService.onRawMessageStored(messageId, accountId, telegramChatId, input.telegramTopicId());
        eventBroadcaster.messageIngested(messageId, groupId, telegramChatId, telegramMessageId);

        log.info("raw message persisted accountId={} chatId={} messageId={} links={}", accountId, telegramChatId, telegramMessageId, links.size());
        return new IngestResult(messageId, groupId, accountId, telegramChatId, telegramMessageId, links.size(), duplicateUpdate);
    }

    private void recordRuntimeEvent(String eventType, long accountId, long telegramChatId, long telegramMessageId, Integer count) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (count != null) {
            payload.put("count", count);
        }
        jdbc.update("""
                INSERT INTO telegram_runtime_events (event_type, account_id, telegram_chat_id, telegram_message_id, payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """, eventType, accountId, telegramChatId, telegramMessageId, json(payload));
    }

    @Transactional
    public IngestResult ingestTdlibUpdate(long accountId, JsonNode update) {
        JsonNode message = update.path("message");
        JsonNode content = message.path("content");
        String contentType = textValue(content, "@type", "unknown");
        String text = null;
        List<JsonNode> entities = List.of();
        String caption = null;
        List<JsonNode> captionEntities = List.of();

        if ("messageText".equals(contentType)) {
            JsonNode formattedText = content.path("text");
            text = textValue(formattedText, "text", null);
            entities = nodeList(formattedText.path("entities"));
        } else if (content.has("caption")) {
            JsonNode formattedCaption = content.path("caption");
            caption = textValue(formattedCaption, "text", null);
            captionEntities = nodeList(formattedCaption.path("entities"));
        }

        LocalMessageInput input = new LocalMessageInput(
                accountId,
                longValue(message, "chat_id"),
                longValue(message, "id"),
                nullableLong(message, "message_thread_id"),
                nullableSenderId(message.path("sender_id")),
                null,
                null,
                false,
                nullableLong(message.path("reply_to"), "message_id"),
                text,
                caption,
                tdlibDate(message.path("date")),
                tdlibDate(message.path("edit_date")),
                contentType,
                entities,
                captionEntities,
                content,
                update
        );

        return ingestLocalMessage(input);
    }

    private void ensureAccount(long accountId) {
        jdbc.update("""
                INSERT INTO telegram_accounts (id, name, status)
                VALUES (?, ?, 'CONNECTED')
                ON CONFLICT (id) DO UPDATE SET updated_at = now()
                """, accountId, "local account " + accountId);
        jdbc.update("""
                INSERT INTO telegram_account_health (account_id, status, updated_at)
                VALUES (?, 'CONNECTED', now())
                ON CONFLICT (account_id) DO UPDATE SET status = EXCLUDED.status, updated_at = now()
                """, accountId);
    }

    private long ensureChat(long accountId, long telegramChatId) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO telegram_chats (account_id, telegram_chat_id, title, type, is_forum, is_enabled)
                VALUES (?, ?, ?, 'GROUP', false, true)
                ON CONFLICT (account_id, telegram_chat_id)
                DO UPDATE SET updated_at = now()
                RETURNING id
                """, Long.class, accountId, telegramChatId, "Local chat " + telegramChatId));
    }

    private void ensureTopic(long accountId, long telegramChatId, long telegramTopicId) {
        jdbc.update("""
                INSERT INTO telegram_topics (account_id, telegram_chat_id, telegram_topic_id, message_thread_id, title, title_source, title_confidence, sync_state, title_updated_at)
                VALUES (?, ?, ?, ?, 'Тема без названия', 'PLACEHOLDER_UNKNOWN', 0, 'NEEDS_TOPIC_SYNC', now())
                ON CONFLICT (account_id, telegram_chat_id, telegram_topic_id)
                DO UPDATE SET updated_at = now()
                """, accountId, telegramChatId, telegramTopicId, telegramTopicId);
        recordRuntimeEvent("TOPIC_PLACEHOLDER_CREATED", accountId, telegramChatId, telegramTopicId, null);
    }

    private int writeInbox(long accountId, long telegramChatId, long telegramMessageId, JsonNode rawJson) {
        return jdbc.update("""
                INSERT INTO tdlib_update_inbox (
                    account_id,
                    update_type,
                    telegram_chat_id,
                    telegram_message_id,
                    raw_update_json,
                    received_at,
                    processing_status
                )
                VALUES (?, 'updateNewMessage', ?, ?, ?::jsonb, now(), 'PENDING')
                ON CONFLICT (account_id, update_type, telegram_chat_id, telegram_message_id) DO NOTHING
                """, accountId, telegramChatId, telegramMessageId, json(rawJson));
    }

    private long upsertRawMessage(
            LocalMessageInput input,
            long accountId,
            long telegramChatId,
            long telegramMessageId,
            String contentType,
            JsonNode rawJson,
            boolean hasLinks
    ) {
        Timestamp messageDate = timestamp(input.messageDate());
        Timestamp editDate = timestamp(input.editDate());
        boolean hasText = notBlank(input.text()) || notBlank(input.caption());
        boolean hasMedia = input.media() != null && !input.media().isNull() && !input.media().isMissingNode();

        return Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO raw_messages (
                    account_id,
                    identity_id,
                    telegram_chat_id,
                    telegram_message_id,
                    telegram_topic_id,
                    forum_topic_id,
                    message_thread_id,
                    topic_title,
                    sender_id,
                    sender_name,
                    sender_username,
                    sender_is_bot,
                    message_date,
                    edit_date,
                    reply_to_message_id,
                    text,
                    caption,
                    content_type,
                    has_text,
                    has_media,
                    has_links,
                    raw_json,
                    ingested_at,
                    updated_at
                )
                VALUES (?, (SELECT identity_id FROM telegram_accounts WHERE id = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
                ON CONFLICT (account_id, telegram_chat_id, telegram_message_id)
                DO UPDATE SET
                    identity_id = COALESCE(raw_messages.identity_id, EXCLUDED.identity_id),
                    telegram_topic_id = EXCLUDED.telegram_topic_id,
                    forum_topic_id = EXCLUDED.forum_topic_id,
                    message_thread_id = EXCLUDED.message_thread_id,
                    topic_title = EXCLUDED.topic_title,
                    sender_id = EXCLUDED.sender_id,
                    sender_name = EXCLUDED.sender_name,
                    sender_username = EXCLUDED.sender_username,
                    sender_is_bot = EXCLUDED.sender_is_bot,
                    message_date = EXCLUDED.message_date,
                    edit_date = EXCLUDED.edit_date,
                    reply_to_message_id = EXCLUDED.reply_to_message_id,
                    text = EXCLUDED.text,
                    caption = EXCLUDED.caption,
                    content_type = EXCLUDED.content_type,
                    has_text = EXCLUDED.has_text,
                    has_media = EXCLUDED.has_media,
                    has_links = EXCLUDED.has_links,
                    raw_json = EXCLUDED.raw_json,
                    updated_at = now()
                RETURNING id
                """, Long.class,
                accountId,
                accountId,
                telegramChatId,
                telegramMessageId,
                input.telegramTopicId(),
                input.telegramTopicId(),
                input.telegramTopicId(),
                topicTitle(accountId, telegramChatId, input.telegramTopicId()),
                input.senderId(),
                input.senderName(),
                input.senderUsername(),
                Boolean.TRUE.equals(input.senderIsBot()),
                messageDate,
                editDate,
                input.replyToMessageId(),
                input.text(),
                input.caption(),
                contentType,
                hasText,
                hasMedia,
                hasLinks,
                json(rawJson)
        ));
    }

    private String topicTitle(long accountId, long telegramChatId, Long topicId) {
        if (topicId == null) {
            return null;
        }
        String title = jdbc.query("""
                SELECT title
                FROM telegram_topics
                WHERE account_id = ?
                  AND telegram_chat_id = ?
                  AND (telegram_topic_id = ? OR message_thread_id = ?)
                  AND title_source <> 'PLACEHOLDER_UNKNOWN'
                  AND title <> 'Тема без названия'
                  AND title NOT LIKE 'Topic %'
                ORDER BY CASE WHEN title_source = 'PLACEHOLDER_UNKNOWN' THEN 2 ELSE 1 END, updated_at DESC
                LIMIT 1
                """, rs -> rs.next() ? rs.getString("title") : null, accountId, telegramChatId, topicId, topicId);
        if (title != null) {
            recordRuntimeEvent("TOPIC_TITLE_RESOLVED", accountId, telegramChatId, topicId, null);
        }
        return title;
    }

    private void replaceMedia(long messageId, LocalMessageInput input) {
        jdbc.update("DELETE FROM raw_message_media WHERE message_id = ?", messageId);
        JsonNode media = input.media();
        if (media == null || media.isNull() || media.isMissingNode()) {
            return;
        }

        jdbc.update("""
                INSERT INTO raw_message_media (
                    message_id,
                    media_type,
                    file_id,
                    file_unique_id,
                    mime_type,
                    file_name,
                    file_size,
                    caption,
                    raw_media_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                messageId,
                textValue(media, "@type", "media"),
                mediaFileId(media),
                mediaFileUniqueId(media),
                textValue(media, "mime_type", null),
                textValue(media, "file_name", null),
                mediaFileSize(media),
                input.caption(),
                json(media)
        );
    }

    private String mediaFileId(JsonNode media) {
        String topLevel = textValue(media, "file_id", null);
        if (topLevel != null) {
            return topLevel;
        }
        String remoteId = textValue(media.path("file").path("remote"), "id", null);
        if (remoteId != null) {
            return remoteId;
        }
        JsonNode nestedId = media.path("file").path("id");
        if (nestedId.isNumber() || nestedId.isTextual()) {
            return nestedId.asText();
        }
        return null;
    }

    private String mediaFileUniqueId(JsonNode media) {
        String topLevel = textValue(media, "file_unique_id", null);
        if (topLevel != null) {
            return topLevel;
        }
        String snakeCase = textValue(media.path("file").path("remote"), "unique_id", null);
        if (snakeCase != null) {
            return snakeCase;
        }
        return textValue(media.path("file").path("remote"), "uniqueId", null);
    }

    private Long mediaFileSize(JsonNode media) {
        Long topLevel = nullableLong(media, "file_size");
        if (topLevel != null) {
            return topLevel;
        }
        Long nested = nullableLong(media.path("file"), "size");
        if (nested != null) {
            return nested;
        }
        return nullableLong(media.path("file"), "expected_size");
    }

    private void replaceLinks(long messageId, long accountId, long telegramChatId, long telegramMessageId, List<ExtractedLink> links) {
        jdbc.update("DELETE FROM message_links WHERE message_id = ?", messageId);
        for (ExtractedLink link : links) {
            jdbc.update("""
                    INSERT INTO message_links (
                        message_id,
                        account_id,
                        telegram_chat_id,
                        telegram_message_id,
                        url,
                        normalized_url,
                        domain,
                        anchor_text,
                        source,
                        entity_type,
                        offset_start,
                        offset_end,
                        is_hidden,
                        is_visible_url,
                        is_telegram_link,
                        is_referral_like,
                        raw_entity_json
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    """,
                    messageId,
                    accountId,
                    telegramChatId,
                    telegramMessageId,
                    link.url(),
                    link.normalizedUrl(),
                    link.domain(),
                    link.anchorText(),
                    link.source(),
                    link.entityType(),
                    link.offsetStart(),
                    link.offsetEnd(),
                    link.hidden(),
                    link.visibleUrl(),
                    link.telegramLink(),
                    link.referralLike(),
                    json(link.rawEntityJson())
            );
        }
        jdbc.update("UPDATE raw_messages SET has_links = ?, updated_at = now() WHERE id = ?", !links.isEmpty(), messageId);
    }

    private void updateChatAfterMessage(long groupId, long telegramMessageId, String messageDate) {
        jdbc.update("""
                UPDATE telegram_chats
                SET last_message_id = ?,
                    last_message_date = COALESCE(?, last_message_date),
                    updated_at = now()
                WHERE id = ?
                """, telegramMessageId, timestamp(messageDate), groupId);
    }

    private void updateHealthAfterMessage(long accountId, long telegramChatId) {
        jdbc.update("""
                INSERT INTO telegram_account_health (
                    account_id,
                    status,
                    last_update_received_at,
                    last_message_persisted_at,
                    inbox_pending_count,
                    updated_at
                )
                VALUES (?, 'CONNECTED', now(), now(), 0, now())
                ON CONFLICT (account_id)
                DO UPDATE SET
                    status = EXCLUDED.status,
                    last_update_received_at = now(),
                    last_message_persisted_at = now(),
                    inbox_pending_count = (
                        SELECT count(*) FROM tdlib_update_inbox
                        WHERE account_id = ? AND processing_status = 'PENDING'
                    ),
                    updated_at = now()
                """, accountId, accountId);
        jdbc.update("""
                INSERT INTO telegram_chat_health (
                    account_id,
                    telegram_chat_id,
                    last_update_received_at,
                    last_message_persisted_at,
                    backfill_status,
                    lag_seconds,
                    message_count_24h,
                    updated_at
                )
                VALUES (?, ?, now(), now(), 'IDLE', 0, 1, now())
                ON CONFLICT (account_id, telegram_chat_id)
                DO UPDATE SET
                    last_update_received_at = now(),
                    last_message_persisted_at = now(),
                    lag_seconds = 0,
                    message_count_24h = (
                        SELECT count(*) FROM raw_messages
                        WHERE account_id = ? AND telegram_chat_id = ? AND message_date >= now() - interval '24 hours'
                    ),
                    updated_at = now()
                """, accountId, telegramChatId, accountId, telegramChatId);
    }

    private void markInboxPersisted(long accountId, long telegramChatId, long telegramMessageId) {
        jdbc.update("""
                UPDATE tdlib_update_inbox
                SET persisted_at = now(), processing_status = 'PERSISTED', error = null
                WHERE account_id = ?
                  AND update_type = 'updateNewMessage'
                  AND telegram_chat_id = ?
                  AND telegram_message_id = ?
                """, accountId, telegramChatId, telegramMessageId);
    }

    private void incrementDuplicateCount(long accountId) {
        jdbc.update("""
                INSERT INTO telegram_account_health (account_id, duplicate_update_count, updated_at)
                VALUES (?, 1, now())
                ON CONFLICT (account_id)
                DO UPDATE SET duplicate_update_count = telegram_account_health.duplicate_update_count + 1, updated_at = now()
                """, accountId);
    }

    private void writePipelineEvent(String eventType, String entityType, Long entityId, JsonNode payload) {
        jdbc.update("""
                INSERT INTO pipeline_events (event_type, entity_type, entity_id, payload)
                VALUES (?, ?, ?, ?::jsonb)
                """, eventType, entityType, entityId, json(payload));
    }

    private ObjectNode rawMessageJson(LocalMessageInput input, String contentType) {
        if (input.rawJson() != null && !input.rawJson().isNull() && !input.rawJson().isMissingNode()) {
            return input.rawJson().deepCopy();
        }
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("@type", "localReplayMessage");
        raw.put("contentType", contentType);
        raw.put("accountId", input.accountId());
        raw.put("telegramChatId", input.telegramChatId());
        raw.put("telegramMessageId", input.telegramMessageId());
        raw.put("senderName", input.senderName());
        raw.put("text", input.text());
        raw.put("caption", input.caption());
        raw.put("messageDate", input.messageDate());
        raw.set("entities", toArray(input.entities()));
        raw.set("captionEntities", toArray(input.captionEntities()));
        if (input.media() != null) {
            raw.set("media", input.media());
        }
        return raw;
    }

    private ObjectNode eventPayload(long messageId, long groupId, long telegramChatId, long telegramMessageId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "MESSAGE_INGESTED");
        payload.put("messageId", messageId);
        payload.put("groupId", groupId);
        payload.put("telegramChatId", telegramChatId);
        payload.put("telegramMessageId", telegramMessageId);
        return payload;
    }

    private ObjectNode linksPayload(long messageId, int linksCount) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("messageId", messageId);
        payload.put("linksCount", linksCount);
        return payload;
    }

    private ArrayNode toArray(List<JsonNode> nodes) {
        ArrayNode array = objectMapper.createArrayNode();
        if (nodes != null) {
            for (JsonNode node : nodes) {
                array.add(node);
            }
        }
        return array;
    }

    private List<JsonNode> nodeList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(item);
        }
        return result;
    }

    private String defaultContentType(LocalMessageInput input) {
        if (notBlank(input.caption()) || (input.media() != null && !input.media().isNull() && !input.media().isMissingNode())) {
            return "messageMedia";
        }
        return "messageText";
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private long required(Long value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private Timestamp timestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Timestamp.from(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException ignored) {
            return Timestamp.from(Instant.parse(value));
        }
    }

    private String tdlibDate(JsonNode value) {
        if (value == null || !value.isNumber() || value.asLong() <= 0) {
            return null;
        }
        return Instant.ofEpochSecond(value.asLong()).toString();
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException(field + " is required in TDLib update");
        }
        return value.asLong();
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asLong() : null;
    }

    private Long nullableSenderId(JsonNode senderId) {
        if (senderId.path("user_id").isNumber()) {
            return senderId.path("user_id").asLong();
        }
        if (senderId.path("chat_id").isNumber()) {
            return senderId.path("chat_id").asLong();
        }
        return null;
    }

    private String textValue(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : fallback;
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Cannot serialize JSON payload", error);
        }
    }
}
