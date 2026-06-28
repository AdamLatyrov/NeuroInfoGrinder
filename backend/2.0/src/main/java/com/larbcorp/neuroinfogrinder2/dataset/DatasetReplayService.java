package com.larbcorp.neuroinfogrinder2.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.ingest.IngestResult;
import com.larbcorp.neuroinfogrinder2.ingest.LocalMessageInput;
import com.larbcorp.neuroinfogrinder2.ingest.TelegramIngestionService;
import com.larbcorp.neuroinfogrinder2.reset.LocalDbResetSafety;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DatasetReplayService {
    private static final String PIPELINE_VERSION = "stage1-raw-links";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TelegramIngestionService ingestionService;
    private final LocalDbResetSafety localDbResetSafety;
    private final Environment environment;
    private final String datasourceUrl;

    public DatasetReplayService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            TelegramIngestionService ingestionService,
            LocalDbResetSafety localDbResetSafety,
            Environment environment,
            @Value("${spring.datasource.url}") String datasourceUrl
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.ingestionService = ingestionService;
        this.localDbResetSafety = localDbResetSafety;
        this.environment = environment;
        this.datasourceUrl = datasourceUrl;
    }

    @Transactional
    public DatasetImportResult importJsonl(ImportDatasetRequest request) throws IOException {
        assertLocalDatabase();
        Path file = Path.of(request.file()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Dataset JSONL file does not exist: " + file);
        }
        String name = notBlank(request.name()) ? request.name() : stripExtension(file.getFileName().toString());
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("importRaw", request.importRawEnabled());
        metadata.put("format", "jsonl");

        long datasetId = upsertDataset(name, request, file, metadata);

        int imported = 0;
        int rawImported = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(line);
                DatasetMessageInput input = toDatasetMessageInput(node);
                long datasetMessageId = upsertDatasetMessage(datasetId, input);
                imported++;
                if (request.importRawEnabled()) {
                    IngestResult result = ingestDatasetMessage(input, node);
                    rawImported++;
                    ObjectNode resultJson = objectMapper.createObjectNode();
                    resultJson.put("rawMessageId", result.messageId());
                    resultJson.put("datasetMessageId", datasetMessageId);
                }
            }
        }

        jdbc.update("UPDATE datasets SET message_count = (SELECT count(*) FROM dataset_messages WHERE dataset_id = ?) WHERE id = ?",
                datasetId, datasetId);
        return new DatasetImportResult(datasetId, imported, rawImported);
    }

    @Transactional
    public DatasetImportResult importUploadedJsonl(UploadDatasetRequest request) throws IOException {
        if (!notBlank(request.content())) {
            throw new IllegalArgumentException("Файл датасета пустой");
        }
        String filename = notBlank(request.originalFilename()) ? request.originalFilename() : "uploaded.jsonl";
        String name = notBlank(request.name()) ? request.name() : stripExtension(filename);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("importRaw", false);
        metadata.put("format", "jsonl");
        metadata.put("upload", true);
        metadata.put("originalFilename", filename);
        ImportDatasetRequest importRequest = new ImportDatasetRequest(
                "upload:" + filename + ":" + sha256(request.content()),
                name,
                notBlank(request.source()) ? request.source() : "UI_JSONL_UPLOAD",
                request.description(),
                notBlank(request.createdBy()) ? request.createdBy() : "ui",
                false
        );

        long datasetId = upsertDataset(name, importRequest, importRequest.file(), metadata);
        int imported = 0;
        try (BufferedReader reader = new BufferedReader(new StringReader(request.content()))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(line);
                    DatasetMessageInput input = toDatasetMessageInput(node);
                    upsertDatasetMessage(datasetId, input);
                    imported++;
                } catch (JsonProcessingException error) {
                    throw new IllegalArgumentException("Некорректный JSONL: ошибка в строке " + lineNumber, error);
                }
            }
        }
        if (imported == 0) {
            throw new IllegalArgumentException("JSONL не содержит сообщений");
        }
        jdbc.update("UPDATE datasets SET message_count = (SELECT count(*) FROM dataset_messages WHERE dataset_id = ?) WHERE id = ?",
                datasetId, datasetId);
        return new DatasetImportResult(datasetId, imported, 0);
    }

    @Transactional
    public ReplayRunDto replay(ReplayRequest request) {
        assertLocalDatabase();
        ObjectNode config = objectMapper.createObjectNode();
        config.put("stage", "raw_import_links");
        config.put("pipelineVersion", PIPELINE_VERSION);
        config.put("importRaw", true);
        long runId = insertReturningId("""
                INSERT INTO replay_runs (dataset_id, run_name, pipeline_version, config_snapshot_json, status)
                VALUES (?, ?, ?, ?::jsonb, 'RUNNING')
                """,
                request.datasetId(),
                notBlank(request.runName()) ? request.runName() : "Replay " + Instant.now(),
                PIPELINE_VERSION,
                json(config)
        );

        int imported = 0;
        int failed = 0;
        String error = null;
        List<DatasetMessageInput> messages = datasetMessageInputs(request.datasetId());
        for (DatasetMessageInput message : messages) {
            try {
                IngestResult result = ingestDatasetMessage(message, message.rawJson());
                ObjectNode resultJson = objectMapper.createObjectNode();
                resultJson.put("rawMessageId", result.messageId());
                resultJson.put("duplicate", result.duplicateUpdate());
                upsertReplayRunMessage(runId, message.id(), result.messageId(), "IMPORTED", resultJson, null);
                imported++;
            } catch (RuntimeException exception) {
                failed++;
                error = exception.getMessage();
                upsertReplayRunMessage(runId, message.id(), null, "FAILED", objectMapper.createObjectNode(), truncate(exception.getMessage(), 300));
            }
        }

        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("datasetMessages", messages.size());
        metrics.put("imported", imported);
        metrics.put("failed", failed);
        String status = failed == 0 ? "COMPLETED" : "FAILED";
        jdbc.update("""
                UPDATE replay_runs
                SET status = ?,
                    finished_at = now(),
                    metrics_json = ?::jsonb,
                    error = ?
                WHERE id = ?
                """, status, json(metrics), truncate(error, 300), runId);
        return replayRun(runId);
    }

    private void upsertReplayRunMessage(
            long runId,
            Long datasetMessageId,
            Long rawMessageId,
            String status,
            JsonNode resultJson,
            String error
    ) {
        Long existingId = jdbc.query("""
                SELECT id
                FROM replay_run_messages
                WHERE run_id = ? AND dataset_message_id = ?
                """, rs -> rs.next() ? rs.getLong("id") : null, runId, datasetMessageId);
        if (existingId == null) {
            jdbc.update("""
                    INSERT INTO replay_run_messages (run_id, dataset_message_id, raw_message_id, status, result_json, error)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?)
                    """, runId, datasetMessageId, rawMessageId, status, json(resultJson), error);
            return;
        }

        jdbc.update("""
                UPDATE replay_run_messages
                SET raw_message_id = ?,
                    status = ?,
                    result_json = ?::jsonb,
                    error = ?
                WHERE id = ?
                """, rawMessageId, status, json(resultJson), error, existingId);
    }

    public List<DatasetDto> datasets() {
        return jdbc.query("""
                SELECT id, name, source, source_kind, description, message_count, created_at, created_by,
                       file_path, metadata_json::text AS metadata_json
                FROM datasets
                ORDER BY created_at DESC, id DESC
                """, datasetMapper());
    }

    public DatasetDto dataset(long datasetId) {
        return jdbc.queryForObject("""
                SELECT id, name, source, source_kind, description, message_count, created_at, created_by,
                       file_path, metadata_json::text AS metadata_json
                FROM datasets
                WHERE id = ?
                """, datasetMapper(), datasetId);
    }

    public List<DatasetMessageDto> datasetMessages(long datasetId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        return jdbc.query("""
                SELECT id, dataset_id, source_message_id, account_id, telegram_chat_id, telegram_message_id,
                       telegram_topic_id, chat_title, sender_id, sender_name, sender_username, message_date,
                       reply_to_message_id, text, caption, content_type, raw_json::text AS raw_json,
                       entities_json::text AS entities_json, media_json::text AS media_json, import_hash, created_at
                FROM dataset_messages
                WHERE dataset_id = ?
                ORDER BY id
                LIMIT ?
                """, (rs, rowNum) -> new DatasetMessageDto(
                rs.getLong("id"),
                rs.getLong("dataset_id"),
                rs.getString("source_message_id"),
                nullableLong(rs, "account_id"),
                rs.getLong("telegram_chat_id"),
                rs.getLong("telegram_message_id"),
                nullableLong(rs, "telegram_topic_id"),
                rs.getString("chat_title"),
                nullableLong(rs, "sender_id"),
                rs.getString("sender_name"),
                rs.getString("sender_username"),
                iso(rs, "message_date"),
                nullableLong(rs, "reply_to_message_id"),
                rs.getString("text"),
                rs.getString("caption"),
                rs.getString("content_type"),
                rs.getString("raw_json"),
                rs.getString("entities_json"),
                rs.getString("media_json"),
                rs.getString("import_hash"),
                iso(rs, "created_at")
        ), datasetId, safeLimit);
    }

    public void deleteDataset(long datasetId) {
        assertLocalDatabase();
        jdbc.update("DELETE FROM datasets WHERE id = ?", datasetId);
    }

    public List<ReplayRunDto> replayRuns() {
        return jdbc.query("""
                SELECT id, dataset_id, run_name, pipeline_version, config_snapshot_json::text AS config_snapshot_json,
                       status, started_at, finished_at, metrics_json::text AS metrics_json, error
                FROM replay_runs
                ORDER BY started_at DESC, id DESC
                """, replayRunMapper());
    }

    public ReplayRunDto replayRun(long runId) {
        return jdbc.queryForObject("""
                SELECT id, dataset_id, run_name, pipeline_version, config_snapshot_json::text AS config_snapshot_json,
                       status, started_at, finished_at, metrics_json::text AS metrics_json, error
                FROM replay_runs
                WHERE id = ?
                """, replayRunMapper(), runId);
    }

    public List<ReplayRunMessageDto> replayRunMessages(long runId) {
        return jdbc.query("""
                SELECT id, run_id, dataset_message_id, raw_message_id, status, result_json::text AS result_json, error
                FROM replay_run_messages
                WHERE run_id = ?
                ORDER BY id
                """, (rs, rowNum) -> new ReplayRunMessageDto(
                rs.getLong("id"),
                rs.getLong("run_id"),
                rs.getLong("dataset_message_id"),
                nullableLong(rs, "raw_message_id"),
                rs.getString("status"),
                rs.getString("result_json"),
                rs.getString("error")
        ), runId);
    }

    public long datasetMessageCount(long datasetId) {
        return jdbc.queryForObject("SELECT count(*) FROM dataset_messages WHERE dataset_id = ?", Long.class, datasetId);
    }

    private long upsertDatasetMessage(long datasetId, DatasetMessageInput input) {
        Long existingId = jdbc.query("""
                SELECT id
                FROM dataset_messages
                WHERE dataset_id = ? AND source_message_id = ?
                """, rs -> rs.next() ? rs.getLong("id") : null, datasetId, input.sourceMessageId());
        if (existingId != null) {
            jdbc.update("""
                    UPDATE dataset_messages
                    SET import_hash = ?,
                        account_id = ?,
                        telegram_chat_id = ?,
                        telegram_message_id = ?,
                        telegram_topic_id = ?,
                        chat_title = ?,
                        sender_id = ?,
                        sender_name = ?,
                        sender_username = ?,
                        message_date = ?,
                        reply_to_message_id = ?,
                        text = ?,
                        caption = ?,
                        content_type = ?,
                        raw_json = ?::jsonb,
                        entities_json = ?::jsonb,
                        media_json = ?::jsonb
                    WHERE id = ?
                    """,
                    input.importHash(),
                    input.accountId(),
                    input.telegramChatId(),
                    input.telegramMessageId(),
                    input.telegramTopicId(),
                    input.chatTitle(),
                    input.senderId(),
                    input.senderName(),
                    input.senderUsername(),
                    timestamp(input.messageDate()),
                    input.replyToMessageId(),
                    input.text(),
                    input.caption(),
                    input.contentType(),
                    json(input.rawJson()),
                    json(input.entitiesJson()),
                    json(input.mediaJson()),
                    existingId
            );
            return existingId;
        }

        return insertReturningId("""
                INSERT INTO dataset_messages (
                    dataset_id, source_message_id, import_hash, account_id, telegram_chat_id, telegram_message_id, telegram_topic_id,
                    chat_title, sender_id, sender_name, sender_username, message_date, reply_to_message_id,
                    text, caption, content_type, raw_json, entities_json, media_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
                """,
                datasetId,
                input.sourceMessageId(),
                input.importHash(),
                input.accountId(),
                input.telegramChatId(),
                input.telegramMessageId(),
                input.telegramTopicId(),
                input.chatTitle(),
                input.senderId(),
                input.senderName(),
                input.senderUsername(),
                timestamp(input.messageDate()),
                input.replyToMessageId(),
                input.text(),
                input.caption(),
                input.contentType(),
                json(input.rawJson()),
                json(input.entitiesJson()),
                json(input.mediaJson())
        );
    }

    private long upsertDataset(
            String name,
            ImportDatasetRequest request,
            Path file,
            ObjectNode metadata
    ) {
        return upsertDataset(name, request, file.toString(), metadata);
    }

    private long upsertDataset(
            String name,
            ImportDatasetRequest request,
            String filePath,
            ObjectNode metadata
    ) {
        Long existingId = jdbc.query("""
                SELECT id
                FROM datasets
                WHERE file_path = ?
                ORDER BY id DESC
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong("id") : null, filePath);
        if (existingId != null) {
            jdbc.update("""
                    UPDATE datasets
                    SET name = ?,
                        source = ?,
                        source_kind = 'JSONL',
                        description = ?,
                        created_by = ?,
                        metadata_json = metadata_json || ?::jsonb
                    WHERE id = ?
                    """,
                    name,
                    notBlank(request.source()) ? request.source() : "LOCAL_JSONL",
                    request.description(),
                    notBlank(request.createdBy()) ? request.createdBy() : "local",
                    json(metadata),
                    existingId
            );
            return existingId;
        }

        return insertReturningId("""
                INSERT INTO datasets (name, source, source_kind, description, created_by, file_path, metadata_json)
                VALUES (?, ?, 'JSONL', ?, ?, ?, ?::jsonb)
                """,
                name,
                notBlank(request.source()) ? request.source() : "LOCAL_JSONL",
                request.description(),
                notBlank(request.createdBy()) ? request.createdBy() : "local",
                filePath,
                json(metadata)
        );
    }

    private IngestResult ingestDatasetMessage(DatasetMessageInput input, JsonNode rawJson) {
        long accountId = input.accountId() == null ? 1L : input.accountId();
        IngestResult result = ingestionService.ingestLocalMessage(new LocalMessageInput(
                accountId,
                input.telegramChatId(),
                input.telegramMessageId(),
                input.telegramTopicId(),
                input.senderId(),
                input.senderName(),
                input.senderUsername(),
                false,
                input.replyToMessageId(),
                input.text(),
                input.caption(),
                input.messageDate(),
                null,
                input.contentType(),
                nodeList(input.entitiesJson()),
                nodeList(input.captionEntitiesJson()),
                mediaNode(input.mediaJson()),
                rawJson
        ));
        if (notBlank(input.chatTitle())) {
            jdbc.update("""
                    UPDATE telegram_chats
                    SET title = ?, updated_at = now()
                    WHERE account_id = ? AND telegram_chat_id = ?
                    """, input.chatTitle(), accountId, input.telegramChatId());
        }
        return result;
    }

    private List<DatasetMessageInput> datasetMessageInputs(long datasetId) {
        return jdbc.query("""
                SELECT id, source_message_id, account_id, telegram_chat_id, telegram_message_id, telegram_topic_id,
                       chat_title, sender_id, sender_name, sender_username, message_date, reply_to_message_id,
                       text, caption, content_type, raw_json::text AS raw_json, entities_json::text AS entities_json,
                       media_json::text AS media_json
                FROM dataset_messages
                WHERE dataset_id = ?
                ORDER BY id
                """, (rs, rowNum) -> new DatasetMessageInput(
                rs.getLong("id"),
                rs.getString("source_message_id"),
                null,
                nullableLong(rs, "account_id"),
                rs.getLong("telegram_chat_id"),
                rs.getLong("telegram_message_id"),
                nullableLong(rs, "telegram_topic_id"),
                rs.getString("chat_title"),
                nullableLong(rs, "sender_id"),
                rs.getString("sender_name"),
                rs.getString("sender_username"),
                iso(rs, "message_date"),
                nullableLong(rs, "reply_to_message_id"),
                rs.getString("text"),
                rs.getString("caption"),
                rs.getString("content_type"),
                readJson(rs.getString("raw_json")),
                readJson(rs.getString("entities_json")),
                objectMapper.createArrayNode(),
                readJson(rs.getString("media_json"))
        ), datasetId);
    }

    private DatasetMessageInput toDatasetMessageInput(JsonNode node) {
        Long accountId = nullableLong(node, "accountId");
        Long telegramChatId = requiredLong(node, "telegramChatId");
        Long telegramMessageId = requiredLong(node, "telegramMessageId");
        String sourceMessageId = text(node, "sourceMessageId");
        if (!notBlank(sourceMessageId)) {
            sourceMessageId = (accountId == null ? 1L : accountId) + ":" + telegramChatId + ":" + telegramMessageId;
        }
        JsonNode entities = arrayOrEmpty(node.path("entities"));
        JsonNode captionEntities = arrayOrEmpty(node.path("captionEntities"));
        JsonNode media = arrayOrEmpty(node.path("media"));
        JsonNode raw = node.has("rawJson") ? node.path("rawJson") : node;
        return new DatasetMessageInput(
                null,
                sourceMessageId,
                sha256(node.toString()),
                accountId,
                telegramChatId,
                telegramMessageId,
                nullableLong(node, "telegramTopicId"),
                text(node, "chatTitle"),
                nullableLong(node, "senderId"),
                text(node, "senderName"),
                text(node, "senderUsername"),
                text(node, "messageDate"),
                nullableLong(node, "replyToMessageId"),
                text(node, "text"),
                text(node, "caption"),
                notBlank(text(node, "contentType")) ? text(node, "contentType") : "messageText",
                raw,
                entities,
                captionEntities,
                media
        );
    }

    private void assertLocalDatabase() {
        String profile = String.join(",", environment.getActiveProfiles());
        if (!notBlank(profile)) {
            profile = "local";
        }
        LocalDbResetSafety.SafetyDecision decision = localDbResetSafety.evaluate(datasourceUrl, profile);
        if (!decision.allowed()) {
            throw new IllegalStateException("Dataset import/replay denied: " + decision.reason());
        }
    }

    private RowMapper<DatasetDto> datasetMapper() {
        return (rs, rowNum) -> new DatasetDto(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("source"),
                rs.getString("source_kind"),
                rs.getString("description"),
                rs.getLong("message_count"),
                iso(rs, "created_at"),
                rs.getString("created_by"),
                rs.getString("file_path"),
                rs.getString("metadata_json")
        );
    }

    private RowMapper<ReplayRunDto> replayRunMapper() {
        return (rs, rowNum) -> new ReplayRunDto(
                rs.getLong("id"),
                rs.getLong("dataset_id"),
                rs.getString("run_name"),
                rs.getString("pipeline_version"),
                rs.getString("config_snapshot_json"),
                rs.getString("status"),
                iso(rs, "started_at"),
                iso(rs, "finished_at"),
                rs.getString("metrics_json"),
                rs.getString("error")
        );
    }

    private JsonNode mediaNode(JsonNode mediaJson) {
        if (mediaJson == null || mediaJson.isMissingNode() || mediaJson.isNull()) {
            return objectMapper.nullNode();
        }
        if (mediaJson.isArray()) {
            return mediaJson.isEmpty() ? objectMapper.nullNode() : mediaJson.get(0);
        }
        return mediaJson;
    }

    private List<JsonNode> nodeList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        node.forEach(result::add);
        return result;
    }

    private JsonNode arrayOrEmpty(JsonNode node) {
        return node != null && node.isArray() ? node : objectMapper.createArrayNode();
    }

    private JsonNode readJson(String json) {
        if (!notBlank(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid JSON stored in database", error);
        }
    }

    private Long requiredLong(JsonNode node, String field) {
        Long value = nullableLong(node, field);
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asLong() : null;
    }

    private Long nullableLong(ResultSet rs, String field) throws SQLException {
        long value = rs.getLong(field);
        return rs.wasNull() ? null : value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private Timestamp timestamp(String value) {
        if (!notBlank(value)) {
            return null;
        }
        try {
            return Timestamp.from(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException ignored) {
            return Timestamp.from(Instant.parse(value));
        }
    }

    private String iso(ResultSet rs, String field) throws SQLException {
        OffsetDateTime value = rs.getObject(field, OffsetDateTime.class);
        return value == null ? null : value.toString();
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Cannot serialize JSON", error);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }

    private long insertReturningId(String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < params.length; index++) {
                statement.setObject(index + 1, params[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return generated id");
        }
        return key.longValue();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record ImportDatasetRequest(
            String file,
            String name,
            String source,
            String description,
            String createdBy,
            Boolean importRaw
    ) {
        public boolean importRawEnabled() {
            return importRaw == null || importRaw;
        }
    }

    public record DatasetImportResult(long datasetId, int importedMessages, int rawImportedMessages) {
    }

    public record UploadDatasetRequest(
            String name,
            String source,
            String description,
            String createdBy,
            String originalFilename,
            String content
    ) {
    }

    public record ReplayRequest(long datasetId, String runName) {
    }

    public record DatasetDto(
            long id,
            String name,
            String source,
            String sourceKind,
            String description,
            long messageCount,
            String createdAt,
            String createdBy,
            String filePath,
            String metadataJson
    ) {
    }

    public record ReplayRunDto(
            long id,
            long datasetId,
            String runName,
            String pipelineVersion,
            String configSnapshotJson,
            String status,
            String startedAt,
            String finishedAt,
            String metricsJson,
            String error
    ) {
    }

    public record ReplayRunMessageDto(
            long id,
            long runId,
            long datasetMessageId,
            Long rawMessageId,
            String status,
            String resultJson,
            String error
    ) {
    }

    public record DatasetMessageDto(
            long id,
            long datasetId,
            String sourceMessageId,
            Long accountId,
            long telegramChatId,
            long telegramMessageId,
            Long telegramTopicId,
            String chatTitle,
            Long senderId,
            String senderName,
            String senderUsername,
            String messageDate,
            Long replyToMessageId,
            String text,
            String caption,
            String contentType,
            String rawJson,
            String entitiesJson,
            String mediaJson,
            String importHash,
            String createdAt
    ) {
    }

    private record DatasetMessageInput(
            Long id,
            String sourceMessageId,
            String importHash,
            Long accountId,
            Long telegramChatId,
            Long telegramMessageId,
            Long telegramTopicId,
            String chatTitle,
            Long senderId,
            String senderName,
            String senderUsername,
            String messageDate,
            Long replyToMessageId,
            String text,
            String caption,
            String contentType,
            JsonNode rawJson,
            JsonNode entitiesJson,
            JsonNode captionEntitiesJson,
            JsonNode mediaJson
    ) {
    }
}
