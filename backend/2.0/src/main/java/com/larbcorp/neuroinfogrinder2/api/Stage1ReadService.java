package com.larbcorp.neuroinfogrinder2.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.telegram.model.TelegramChatDto;
import com.larbcorp.neuroinfogrinder2.telegram.model.TelegramTopicDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class Stage1ReadService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PhonePrivacyService phonePrivacyService;

    @Autowired
    public Stage1ReadService(JdbcTemplate jdbc, ObjectMapper objectMapper, PhonePrivacyService phonePrivacyService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.phonePrivacyService = phonePrivacyService;
    }

    Stage1ReadService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this(jdbc, objectMapper, new PhonePrivacyService(null));
    }

    public List<AccountDto> accounts() {
        return jdbc.query("""
                SELECT a.id,
                       a.name,
                       a.phone_masked,
                       a.phone_encrypted,
                       a.status,
                       a.created_at,
                       count(c.id) AS groups_count
                FROM telegram_accounts a
                LEFT JOIN telegram_chats c ON c.account_id = a.id
                WHERE a.owner_user_id = 1 AND a.deleted_at IS NULL
                GROUP BY a.id
                ORDER BY a.id
                """, accountMapper());
    }

    public AccountDto createAccount(String phone) {
        String normalizedPhone = phonePrivacyService.normalize(phone);
        String maskedPhone = phonePrivacyService.mask(phone);
        String phoneHash = phonePrivacyService.hash(normalizedPhone);
        String phoneEncrypted = phonePrivacyService.encrypt(normalizedPhone);
        Long identityId = Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO telegram_identities (phone_hash, phone_encrypted)
                VALUES (?, ?)
                ON CONFLICT (phone_hash) DO UPDATE SET phone_encrypted = COALESCE(telegram_identities.phone_encrypted, EXCLUDED.phone_encrypted), updated_at = now()
                RETURNING id
                """, Long.class, phoneHash, phoneEncrypted));
        Long id = Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO telegram_accounts (name, phone_masked, phone_hash, phone_encrypted, identity_id, owner_user_id, status)
                VALUES (?, ?, ?, ?, ?, 1, 'WAIT_PHONE')
                ON CONFLICT (owner_user_id, identity_id) WHERE deleted_at IS NULL AND identity_id IS NOT NULL
                DO UPDATE SET updated_at = now()
                RETURNING id
                """, Long.class, "Telegram account", maskedPhone, phoneHash, phoneEncrypted, identityId));
        return account(id);
    }

    public AccountDto updateAccountStatus(long id, String status) {
        jdbc.update("UPDATE telegram_accounts SET status = ?, updated_at = now() WHERE id = ?", status, id);
        jdbc.update("""
                INSERT INTO telegram_account_health (account_id, status, updated_at)
                VALUES (?, ?, now())
                ON CONFLICT (account_id) DO UPDATE SET status = EXCLUDED.status, updated_at = now()
                """, id, status);
        return account(id);
    }

    @Transactional
    public AccountDto updateAccountOperationalStatus(long id, String status, String eventType) {
        jdbc.update("UPDATE telegram_accounts SET status = ?, updated_at = now() WHERE id = ?", status, id);
        jdbc.update("""
                INSERT INTO telegram_account_health (account_id, status, updated_at)
                VALUES (?, ?, now())
                ON CONFLICT (account_id) DO UPDATE SET status = EXCLUDED.status, updated_at = now()
                """, id, status);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", status);
        recordRuntimeEvent(eventType, id, null, null, null, null, payload);
        return account(id);
    }

    @Transactional
    public void deleteAccount(long id) {
        jdbc.update("DELETE FROM telegram_runtime_errors WHERE account_id = ?", id);
        jdbc.update("DELETE FROM telegram_runtime_events WHERE account_id = ?", id);
        jdbc.update("DELETE FROM auto_pipeline_queue WHERE account_id = ?", id);
        jdbc.update("DELETE FROM auto_pipeline_batches WHERE account_id = ?", id);
        jdbc.update("DELETE FROM auto_pipeline_settings WHERE account_id = ?", id);
        jdbc.update("DELETE FROM telegram_topic_sync_jobs WHERE account_id = ?", id);
        recordRuntimeEvent("TELEGRAM_ACCOUNT_DELETED", id, null, null, null, null, null);
        jdbc.update("""
                UPDATE telegram_accounts
                SET status = 'DISCONNECTED', deleted_at = now(), deleted_by = 1, disconnect_reason = 'USER_DELETED_SESSION', updated_at = now()
                WHERE id = ? AND owner_user_id = 1
                """, id);
    }

    public AccountDto account(long id) {
        return jdbc.queryForObject("""
                SELECT a.id,
                       a.name,
                       a.phone_masked,
                       a.phone_encrypted,
                       a.status,
                       a.created_at,
                       count(c.id) AS groups_count
                FROM telegram_accounts a
                LEFT JOIN telegram_chats c ON c.account_id = a.id
                WHERE a.id = ? AND a.deleted_at IS NULL
                GROUP BY a.id
                """, accountMapper(), id);
    }

    public PageResponse<GroupDto> groups(int page, int size, Boolean enabled, String search) {
        return groups(page, size, enabled, search, null);
    }

    public PageResponse<GroupDto> groups(int page, int size, Boolean enabled, String search, Long accountId) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (accountId != null) {
            where.append(" AND c.account_id = ?");
            args.add(accountId);
        }
        if (enabled != null) {
            where.append(" AND c.is_enabled = ?");
            args.add(enabled);
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND lower(c.title) LIKE ?");
            args.add("%" + search.toLowerCase(Locale.ROOT) + "%");
        }

        long total = jdbc.queryForObject("SELECT count(*) FROM telegram_chats c" + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(page * size);
        List<GroupDto> content = jdbc.query("""
                SELECT c.id,
                       c.telegram_chat_id,
                       c.title,
                       c.username,
                       c.type,
                       c.is_forum,
                       c.is_enabled,
                       c.account_id,
                       c.last_message_date,
                       c.last_message_id,
                        (SELECT count(*) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id) AS raw_messages_total,
                        (SELECT count(*) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id AND m.ingested_at >= now() - interval '24 hours') AS raw_messages_last24h,
                        (SELECT max(m.ingested_at) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id) AS latest_raw_message_at,
                        GREATEST(
                            (SELECT count(*) FROM telegram_topics t WHERE t.account_id = c.account_id AND t.telegram_chat_id = c.telegram_chat_id),
                            (SELECT count(DISTINCT COALESCE(m.telegram_topic_id, m.message_thread_id)) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id AND COALESCE(m.telegram_topic_id, m.message_thread_id) IS NOT NULL)
                        ) AS topics_count,
                         (c.is_enabled = true AND (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL)) AS auto_pipeline_enabled,
                        (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL) AS active_dialog,
                        COALESCE(c.sync_state, 'DB_CACHE') AS source
                FROM telegram_chats c
                """ + where + " " + """
                ORDER BY c.updated_at DESC, c.id DESC
                LIMIT ? OFFSET ?
                """, groupMapper(), queryArgs.toArray());

        return page(content, page, size, total);
    }

    public GroupDto upsertTelegramChat(long accountId, TelegramChatDto chat) {
        boolean direct = "DIRECT_CHAT".equals(chat.sourceType());
        boolean channel = "CHANNEL".equals(chat.sourceType());
        boolean group = !direct && !channel;
        Long id = Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO telegram_chats (
                    account_id,
                    identity_id,
                    telegram_chat_id,
                    title,
                    type,
                    username,
                    is_forum,
                    is_group,
                    is_direct,
                    is_channel,
                    is_supergroup,
                    has_topics,
                    is_enabled,
                    last_message_id,
                    sync_state,
                    updated_at
                )
                VALUES (?, (SELECT identity_id FROM telegram_accounts WHERE id = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, 'DB_CACHE', now())
                ON CONFLICT (account_id, telegram_chat_id)
                DO UPDATE SET
                    identity_id = COALESCE(telegram_chats.identity_id, EXCLUDED.identity_id),
                    title = EXCLUDED.title,
                    type = EXCLUDED.type,
                    username = EXCLUDED.username,
                    is_forum = EXCLUDED.is_forum,
                    is_group = EXCLUDED.is_group,
                    is_direct = EXCLUDED.is_direct,
                    is_channel = EXCLUDED.is_channel,
                    is_supergroup = EXCLUDED.is_supergroup,
                    has_topics = EXCLUDED.has_topics,
                    last_message_id = EXCLUDED.last_message_id,
                    sync_state = 'DB_CACHE',
                    updated_at = now()
                RETURNING id
                """, Long.class,
                accountId,
                accountId,
                chat.id(),
                chat.title(),
                chat.sourceType(),
                chat.username(),
                chat.forum(),
                group,
                direct,
                channel,
                group || channel,
                chat.forum(),
                chat.lastMessageId() <= 0 ? null : chat.lastMessageId()
        ));
        return group(id);
    }

    public void updateTelegramChatTitle(long accountId, long telegramChatId, String title) {
        jdbc.update("""
                UPDATE telegram_chats
                SET title = COALESCE(NULLIF(?, ''), title), updated_at = now()
                WHERE account_id = ? AND telegram_chat_id = ?
                """, title, accountId, telegramChatId);
    }

    public void updateTelegramChatLastMessage(long accountId, long telegramChatId, Long lastMessageId, OffsetDateTime lastMessageAt) {
        jdbc.update("""
                UPDATE telegram_chats
                SET last_message_id = COALESCE(?, last_message_id),
                    last_message_date = COALESCE(?, last_message_date),
                    last_message_at = COALESCE(?, last_message_date),
                    updated_at = now()
                WHERE account_id = ? AND telegram_chat_id = ?
                """, lastMessageId, lastMessageAt, lastMessageAt, accountId, telegramChatId);
    }

    public void updateTelegramChatPosition(long accountId, long telegramChatId, String chatList, Long positionOrder) {
        jdbc.update("""
                UPDATE telegram_chats
                SET chat_list = COALESCE(?, chat_list),
                    position_order = COALESCE(?, position_order),
                    updated_at = now()
                WHERE account_id = ? AND telegram_chat_id = ?
                """, chatList, positionOrder, accountId, telegramChatId);
    }

    public void updateSupergroupMetadata(long accountId, long supergroupId, boolean isChannel, boolean isForum, boolean hasTopics) {
        jdbc.update("""
                UPDATE telegram_chats
                SET supergroup_id = ?,
                    is_channel = ?,
                    is_group = NOT ?,
                    is_supergroup = true,
                    is_forum = ?,
                    has_topics = ?,
                    type = CASE WHEN ? THEN 'CHANNEL' ELSE 'GROUP' END,
                    updated_at = now()
                WHERE account_id = ? AND supergroup_id = ?
                """, supergroupId, isChannel, isChannel, isForum, hasTopics, isChannel, accountId, supergroupId);
    }

    public void linkChatToSupergroup(long accountId, long telegramChatId, long supergroupId, boolean isChannel) {
        jdbc.update("""
                UPDATE telegram_chats
                SET supergroup_id = ?,
                    is_supergroup = true,
                    is_channel = ?,
                    is_group = NOT ?,
                    type = CASE WHEN ? THEN 'CHANNEL' ELSE 'GROUP' END,
                    updated_at = now()
                WHERE account_id = ? AND telegram_chat_id = ?
                """, supergroupId, isChannel, isChannel, isChannel, accountId, telegramChatId);
    }

    public void upsertTelegramTopic(long accountId, TelegramTopicDto topic) {
        jdbc.update("""
                INSERT INTO telegram_topics (
                    account_id,
                    telegram_chat_id,
                    telegram_topic_id,
                    message_thread_id,
                    title,
                    is_closed,
                    title_source,
                    title_confidence,
                    title_updated_at,
                    sync_state
                )
                VALUES (?, ?, ?, ?, ?, false, 'GET_FORUM_TOPICS', 1.0, now(), 'SYNCED')
                ON CONFLICT (account_id, telegram_chat_id, telegram_topic_id)
                DO UPDATE SET title = EXCLUDED.title,
                              message_thread_id = EXCLUDED.message_thread_id,
                              title_source = 'GET_FORUM_TOPICS',
                              title_confidence = 1.0,
                              title_updated_at = now(),
                              sync_state = 'SYNCED',
                              updated_at = now()
                """, accountId, topic.chatId(), topic.forumTopicId(), topic.messageThreadId(), topic.name());
        int rawUpdated = updateRawMessageTopicTitles(accountId, topic.chatId(), topic.forumTopicId(), topic.messageThreadId(), topic.name());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("forumTopicId", topic.forumTopicId());
        payload.put("messageThreadId", topic.messageThreadId());
        payload.put("title", topic.name());
        payload.put("rawMessagesUpdated", rawUpdated);
        recordRuntimeEvent("TOPIC_TITLE_UPDATED", accountId, topic.chatId(), null, null, null, payload);
    }

    public TopicReconcileResult reconcileTelegramTopics(long accountId, long telegramChatId) {
        long placeholdersBefore = jdbc.queryForObject("""
                SELECT count(*)
                FROM telegram_topics
                WHERE account_id = ?
                  AND telegram_chat_id = ?
                  AND (title IS NULL OR title = 'Тема без названия' OR title LIKE 'Topic %' OR title_source = 'PLACEHOLDER_UNKNOWN')
                """, Long.class, accountId, telegramChatId);
        int rawMessagesUpdated = jdbc.update("""
                UPDATE raw_messages m
                SET topic_title = t.title, updated_at = now()
                FROM telegram_topics t
                WHERE m.account_id = t.account_id
                  AND m.telegram_chat_id = t.telegram_chat_id
                  AND m.account_id = ?
                  AND m.telegram_chat_id = ?
                  AND t.title IS NOT NULL
                  AND t.title <> 'Тема без названия'
                  AND t.title NOT LIKE 'Topic %'
                  AND t.title_source <> 'PLACEHOLDER_UNKNOWN'
                  AND (
                      m.forum_topic_id = t.telegram_topic_id
                      OR m.forum_topic_id = t.message_thread_id
                      OR m.message_thread_id = t.telegram_topic_id
                      OR m.message_thread_id = t.message_thread_id
                      OR m.telegram_topic_id = t.telegram_topic_id
                      OR m.telegram_topic_id = t.message_thread_id
                  )
                  AND (m.topic_title IS NULL OR m.topic_title = 'Тема без названия' OR m.topic_title LIKE 'Topic %' OR m.topic_title <> t.title)
                """, accountId, telegramChatId);
        long unresolved = jdbc.queryForObject("""
                SELECT count(*)
                FROM telegram_topics
                WHERE account_id = ?
                  AND telegram_chat_id = ?
                  AND (title IS NULL OR title = 'Тема без названия' OR title LIKE 'Topic %' OR title_source = 'PLACEHOLDER_UNKNOWN')
                """, Long.class, accountId, telegramChatId);
        List<String> unresolvedExamples = jdbc.query("""
                SELECT 'forum_topic_id=' || telegram_topic_id || ', message_thread_id=' || COALESCE(message_thread_id::text, '-') || ', title=' || COALESCE(title, '-') AS example
                FROM telegram_topics
                WHERE account_id = ?
                  AND telegram_chat_id = ?
                  AND (title IS NULL OR title = 'Тема без названия' OR title LIKE 'Topic %' OR title_source = 'PLACEHOLDER_UNKNOWN')
                ORDER BY updated_at DESC
                LIMIT 10
                """, (rs, rowNum) -> rs.getString("example"), accountId, telegramChatId);
        long resolved = Math.max(0, placeholdersBefore - unresolved);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("placeholdersBefore", placeholdersBefore);
        payload.put("resolved", resolved);
        payload.put("unresolved", unresolved);
        payload.put("rawMessagesUpdated", rawMessagesUpdated);
        recordRuntimeEvent("TOPIC_RECONCILE_COMPLETED", accountId, telegramChatId, null, null, null, payload);
        return new TopicReconcileResult(placeholdersBefore, resolved, unresolved, rawMessagesUpdated, unresolvedExamples);
    }

    public TopicDiagnostics topicDiagnostics(long accountId, long telegramChatId) {
        long topics = jdbc.queryForObject("SELECT count(*) FROM telegram_topics WHERE account_id = ? AND telegram_chat_id = ?", Long.class, accountId, telegramChatId);
        long placeholders = jdbc.queryForObject("""
                SELECT count(*) FROM telegram_topics
                WHERE account_id = ? AND telegram_chat_id = ?
                  AND (title IS NULL OR title = 'Тема без названия' OR title LIKE 'Topic %' OR title_source = 'PLACEHOLDER_UNKNOWN')
                """, Long.class, accountId, telegramChatId);
        long rawMessages = jdbc.queryForObject("SELECT count(*) FROM raw_messages WHERE account_id = ? AND telegram_chat_id = ?", Long.class, accountId, telegramChatId);
        long rawWithTopic = jdbc.queryForObject("""
                SELECT count(*) FROM raw_messages
                WHERE account_id = ? AND telegram_chat_id = ?
                  AND (forum_topic_id IS NOT NULL OR message_thread_id IS NOT NULL OR telegram_topic_id IS NOT NULL)
                """, Long.class, accountId, telegramChatId);
        List<String> topicExamples = jdbc.query("""
                SELECT 'forum_topic_id=' || telegram_topic_id || ', message_thread_id=' || COALESCE(message_thread_id::text, '-') || ', title=' || COALESCE(title, '-') || ', source=' || COALESCE(title_source, '-') AS example
                FROM telegram_topics
                WHERE account_id = ? AND telegram_chat_id = ?
                ORDER BY updated_at DESC
                LIMIT 20
                """, (rs, rowNum) -> rs.getString("example"), accountId, telegramChatId);
        List<String> rawExamples = jdbc.query("""
                SELECT 'message_id=' || telegram_message_id || ', forum_topic_id=' || COALESCE(forum_topic_id::text, '-') || ', message_thread_id=' || COALESCE(message_thread_id::text, '-') || ', telegram_topic_id=' || COALESCE(telegram_topic_id::text, '-') || ', topic_title=' || COALESCE(topic_title, '-') AS example
                FROM raw_messages
                WHERE account_id = ? AND telegram_chat_id = ?
                ORDER BY id DESC
                LIMIT 20
                """, (rs, rowNum) -> rs.getString("example"), accountId, telegramChatId);
        return new TopicDiagnostics(accountId, telegramChatId, topics, placeholders, rawMessages, rawWithTopic, topicExamples, rawExamples);
    }

    private int updateRawMessageTopicTitles(long accountId, long telegramChatId, long forumTopicId, long messageThreadId, String title) {
        if (title == null || title.isBlank() || title.equals("Тема без названия") || title.startsWith("Topic ")) {
            return 0;
        }
        return jdbc.update("""
                UPDATE raw_messages
                SET topic_title = ?, updated_at = now()
                WHERE account_id = ?
                  AND telegram_chat_id = ?
                  AND (
                      forum_topic_id = ? OR forum_topic_id = ?
                      OR message_thread_id = ? OR message_thread_id = ?
                      OR telegram_topic_id = ? OR telegram_topic_id = ?
                  )
                  AND (topic_title IS NULL OR topic_title = 'Тема без названия' OR topic_title LIKE 'Topic %' OR topic_title <> ?)
                """, title, accountId, telegramChatId, forumTopicId, messageThreadId, forumTopicId, messageThreadId, forumTopicId, messageThreadId, title);
    }

    public void updateAccountTdlibState(long accountId, String status, String databasePath, String authState, String errorSummary) {
        jdbc.update("""
                UPDATE telegram_accounts
                SET status = ?, tdlib_database_path = ?, updated_at = now()
                WHERE id = ?
                """, status, databasePath, accountId);
        String summary = authState == null ? errorSummary : "auth_state=" + authState + (errorSummary == null ? "" : "; " + errorSummary);
        ensureAccountExists(accountId);
        jdbc.update("""
                INSERT INTO telegram_account_health (account_id, status, error_summary, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (account_id)
                DO UPDATE SET status = EXCLUDED.status, error_summary = EXCLUDED.error_summary, updated_at = now()
                """, accountId, status, summary);
    }

    public boolean isChatEnabled(long accountId, long telegramChatId) {
        Boolean enabled = jdbc.query("""
                SELECT is_enabled
                FROM telegram_chats
                WHERE account_id = ? AND telegram_chat_id = ?
                """, rs -> rs.next() ? rs.getBoolean("is_enabled") : null, accountId, telegramChatId);
        return enabled == null || enabled;
    }

    public GroupDto updateGroup(long groupId, Boolean enabled, String title) {
        if (enabled != null) {
            jdbc.update("UPDATE telegram_chats SET is_enabled = ?, updated_at = now() WHERE id = ?", enabled, groupId);
        }
        if (title != null && !title.isBlank()) {
            jdbc.update("UPDATE telegram_chats SET title = ?, updated_at = now() WHERE id = ?", title, groupId);
        }
        return group(groupId);
    }

    public GroupDto group(long groupId) {
        return jdbc.queryForObject("""
                SELECT c.id,
                       c.telegram_chat_id,
                       c.title,
                       c.username,
                       c.type,
                       c.is_forum,
                       c.is_enabled,
                       c.account_id,
                       c.last_message_date,
                       c.last_message_id,
                        (SELECT count(*) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id) AS raw_messages_total,
                        (SELECT count(*) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id AND m.ingested_at >= now() - interval '24 hours') AS raw_messages_last24h,
                        (SELECT max(m.ingested_at) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id) AS latest_raw_message_at,
                        GREATEST(
                            (SELECT count(*) FROM telegram_topics t WHERE t.account_id = c.account_id AND t.telegram_chat_id = c.telegram_chat_id),
                            (SELECT count(DISTINCT COALESCE(m.telegram_topic_id, m.message_thread_id)) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id AND COALESCE(m.telegram_topic_id, m.message_thread_id) IS NOT NULL)
                        ) AS topics_count,
                         (c.is_enabled = true AND (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL)) AS auto_pipeline_enabled,
                        (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL) AS active_dialog,
                        COALESCE(c.sync_state, 'DB_CACHE') AS source
                FROM telegram_chats c
                WHERE c.id = ?
                """, groupMapper(), groupId);
    }

    public GroupDto groupByTelegramChatId(long telegramChatId) {
        return jdbc.queryForObject("""
                SELECT c.id,
                       c.telegram_chat_id,
                       c.title,
                       c.username,
                       c.type,
                       c.is_forum,
                       c.is_enabled,
                       c.account_id,
                       c.last_message_date,
                       c.last_message_id,
                        (SELECT count(*) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id) AS raw_messages_total,
                        (SELECT count(*) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id AND m.ingested_at >= now() - interval '24 hours') AS raw_messages_last24h,
                        (SELECT max(m.ingested_at) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id) AS latest_raw_message_at,
                        GREATEST(
                            (SELECT count(*) FROM telegram_topics t WHERE t.account_id = c.account_id AND t.telegram_chat_id = c.telegram_chat_id),
                            (SELECT count(DISTINCT COALESCE(m.telegram_topic_id, m.message_thread_id)) FROM raw_messages m WHERE m.account_id = c.account_id AND m.telegram_chat_id = c.telegram_chat_id AND COALESCE(m.telegram_topic_id, m.message_thread_id) IS NOT NULL)
                        ) AS topics_count,
                         (c.is_enabled = true AND (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL)) AS auto_pipeline_enabled,
                        (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL) AS active_dialog,
                        COALESCE(c.sync_state, 'DB_CACHE') AS source
                FROM telegram_chats c
                WHERE c.telegram_chat_id = ?
                ORDER BY c.updated_at DESC
                LIMIT 1
                """, groupMapper(), telegramChatId);
    }

    public List<TopicDto> topicsByTelegramChatId(long telegramChatId) {
        return topicsByTelegramChatId(null, telegramChatId);
    }

    public List<TopicDto> topicsByTelegramChatId(Long accountId, long telegramChatId) {
        List<Object> args = new ArrayList<>();
        args.add(telegramChatId);
        String accountFilter = "";
        if (accountId != null) {
            accountFilter = " AND account_id = ?";
            args.add(accountId);
        }
        return jdbc.query("""
                SELECT telegram_chat_id,
                       telegram_topic_id,
                       COALESCE(message_thread_id, telegram_topic_id) AS message_thread_id,
                       title,
                       title_source,
                       sync_state
                FROM telegram_topics
                WHERE telegram_chat_id = ?
                """ + accountFilter + " " + """
                ORDER BY telegram_topic_id
                """, (rs, rowNum) -> new TopicDto(
                rs.getLong("telegram_chat_id"),
                rs.getLong("telegram_topic_id"),
                rs.getLong("message_thread_id"),
                rs.getString("title"),
                rs.getLong("telegram_topic_id") == 0,
                rs.getString("title_source"),
                rs.getString("sync_state")
        ), args.toArray());
    }

    public PageResponse<MessageDto> messages(long groupId, int page, int size, Long topicId) {
        GroupDto group = group(groupId);
        List<Object> args = new ArrayList<>();
        args.add(group.accountId());
        args.add(group.telegramChatId());
        String where = " WHERE m.account_id = ? AND m.telegram_chat_id = ?";
        if (topicId != null) {
            where += " AND m.telegram_topic_id = ?";
            args.add(topicId);
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM raw_messages m" + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(page * size);
        List<MessageDto> content = jdbc.query("""
                SELECT m.*,
                       c.id AS group_id,
                       c.title AS group_title,
                       c.username AS group_username,
                       (
                           SELECT count(*)
                           FROM raw_messages r
                           WHERE r.account_id = m.account_id
                             AND r.telegram_chat_id = m.telegram_chat_id
                             AND r.reply_to_message_id = m.telegram_message_id
                       ) AS reply_count,
                       t.title AS topic_name
                FROM raw_messages m
                JOIN telegram_chats c ON c.account_id = m.account_id AND c.telegram_chat_id = m.telegram_chat_id
                LEFT JOIN telegram_topics t ON t.account_id = m.account_id
                    AND t.telegram_chat_id = m.telegram_chat_id
                    AND t.telegram_topic_id = m.telegram_topic_id
                """ + where + " " + """
                ORDER BY m.message_date DESC NULLS LAST, m.telegram_message_id DESC
                LIMIT ? OFFSET ?
                """, messageMapper(), queryArgs.toArray());
        return page(content, page, size, total);
    }

    public MessageDto message(long groupId, long messageId) {
        return jdbc.queryForObject("""
                SELECT m.*,
                       c.id AS group_id,
                       c.title AS group_title,
                       c.username AS group_username,
                       (
                           SELECT count(*)
                           FROM raw_messages r
                           WHERE r.account_id = m.account_id
                             AND r.telegram_chat_id = m.telegram_chat_id
                             AND r.reply_to_message_id = m.telegram_message_id
                       ) AS reply_count,
                       t.title AS topic_name
                FROM raw_messages m
                JOIN telegram_chats c ON c.account_id = m.account_id AND c.telegram_chat_id = m.telegram_chat_id
                LEFT JOIN telegram_topics t ON t.account_id = m.account_id
                    AND t.telegram_chat_id = m.telegram_chat_id
                    AND t.telegram_topic_id = m.telegram_topic_id
                WHERE c.id = ? AND m.id = ?
                """, messageMapper(), groupId, messageId);
    }

    public MessageChainDto messageChain(long groupId, long messageId) {
        MessageDto root = message(groupId, messageId);
        List<MessageDto> messages = jdbc.query("""
                SELECT m.*,
                       c.id AS group_id,
                       c.title AS group_title,
                       c.username AS group_username,
                       (
                           SELECT count(*)
                           FROM raw_messages r
                           WHERE r.account_id = m.account_id
                             AND r.telegram_chat_id = m.telegram_chat_id
                             AND r.reply_to_message_id = m.telegram_message_id
                       ) AS reply_count,
                       t.title AS topic_name
                FROM raw_messages m
                JOIN telegram_chats c ON c.account_id = m.account_id AND c.telegram_chat_id = m.telegram_chat_id
                LEFT JOIN telegram_topics t ON t.account_id = m.account_id
                    AND t.telegram_chat_id = m.telegram_chat_id
                    AND t.telegram_topic_id = m.telegram_topic_id
                WHERE c.id = ?
                  AND (m.id = ? OR m.reply_to_message_id = ? OR m.telegram_message_id = ?)
                ORDER BY m.message_date ASC NULLS LAST, m.telegram_message_id ASC
                """, messageMapper(), groupId, messageId, root.telegramMessageId(), root.replyToMessageId() == null ? root.telegramMessageId() : root.replyToMessageId());
        return new MessageChainDto(messageId, groupId, root.id(), messages, "raw_thread");
    }

    public Map<String, Object> messagePipelineDetail(long rawId) {
        Map<String, Object> raw = rawPipelineRow(rawId);
        if (raw == null) {
            Map<String, Object> missing = ordered();
            missing.put("rawId", rawId);
            missing.put("pipelineStatus", "NOT_INGESTED");
            missing.put("timeline", List.of(stage("RAW_INGESTED", "rejected", null, null, "raw message not found", null)));
            return missing;
        }

        Long datasetMessageId = longValue(raw.get("datasetMessageId"));
        Map<String, Object> replay = latestReplayMessage(rawId, datasetMessageId);
        Long replayRunMessageId = longValue(replay.get("replayRunMessageId"));
        Long runId = longValue(replay.get("runId"));
        Map<String, Object> single = singleMessage(replay);
        Map<String, Object> discussion = discussionSegmentFor(rawId, datasetMessageId);
        Map<String, Object> cluster = clusterFor(replay);
        Map<String, Object> material = materialFor(rawId, datasetMessageId, discussion);
        List<Map<String, Object>> traceStages = traceStagesFor(rawId);
        List<Map<String, Object>> providerCalls = providerCallsFor(runId);
        Map<String, Object> llmJudge = providerDecision(providerCalls, List.of("DISCUSSION_SEGMENT_JUDGE", "LLM_CLUSTER_JUDGE_AND_ROUTING", "CLUSTER_JUDGE"));
        Map<String, Object> generation = generation(providerCalls, material);
        String pipelineStatus = pipelineStatus(raw, replay, single, discussion, cluster, material, llmJudge, generation);
        List<String> rejectionReasons = rejectionReasons(single, discussion, llmJudge, generation, traceStages);

        Map<String, Object> detail = ordered();
        detail.put("rawId", rawId);
        detail.put("datasetMessageId", datasetMessageId);
        detail.put("replayRunMessageId", replayRunMessageId);
        detail.put("chatId", raw.get("chatId"));
        detail.put("chatTitle", raw.get("chatTitle"));
        detail.put("topicId", raw.get("topicId"));
        detail.put("threadId", raw.get("threadId"));
        detail.put("messageDate", raw.get("messageDate"));
        detail.put("text", raw.get("text"));
        detail.put("textUnavailableReason", raw.get("textUnavailableReason"));
        detail.put("appMessageUrl", raw.get("appMessageUrl"));
        detail.put("telegramMessageUrl", raw.get("telegramMessageUrl"));
        detail.put("telegramLinkAvailable", raw.get("telegramLinkAvailable"));
        detail.put("telegramLinkReason", raw.get("telegramLinkReason"));
        detail.put("processable", raw.get("processable"));
        detail.put("processingState", raw.get("processingState"));
        detail.put("activeDialog", raw.get("activeDialog"));
        detail.put("autoPipelineEnabled", raw.get("autoPipelineEnabled"));
        detail.put("pipelineStatus", pipelineStatus);
        detail.put("candidateType", candidateType(material, discussion, cluster, single));
        detail.put("material", material.isEmpty() ? null : material);
        detail.put("singleMessage", single);
        detail.put("discussionSegment", discussion.isEmpty() ? null : discussion);
        detail.put("cluster", cluster.isEmpty() ? emptyCluster() : cluster);
        detail.put("llmJudge", llmJudge);
        detail.put("generation", generation);
        detail.put("traceStages", traceStages);
        detail.put("providerCalls", providerCalls);
        detail.put("rejectionReasons", rejectionReasons);
        detail.put("timeline", timeline(raw, replay, single, discussion, material, traceStages, providerCalls, pipelineStatus));
        return detail;
    }

    private Map<String, Object> rawPipelineRow(long rawId) {
        return jdbc.query("""
                SELECT rm.id AS raw_id, rm.account_id, rm.telegram_chat_id, rm.telegram_message_id,
                       rm.telegram_topic_id, rm.message_thread_id, rm.topic_title, rm.message_date, rm.ingested_at,
                       rm.text, rm.caption, rm.chat_title AS raw_chat_title,
                       c.id AS group_id, c.title AS chat_title, c.username AS chat_username, c.type AS chat_type, c.tdlib_chat_type,
                       c.is_enabled, (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL) AS active_dialog,
                       (c.is_enabled = true AND (c.chat_list IN ('MAIN', 'ARCHIVE') OR c.position_order IS NOT NULL)) AS auto_pipeline_enabled,
                       dm.id AS dataset_message_id
                FROM raw_messages rm
                LEFT JOIN telegram_chats c ON c.account_id = rm.account_id AND c.telegram_chat_id = rm.telegram_chat_id
                LEFT JOIN dataset_messages dm ON dm.account_id = rm.account_id
                    AND dm.telegram_chat_id = rm.telegram_chat_id
                    AND dm.telegram_message_id = rm.telegram_message_id
                WHERE rm.id = ?
                ORDER BY dm.id DESC NULLS LAST
                LIMIT 1
                """, rs -> {
            if (!rs.next()) return null;
            String text = preferredText(rs.getString("text"), rs.getString("caption"));
            boolean activeDialog = rs.getBoolean("active_dialog");
            boolean autoPipelineEnabled = rs.getBoolean("auto_pipeline_enabled");
            Long telegramChatId = nullableLong(rs, "telegram_chat_id");
            Long telegramMessageId = nullableLong(rs, "telegram_message_id");
            Map<String, Object> row = ordered();
            row.put("rawId", rs.getLong("raw_id"));
            row.put("datasetMessageId", nullableLong(rs, "dataset_message_id"));
            row.put("accountId", nullableLong(rs, "account_id"));
            row.put("chatId", telegramChatId);
            row.put("chatTitle", coalesce(rs.getString("chat_title"), rs.getString("raw_chat_title")));
            row.put("topicId", nullableLong(rs, "telegram_topic_id"));
            row.put("threadId", nullableLong(rs, "message_thread_id"));
            row.put("topicName", rs.getString("topic_title"));
            row.put("telegramMessageId", telegramMessageId);
            row.put("messageDate", iso(rs.getObject("message_date", OffsetDateTime.class)));
            row.put("ingestedAt", iso(rs.getObject("ingested_at", OffsetDateTime.class)));
            row.put("text", text);
            row.put("textUnavailableReason", text == null || text.isBlank() ? "NO_TEXT" : null);
            row.put("groupId", nullableLong(rs, "group_id"));
            row.put("activeDialog", activeDialog);
            row.put("autoPipelineEnabled", autoPipelineEnabled);
            row.put("processable", activeDialog && autoPipelineEnabled);
            row.put("processingState", activeDialog && autoPipelineEnabled ? "ENABLED_PROCESSABLE" : (activeDialog ? "DISPLAY_ONLY_AUTO_DISABLED" : "DISPLAY_ONLY_NOT_ACTIVE"));
            row.put("appMessageUrl", appMessageUrl(telegramChatId, telegramMessageId, rs.getLong("raw_id")));
            String telegramUrl = telegramUrl(rs.getString("chat_username"), telegramMessageId, rs.getString("chat_type"), rs.getString("tdlib_chat_type"));
            row.put("telegramMessageUrl", telegramUrl);
            row.put("telegramLinkAvailable", telegramUrl != null);
            row.put("telegramLinkReason", telegramLinkReason(rs.getString("chat_username"), telegramMessageId, rs.getString("chat_type"), rs.getString("tdlib_chat_type")));
            return row;
        }, rawId);
    }

    private Map<String, Object> latestReplayMessage(long rawId, Long datasetMessageId) {
        List<Object> args = new ArrayList<>();
        args.add(rawId);
        String datasetClause = "";
        if (datasetMessageId != null) {
            datasetClause = " OR rrm.dataset_message_id = ?";
            args.add(datasetMessageId);
        }
        return jdbc.query("""
                SELECT rrm.id, rrm.run_id, rrm.dataset_message_id, rrm.raw_message_id, rrm.status, rrm.decision,
                       rrm.final_decision, rrm.rule_decision, rrm.scores_json::text AS scores_json,
                       rrm.labels_json::text AS labels_json, rrm.rule_labels_json::text AS rule_labels_json,
                       rrm.single_message_rejection_reason, rrm.single_message_score_breakdown_json::text AS single_breakdown,
                       rrm.knowledge_item_id, rrm.microcluster_id, rrm.macrocluster_id, rrm.llm_used, rrm.llm_skip_reason,
                       rrm.embedding_id, rrm.created_at, rrm.updated_at
                FROM replay_run_messages rrm
                WHERE rrm.raw_message_id = ?%s
                ORDER BY rrm.updated_at DESC NULLS LAST, rrm.id DESC
                LIMIT 1
                """.formatted(datasetClause), rs -> {
            if (!rs.next()) return Map.of();
            Map<String, Object> row = ordered();
            row.put("replayRunMessageId", rs.getLong("id"));
            row.put("runId", rs.getLong("run_id"));
            row.put("datasetMessageId", rs.getLong("dataset_message_id"));
            row.put("rawMessageId", nullableLong(rs, "raw_message_id"));
            row.put("status", rs.getString("status"));
            row.put("decision", coalesce(rs.getString("final_decision"), rs.getString("decision"), rs.getString("rule_decision")));
            row.put("ruleDecision", rs.getString("rule_decision"));
            row.put("scores", parseAny(rs.getString("scores_json")));
            row.put("labels", parseAny(rs.getString("labels_json")));
            row.put("ruleLabels", parseAny(rs.getString("rule_labels_json")));
            row.put("singleMessageRejectionReason", rs.getString("single_message_rejection_reason"));
            row.put("singleMessageScoreBreakdown", parseAny(rs.getString("single_breakdown")));
            row.put("knowledgeItemId", nullableLong(rs, "knowledge_item_id"));
            row.put("microclusterId", nullableLong(rs, "microcluster_id"));
            row.put("macroclusterId", nullableLong(rs, "macrocluster_id"));
            row.put("llmUsed", rs.getBoolean("llm_used"));
            row.put("llmSkipReason", rs.getString("llm_skip_reason"));
            row.put("embeddingId", nullableLong(rs, "embedding_id"));
            row.put("createdAt", iso(rs.getObject("created_at", OffsetDateTime.class)));
            row.put("updatedAt", iso(rs.getObject("updated_at", OffsetDateTime.class)));
            return row;
        }, args.toArray());
    }

    private Map<String, Object> singleMessage(Map<String, Object> replay) {
        Map<String, Object> row = ordered();
        boolean evaluated = !replay.isEmpty();
        String reason = stringValue(replay.get("singleMessageRejectionReason"));
        String decision = stringValue(replay.get("decision"));
        Object score = nested(replay.get("singleMessageScoreBreakdown"), "totalScore");
        if (score == null) score = nested(replay.get("scores"), "singleMessageScore");
        row.put("evaluated", evaluated);
        row.put("score", score);
        row.put("decision", reason != null ? "REJECTED" : decision);
        row.put("reason", reason);
        row.put("signals", replay.getOrDefault("ruleLabels", List.of()));
        return row;
    }

    private Map<String, Object> discussionSegmentFor(long rawId, Long datasetMessageId) {
        List<Object> args = new ArrayList<>();
        args.add(rawId);
        String datasetClause = "";
        if (datasetMessageId != null) {
            datasetClause = " OR dss.dataset_message_id = ?";
            args.add(datasetMessageId);
        }
        return jdbc.query("""
                SELECT ds.id, ds.source_count, ds.combined_score, ds.decision, ds.rejection_reason,
                       ds.proposed_material_type, ds.signals_json::text AS signals_json,
                       ds.suppression_reasons_json::text AS suppression_reasons_json,
                       ds.start_message_date, ds.end_message_date
                FROM discussion_segment_sources dss
                JOIN discussion_segments ds ON ds.id = dss.discussion_segment_id
                WHERE dss.raw_message_id = ?%s
                ORDER BY ds.updated_at DESC, ds.id DESC
                LIMIT 1
                """.formatted(datasetClause), rs -> {
            if (!rs.next()) return Map.of();
            long segmentId = rs.getLong("id");
            Map<String, Object> row = ordered();
            row.put("segmentId", segmentId);
            row.put("sourceCount", rs.getInt("source_count"));
            row.put("score", rs.getBigDecimal("combined_score"));
            row.put("decision", rs.getString("decision"));
            row.put("materialType", rs.getString("proposed_material_type"));
            row.put("signals", parseAny(rs.getString("signals_json")));
            row.put("suppressionReasons", parseAny(rs.getString("suppression_reasons_json")));
            row.put("rejectionReason", rs.getString("rejection_reason"));
            Map<String, Object> timeWindow = ordered();
            timeWindow.put("start", iso(rs.getObject("start_message_date", OffsetDateTime.class)));
            timeWindow.put("end", iso(rs.getObject("end_message_date", OffsetDateTime.class)));
            row.put("timeWindow", timeWindow);
            row.put("sources", discussionSources(segmentId, rawId));
            return row;
        }, args.toArray());
    }

    private List<Map<String, Object>> discussionSources(long segmentId, long selectedRawId) {
        return jdbc.query("""
                SELECT dss.order_index, dss.raw_message_id, dss.dataset_message_id, dss.replay_run_message_id,
                       dss.message_date, dss.role, dss.text_preview
                FROM discussion_segment_sources dss
                WHERE dss.discussion_segment_id = ?
                ORDER BY dss.order_index
                """, (rs, rowNum) -> {
            Map<String, Object> row = ordered();
            Long rawId = nullableLong(rs, "raw_message_id");
            row.put("orderIndex", rs.getInt("order_index"));
            row.put("rawId", rawId);
            row.put("datasetMessageId", nullableLong(rs, "dataset_message_id"));
            row.put("replayRunMessageId", nullableLong(rs, "replay_run_message_id"));
            row.put("messageDate", iso(rs.getObject("message_date", OffsetDateTime.class)));
            row.put("role", rs.getString("role"));
            row.put("text", rs.getString("text_preview"));
            row.put("selected", rawId != null && rawId == selectedRawId);
            return row;
        }, segmentId);
    }

    private Map<String, Object> clusterFor(Map<String, Object> replay) {
        Long macroId = longValue(replay.get("macroclusterId"));
        Long microId = longValue(replay.get("microclusterId"));
        if (macroId == null && microId == null) return Map.of();
        Map<String, Object> row = ordered();
        row.put("clusterId", macroId != null ? macroId : microId);
        row.put("clusterType", macroId != null ? "MACRO_CLUSTER" : "CLUSTER");
        row.put("sourceCount", 0);
        row.put("decision", replay.get("decision"));
        row.put("reason", replay.get("llmSkipReason"));
        return row;
    }

    private Map<String, Object> materialFor(long rawId, Long datasetMessageId, Map<String, Object> discussion) {
        Long segmentId = longValue(discussion.get("segmentId"));
        List<Object> args = new ArrayList<>();
        args.add(rawId);
        String condition = "rm.id = ?";
        if (datasetMessageId != null) {
            condition += " OR kis.dataset_message_id = ?";
            args.add(datasetMessageId);
        }
        if (segmentId != null) {
            condition += " OR (ki.source_cluster_type = 'DISCUSSION_SEGMENT' AND ki.source_cluster_id = ?)";
            args.add(segmentId);
        }
        return jdbc.query("""
                SELECT DISTINCT ki.id, ki.title, COALESCE(ki.artifact_type, ki.item_type) AS type, ki.status
                FROM knowledge_items ki
                LEFT JOIN knowledge_item_sources kis ON kis.knowledge_item_id = ki.id
                LEFT JOIN dataset_messages dm ON dm.id = kis.dataset_message_id
                LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
                WHERE ki.deleted_at IS NULL AND (%s)
                ORDER BY ki.id DESC
                LIMIT 1
                """.formatted(condition), rs -> {
            if (!rs.next()) return Map.of();
            long materialId = rs.getLong("id");
            Map<String, Object> row = ordered();
            row.put("materialId", materialId);
            row.put("title", rs.getString("title"));
            row.put("type", rs.getString("type"));
            row.put("status", rs.getString("status"));
            row.put("url", "/materials/" + materialId);
            return row;
        }, args.toArray());
    }

    private List<Map<String, Object>> traceStagesFor(long rawId) {
        return jdbc.query("""
                SELECT stage_id, stage_name, status, error_code, error_message, started_at, finished_at, duration_ms, created_at
                FROM pipeline_message_trace
                WHERE raw_message_id = ?
                ORDER BY created_at, id
                """, (rs, rowNum) -> stage(
                coalesce(rs.getString("stage_id"), rs.getString("stage_name")),
                timelineStatus(rs.getString("status")),
                iso(rs.getObject("created_at", OffsetDateTime.class)),
                nullableLong(rs, "duration_ms"),
                coalesce(rs.getString("error_code"), rs.getString("error_message")),
                null
        ), rawId);
    }

    private List<Map<String, Object>> providerCallsFor(Long runId) {
        if (runId == null) return List.of();
        return jdbc.query("""
                SELECT id, stage, model_name, status, latency_ms, error_code, error_message,
                       request_preview, response_preview, response_json::text AS response_json, created_at
                FROM provider_calls
                WHERE run_id = ?
                ORDER BY id
                """, (rs, rowNum) -> {
            Map<String, Object> row = ordered();
            row.put("providerCallId", rs.getLong("id"));
            row.put("stage", rs.getString("stage"));
            row.put("provider", "ModelHub");
            row.put("model", rs.getString("model_name"));
            row.put("status", rs.getString("status"));
            row.put("durationMs", nullableLong(rs, "latency_ms"));
            row.put("promptPreview", redactSensitive(rs.getString("request_preview")));
            row.put("responsePreview", redactSensitive(rs.getString("response_preview")));
            row.put("parsedDecision", parsedDecision(rs.getString("response_json")));
            row.put("error", coalesce(rs.getString("error_code"), rs.getString("error_message")));
            row.put("createdAt", iso(rs.getObject("created_at", OffsetDateTime.class)));
            return row;
        }, runId);
    }

    private Map<String, Object> providerDecision(List<Map<String, Object>> calls, List<String> stages) {
        return calls.stream()
                .filter(call -> stages.contains(stringValue(call.get("stage"))))
                .reduce((first, second) -> second)
                .map(call -> {
                    Map<String, Object> row = ordered();
                    row.put("called", true);
                    row.put("providerCallId", call.get("providerCallId"));
                    row.put("decision", nested(call.get("parsedDecision"), "decision"));
                    row.put("confidence", nested(call.get("parsedDecision"), "confidence"));
                    row.put("reason", coalesce(stringValue(nested(call.get("parsedDecision"), "reason")), stringValue(call.get("error"))));
                    return row;
                })
                .orElseGet(() -> {
                    Map<String, Object> row = ordered();
                    row.put("called", false);
                    row.put("providerCallId", null);
                    row.put("decision", null);
                    row.put("confidence", null);
                    row.put("reason", null);
                    return row;
                });
    }

    private Map<String, Object> generation(List<Map<String, Object>> calls, Map<String, Object> material) {
        Map<String, Object> call = calls.stream().filter(c -> "KNOWLEDGE_GENERATION".equals(c.get("stage"))).reduce((a, b) -> b).orElse(null);
        Map<String, Object> row = ordered();
        row.put("called", call != null);
        row.put("providerCallId", call == null ? null : call.get("providerCallId"));
        row.put("status", call == null ? null : call.get("status"));
        row.put("materialId", material.get("materialId"));
        row.put("skipReason", call == null && material.isEmpty() ? "GENERATION_NOT_CALLED" : null);
        return row;
    }

    private String pipelineStatus(Map<String, Object> raw, Map<String, Object> replay, Map<String, Object> single, Map<String, Object> discussion, Map<String, Object> cluster, Map<String, Object> material, Map<String, Object> llmJudge, Map<String, Object> generation) {
        if (!material.isEmpty()) return "MATERIAL_CREATED";
        if ("SUCCESS".equals(generation.get("status")) && material.isEmpty()) return "GENERATION_SKIPPED";
        if (Boolean.TRUE.equals(llmJudge.get("called")) && stringValue(llmJudge.get("decision")) != null && stringValue(llmJudge.get("decision")).contains("REJECT")) return "LLM_JUDGE_REJECTED";
        if (!discussion.isEmpty() && discussion.get("rejectionReason") != null) return "DISCUSSION_SEGMENT_REJECTED";
        if (!discussion.isEmpty()) return "DISCUSSION_SEGMENT_MEMBER";
        if (!cluster.isEmpty()) return "CLUSTER_MEMBER";
        if (single.get("reason") != null) return "SINGLE_MESSAGE_REJECTED";
        if (single.get("decision") != null && stringValue(single.get("decision")).contains("CANDIDATE")) return "SINGLE_MESSAGE_CANDIDATE";
        if (replay.get("embeddingId") != null) return "EMBEDDED";
        if (!replay.isEmpty()) return "DATASET_CREATED";
        if (raw.get("rawId") != null) return "INGESTED_ONLY";
        return "NOT_INGESTED";
    }

    private List<String> rejectionReasons(Map<String, Object> single, Map<String, Object> discussion, Map<String, Object> llmJudge, Map<String, Object> generation, List<Map<String, Object>> traceStages) {
        List<String> reasons = new ArrayList<>();
        addIfPresent(reasons, stringValue(single.get("reason")));
        addIfPresent(reasons, stringValue(discussion.get("rejectionReason")));
        addIfPresent(reasons, stringValue(llmJudge.get("reason")));
        addIfPresent(reasons, stringValue(generation.get("skipReason")));
        for (Map<String, Object> stage : traceStages) addIfPresent(reasons, stringValue(stage.get("reason")));
        return reasons.stream().distinct().toList();
    }

    private List<Map<String, Object>> timeline(Map<String, Object> raw, Map<String, Object> replay, Map<String, Object> single, Map<String, Object> discussion, Map<String, Object> material, List<Map<String, Object>> traceStages, List<Map<String, Object>> providerCalls, String finalStatus) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(stage("RAW_INGESTED", raw.get("rawId") == null ? "skipped" : "success", stringValue(raw.get("ingestedAt")), null, null, null));
        rows.add(stage("DATASET_MESSAGE_CREATED", raw.get("datasetMessageId") == null ? "skipped" : "success", null, null, null, null));
        rows.add(stage("EMBEDDING_CREATED", replay.get("embeddingId") == null ? "skipped" : "success", null, null, null, null));
        rows.add(stage("SINGLE_MESSAGE_SCORING", single.get("reason") == null ? "success" : "rejected", null, null, stringValue(single.get("reason")), single.get("score")));
        if (!discussion.isEmpty()) {
            rows.add(stage("DISCUSSION_SEGMENT_WINDOW_FORMED", "success", null, null, null, discussion.get("score")));
            rows.add(stage("DISCUSSION_SEGMENT_SCORING", discussion.get("rejectionReason") == null ? "success" : "rejected", null, null, stringValue(discussion.get("rejectionReason")), discussion.get("score")));
            rows.add(stage("DISCUSSION_SEGMENT_DEDUPE", "success", null, null, null, null));
        }
        if (providerCalls.stream().anyMatch(c -> stringValue(c.get("stage")).contains("JUDGE"))) rows.add(stage("LLM_JUDGE", finalStatus.contains("REJECTED") ? "rejected" : "success", null, null, null, null));
        if (providerCalls.stream().anyMatch(c -> "KNOWLEDGE_GENERATION".equals(c.get("stage")))) rows.add(stage("KNOWLEDGE_GENERATION", material.isEmpty() ? "skipped" : "success", null, null, null, null));
        rows.add(stage(finalStatus, "MATERIAL_CREATED".equals(finalStatus) ? "success" : (finalStatus.contains("REJECT") ? "rejected" : "skipped"), null, null, null, null));
        if (!traceStages.isEmpty()) rows.addAll(traceStages);
        return rows;
    }

    private Map<String, Object> stage(String name, String status, String timestamp, Long durationMs, String reason, Object score) {
        Map<String, Object> row = ordered();
        row.put("stage", name);
        row.put("status", status);
        row.put("timestamp", timestamp);
        row.put("durationMs", durationMs == null || durationMs <= 0 ? null : durationMs);
        row.put("durationLabel", durationMs == null || durationMs <= 0 ? "нет данных" : durationMs + " мс");
        row.put("reason", reason);
        row.put("score", score);
        return row;
    }

    private Map<String, Object> emptyCluster() {
        Map<String, Object> row = ordered();
        row.put("clusterId", null);
        row.put("clusterType", null);
        row.put("sourceCount", 0);
        row.put("decision", null);
        row.put("reason", null);
        return row;
    }

    private String candidateType(Map<String, Object> material, Map<String, Object> discussion, Map<String, Object> cluster, Map<String, Object> single) {
        if (!discussion.isEmpty()) return "DISCUSSION_SEGMENT";
        if (!cluster.isEmpty()) return stringValue(cluster.get("clusterType"));
        if (single.get("decision") != null || !material.isEmpty()) return "SINGLE_MESSAGE";
        return null;
    }

    private Object parsedDecision(String json) {
        JsonNode node = readJson(json);
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        Map<String, Object> row = ordered();
        for (String field : List.of("decision", "accepted", "confidence", "reason", "recommendation", "materialType", "title")) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) row.put(field, value.isNumber() ? value.decimalValue() : value.asText());
        }
        return row.isEmpty() ? null : row;
    }

    private Object parseAny(String value) {
        JsonNode node = readJson(value);
        if (node == null) return value;
        return objectMapper.convertValue(node, Object.class);
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object nested(Object object, String key) {
        if (object instanceof Map<?, ?> map) return map.get(key);
        return null;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && !text.isBlank()) {
            try { return Long.parseLong(text); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }

    private Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }

    private String coalesce(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String iso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private String timelineStatus(String status) {
        if (status == null) return "skipped";
        return switch (status) {
            case "PROCESSED", "PROCESSED_DEGRADED", "COMPLETED" -> "success";
            case "FAILED" -> "error";
            case "SKIPPED" -> "skipped";
            default -> "processing";
        };
    }

    private String appMessageUrl(Long telegramChatId, Long telegramMessageId, Long rawId) {
        List<String> parts = new ArrayList<>();
        if (telegramChatId != null) parts.add("chatId=" + telegramChatId);
        if (telegramMessageId != null) parts.add("messageId=" + telegramMessageId);
        if (rawId != null) parts.add("rawId=" + rawId);
        return parts.isEmpty() ? "/groups" : "/groups?" + String.join("&", parts);
    }

    private String telegramUrl(String username, Long telegramMessageId, String chatType, String tdlibChatType) {
        if (username == null || username.isBlank() || telegramMessageId == null) return null;
        if ("DIRECT_CHAT".equals(chatType) || "PRIVATE".equals(tdlibChatType)) return null;
        String clean = username.startsWith("@") ? username.substring(1) : username;
        return clean.isBlank() ? null : "https://t.me/" + clean + "/" + telegramMessageId;
    }

    private String telegramLinkReason(String username, Long telegramMessageId, String chatType, String tdlibChatType) {
        if (telegramUrl(username, telegramMessageId, chatType, tdlibChatType) != null) return null;
        if ("DIRECT_CHAT".equals(chatType) || "PRIVATE".equals(tdlibChatType)) return "Telegram-ссылка недоступна для приватного чата";
        if (username == null || username.isBlank()) return "Telegram-ссылка недоступна: у чата нет публичного username";
        if (telegramMessageId == null) return "Telegram-ссылка недоступна: нет telegram_message_id";
        return "Telegram-ссылка недоступна";
    }

    private String redactSensitive(String value) {
        if (value == null) return null;
        return value
                .replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s\\\",}]+", "$1[REDACTED]")
                .replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)[^\\s\\\",}]+", "$1[REDACTED]")
                .replaceAll("(?i)(token\\s*[:=]\\s*)[^\\s\\\",}]+", "$1[REDACTED]")
                .replaceAll("(?i)(password\\s*[:=]\\s*)[^\\s\\\",}]+", "$1[REDACTED]")
                .replaceAll("(?i)(session\\s*[:=]\\s*)[^\\s\\\",}]+", "$1[REDACTED]");
    }

    public List<MessageLinkDto> linksForMessage(long messageId) {
        return jdbc.query("""
                SELECT id,
                       message_id,
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
                       raw_entity_json::text AS raw_entity_json,
                       created_at
                FROM message_links
                WHERE message_id = ?
                ORDER BY id
                """, linkMapper(), messageId);
    }

    public PipelineStatusDto pipelineStatus() {
        long messages = count("raw_messages");
        long links = count("message_links");
        long pending = jdbc.queryForObject("SELECT count(*) FROM tdlib_update_inbox WHERE processing_status = 'PENDING'", Long.class);
        long events = count("pipeline_events");
        return new PipelineStatusDto(messages, links, pending, events, "LOCAL_STAGE1");
    }

    public void recordChatSynced(long accountId, int syncedCount) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "CHAT_SYNCED");
        payload.put("accountId", accountId);
        payload.put("synced", syncedCount);
        writePipelineEvent("CHAT_SYNCED", "telegram_chats", null, payload);
    }

    public List<TelegramChatDto> telegramChats(long accountId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return jdbc.query("""
                SELECT telegram_chat_id, title, username, type, is_forum, last_message_id
                FROM telegram_chats
                WHERE account_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> new TelegramChatDto(
                rs.getLong("telegram_chat_id"),
                rs.getString("title"),
                rs.getString("username"),
                rs.getString("type"),
                rs.getString("type"),
                rs.getBoolean("is_forum"),
                nullableLong(rs, "last_message_id") == null ? 0L : nullableLong(rs, "last_message_id")
        ), accountId, safeLimit);
    }

    public long telegramChatCount(long accountId) {
        return jdbc.queryForObject("SELECT count(*) FROM telegram_chats WHERE account_id = ?", Long.class, accountId);
    }

    public Long latestTelegramChatId(long accountId) {
        return jdbc.query("""
                SELECT telegram_chat_id
                FROM telegram_chats
                WHERE account_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong("telegram_chat_id") : null, accountId);
    }

    public ChatPreflightDto chatPreflight(long accountId, long telegramChatId) {
        List<ChatPreflightDto> rows = jdbc.query("""
                SELECT true AS chat_exists, is_enabled
                FROM telegram_chats
                WHERE account_id = ? AND telegram_chat_id = ?
                LIMIT 1
                """, (rs, rowNum) -> new ChatPreflightDto(true, rs.getBoolean("is_enabled")), accountId, telegramChatId);
        return rows.isEmpty() ? new ChatPreflightDto(false, false) : rows.get(0);
    }

    public boolean isActiveTdlibDialog(long accountId, long telegramChatId) {
        Boolean active = jdbc.query("""
                SELECT EXISTS (
                    SELECT 1
                    FROM telegram_chats
                    WHERE account_id = ?
                      AND telegram_chat_id = ?
                      AND (chat_list IN ('MAIN', 'ARCHIVE') OR position_order IS NOT NULL)
                )
                """, rs -> rs.next() && rs.getBoolean(1), accountId, telegramChatId);
        return Boolean.TRUE.equals(active);
    }

    public void recordChatSyncEvent(String eventType, long accountId, String syncId, Integer batchNo, Integer requestedLimit, Integer receivedCount, Integer storedCount, Long durationMs, String errorSummary) {
        recordChatSyncEvent(eventType, accountId, syncId, batchNo, requestedLimit, receivedCount, storedCount, durationMs, errorSummary, null, null);
    }

    public void recordChatSyncEvent(String eventType, long accountId, String syncId, Integer batchNo, Integer requestedLimit, Integer receivedCount, Integer storedCount, Long durationMs, String errorSummary, Boolean usableCache, String sourceOfTruth) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("syncId", syncId);
        if (batchNo != null) {
            payload.put("batchNo", batchNo);
        }
        if (requestedLimit != null) {
            payload.put("requestedLimit", requestedLimit);
        }
        if (receivedCount != null) {
            payload.put("receivedCount", receivedCount);
        }
        if (storedCount != null) {
            payload.put("storedCount", storedCount);
        }
        if (errorSummary != null) {
            payload.put("errorSummary", truncate(errorSummary, 300));
        }
        if (usableCache != null) {
            payload.put("usableCache", usableCache);
        }
        if (sourceOfTruth != null) {
            payload.put("sourceOfTruth", sourceOfTruth);
        }
        recordRuntimeEvent(eventType, accountId, null, null, syncId, durationMs, payload);
    }

    public void recordBackfillStarted(long accountId, long telegramChatId, long groupId, int limit) {
        updateBackfillStatus(accountId, telegramChatId, "STARTED");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "BACKFILL_STARTED");
        payload.put("accountId", accountId);
        payload.put("telegramChatId", telegramChatId);
        payload.put("groupId", groupId);
        payload.put("limit", limit);
        writePipelineEvent("BACKFILL_STARTED", "telegram_chats", groupId, payload);
    }

    public void recordBackfillCompleted(long accountId, long telegramChatId, long groupId, int persisted) {
        updateBackfillStatus(accountId, telegramChatId, "COMPLETED");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "BACKFILL_COMPLETED");
        payload.put("accountId", accountId);
        payload.put("telegramChatId", telegramChatId);
        payload.put("groupId", groupId);
        payload.put("persisted", persisted);
        writePipelineEvent("BACKFILL_COMPLETED", "telegram_chats", groupId, payload);
    }

    public void recordBackfillFailed(long accountId, long telegramChatId, long groupId, String errorSummary) {
        updateBackfillStatus(accountId, telegramChatId, "FAILED");
        String safeSummary = truncate(errorSummary, 300);
        String status = safeSummary != null && safeSummary.toUpperCase(Locale.ROOT).contains("FLOOD_WAIT")
                ? "FLOOD_WAIT"
                : "DEGRADED";
        jdbc.update("""
                INSERT INTO telegram_account_health (account_id, status, error_summary, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (account_id)
                DO UPDATE SET status = EXCLUDED.status, error_summary = EXCLUDED.error_summary, updated_at = now()
                """, accountId, status, safeSummary);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "BACKFILL_FAILED");
        payload.put("accountId", accountId);
        payload.put("telegramChatId", telegramChatId);
        payload.put("groupId", groupId);
        payload.put("error", safeSummary);
        writePipelineEvent("BACKFILL_FAILED", "telegram_chats", groupId, payload);
    }

    public List<AccountHealthDto> telegramHealth() {
        return jdbc.query("""
                SELECT a.id AS account_id,
                       COALESCE(h.status, a.status) AS status,
                       h.last_update_received_at,
                       h.last_message_persisted_at,
                       COALESCE(h.inbox_pending_count, 0) AS inbox_pending_count,
                       COALESCE(h.reconnect_count, 0) AS reconnect_count,
                       COALESCE(h.dropped_update_count, 0) AS dropped_update_count,
                       COALESCE(h.duplicate_update_count, 0) AS duplicate_update_count,
                       h.flood_wait_until,
                       h.error_summary,
                       a.tdlib_database_path,
                       COALESCE(h.updated_at, a.updated_at) AS updated_at
                FROM telegram_accounts a
                LEFT JOIN telegram_account_health h ON h.account_id = a.id
                ORDER BY a.id
                """, accountHealthMapper());
    }

    public AccountHealthDto accountHealth(long accountId) {
        return jdbc.queryForObject("""
                SELECT a.id AS account_id,
                       COALESCE(h.status, a.status) AS status,
                       h.last_update_received_at,
                       h.last_message_persisted_at,
                       COALESCE(h.inbox_pending_count, 0) AS inbox_pending_count,
                       COALESCE(h.reconnect_count, 0) AS reconnect_count,
                       COALESCE(h.dropped_update_count, 0) AS dropped_update_count,
                       COALESCE(h.duplicate_update_count, 0) AS duplicate_update_count,
                       h.flood_wait_until,
                       h.error_summary,
                       a.tdlib_database_path,
                       COALESCE(h.updated_at, a.updated_at) AS updated_at
                FROM telegram_accounts a
                LEFT JOIN telegram_account_health h ON h.account_id = a.id
                WHERE a.id = ?
                """, accountHealthMapper(), accountId);
    }

    public OperatorStatusDto operatorStatus() {
        List<AccountHealthDto> health = telegramHealth();
        AccountHealthDto first = primaryAccount(health);
        return new OperatorStatusDto(
                "UP",
                "UP",
                first == null ? "AUTH_REQUIRED" : first.status(),
                first == null ? null : first.accountId(),
                first == null ? null : first.lastUpdateReceivedAt(),
                first == null ? null : first.lastMessagePersistedAt(),
                count("raw_messages"),
                count("pipeline_events"),
                count("replay_runs"),
                recentErrors(10),
                replayRunsRecent(10)
        );
    }

    private AccountHealthDto primaryAccount(List<AccountHealthDto> health) {
        if (health.isEmpty()) {
            return null;
        }
        return health.stream()
                .min((left, right) -> {
                    int status = Integer.compare(primaryRank(left.status()), primaryRank(right.status()));
                    if (status != 0) {
                        return status;
                    }
                    return Long.compare(left.accountId(), right.accountId());
                })
                .orElse(health.get(0));
    }

    private int primaryRank(String status) {
        if ("CONNECTED".equals(status) || "READY".equals(status)) {
            return 0;
        }
        if ("WAIT_CODE".equals(status) || "WAIT_PASSWORD".equals(status)) {
            return 1;
        }
        if ("WAIT_PHONE".equals(status)) {
            return 2;
        }
        if ("CONNECTING".equals(status)) {
            return 3;
        }
        return 4;
    }

    public TelegramDiagnosticsDbDto telegramDiagnosticsDb() {
        return new TelegramDiagnosticsDbDto(
                count("telegram_accounts"),
                jdbc.query("SELECT id, status, updated_at FROM telegram_accounts ORDER BY id", (rs, rowNum) -> new AccountAuthStateDto(
                        rs.getLong("id"),
                        rs.getString("status"),
                        iso(rs, "updated_at")
                )),
                scalarIso("SELECT max(updated_at) FROM telegram_accounts"),
                scalarIso("SELECT max(received_at) FROM tdlib_update_inbox"),
                scalarIso("SELECT max(received_at) FROM tdlib_update_inbox WHERE update_type = 'updateNewMessage'"),
                scalarLong("SELECT count(*) FROM tdlib_update_inbox WHERE processing_status = 'PENDING'"),
                scalarLong("SELECT count(*) FROM tdlib_update_inbox WHERE processing_status IN ('PERSISTED', 'PROCESSED')"),
                scalarLong("SELECT count(*) FROM tdlib_update_inbox WHERE processing_status = 'ERROR' OR error IS NOT NULL"),
                count("raw_messages"),
                scalarIso("SELECT max(ingested_at) FROM raw_messages"),
                scalarString("SELECT error_summary FROM telegram_account_health WHERE error_summary IS NOT NULL ORDER BY updated_at DESC LIMIT 1"),
                scalarString("SELECT status FROM telegram_account_health WHERE status = 'FLOOD_WAIT' ORDER BY updated_at DESC LIMIT 1"),
                scalarString("SELECT backfill_status FROM telegram_chat_health WHERE backfill_status IS NOT NULL ORDER BY updated_at DESC LIMIT 1"),
                scalarIso("SELECT max(created_at) FROM telegram_runtime_events WHERE event_type IN ('UPDATE_RECEIVED', 'RAW_MESSAGE_STORED', 'MESSAGE_INGESTED_EVENT_CREATED')")
        );
    }

    public List<TelegramRuntimeLogDto> recentTelegramLogs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return jdbc.query("""
                SELECT created_at, level, account_id, event_type, message, error_code, error_summary, correlation_id
                FROM telegram_runtime_errors
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> new TelegramRuntimeLogDto(
                iso(rs, "created_at"),
                rs.getString("level"),
                nullableLong(rs, "account_id"),
                rs.getString("event_type"),
                rs.getString("message"),
                rs.getString("error_code"),
                rs.getString("error_summary"),
                rs.getString("correlation_id")
        ), safeLimit);
    }

    public List<TelegramRuntimeEventDto> recentTelegramEvents(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return jdbc.query("""
                SELECT created_at, event_type, account_id, telegram_chat_id, telegram_message_id, correlation_id, update_id, duration_ms, payload::text AS payload
                FROM telegram_runtime_events
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> new TelegramRuntimeEventDto(
                iso(rs, "created_at"),
                rs.getString("event_type"),
                nullableLong(rs, "account_id"),
                nullableLong(rs, "telegram_chat_id"),
                nullableLong(rs, "telegram_message_id"),
                rs.getString("correlation_id"),
                rs.getString("update_id"),
                nullableLong(rs, "duration_ms"),
                rs.getString("payload")
        ), safeLimit);
    }

    public List<MessageDto> recentRawMessages(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return jdbc.query("""
                SELECT m.*,
                       c.id AS group_id,
                       c.title AS group_title,
                       c.username AS group_username,
                       (
                           SELECT count(*)
                           FROM raw_messages r
                           WHERE r.account_id = m.account_id
                             AND r.telegram_chat_id = m.telegram_chat_id
                             AND r.reply_to_message_id = m.telegram_message_id
                       ) AS reply_count,
                       t.title AS topic_name
                FROM raw_messages m
                JOIN telegram_chats c ON c.account_id = m.account_id AND c.telegram_chat_id = m.telegram_chat_id
                LEFT JOIN telegram_topics t ON t.account_id = m.account_id
                    AND t.telegram_chat_id = m.telegram_chat_id
                    AND t.telegram_topic_id = m.telegram_topic_id
                ORDER BY m.ingested_at DESC, m.id DESC
                LIMIT ?
                """, messageMapper(), safeLimit);
    }

    public List<PipelineEventDto> recentPipelineEvents(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return jdbc.query("""
                SELECT id, event_type, entity_type, entity_id, payload::text AS payload, created_at
                FROM pipeline_events
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> new PipelineEventDto(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getString("entity_type"),
                nullableLong(rs, "entity_id"),
                rs.getString("payload"),
                iso(rs, "created_at")
        ), safeLimit);
    }

    public void recordRuntimeEvent(String eventType, Long accountId, Long telegramChatId, Long telegramMessageId, String correlationId, Long durationMs, ObjectNode payload) {
        jdbc.update("""
                INSERT INTO telegram_runtime_events (event_type, account_id, telegram_chat_id, telegram_message_id, correlation_id, duration_ms, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                """, eventType, accountId, telegramChatId, telegramMessageId, correlationId, durationMs, json(payload == null ? objectMapper.createObjectNode() : payload));
    }

    public void recordRuntimeError(String level, Long accountId, Long telegramChatId, Long telegramMessageId, String eventType, String message, String errorCode, String errorSummary, String correlationId) {
        jdbc.update("""
                INSERT INTO telegram_runtime_errors (level, account_id, telegram_chat_id, telegram_message_id, event_type, message, error_code, error_summary, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, level == null ? "ERROR" : level, accountId, telegramChatId, telegramMessageId, eventType, truncate(message, 500), errorCode, truncate(errorSummary, 500), correlationId);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private long scalarLong(String sql) {
        Long value = jdbc.query(sql, rs -> rs.next() ? rs.getLong(1) : null);
        return value == null ? 0L : value;
    }

    private String scalarString(String sql) {
        return jdbc.query(sql, rs -> rs.next() ? rs.getString(1) : null);
    }

    private String scalarIso(String sql) {
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            OffsetDateTime value = rs.getObject(1, OffsetDateTime.class);
            return value == null ? null : value.toString();
        });
    }

    private List<TelegramRuntimeLogDto> recentErrors(int limit) {
        return recentTelegramLogs(limit);
    }

    private List<ReplayRunSummaryDto> replayRunsRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.query("""
                SELECT id, run_name, status, started_at, finished_at, error
                FROM replay_runs
                ORDER BY started_at DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> new ReplayRunSummaryDto(
                rs.getLong("id"),
                rs.getString("run_name"),
                rs.getString("status"),
                iso(rs, "started_at"),
                iso(rs, "finished_at"),
                rs.getString("error")
        ), safeLimit);
    }

    private void updateBackfillStatus(long accountId, long telegramChatId, String status) {
        jdbc.update("""
                INSERT INTO telegram_chat_health (
                    account_id,
                    telegram_chat_id,
                    last_backfill_at,
                    backfill_status,
                    updated_at
                )
                VALUES (?, ?, now(), ?, now())
                ON CONFLICT (account_id, telegram_chat_id)
                DO UPDATE SET last_backfill_at = now(), backfill_status = ?, updated_at = now()
                """, accountId, telegramChatId, status, status);
    }

    private void writePipelineEvent(String eventType, String entityType, Long entityId, ObjectNode payload) {
        jdbc.update("""
                INSERT INTO pipeline_events (event_type, entity_type, entity_id, payload)
                VALUES (?, ?, ?, ?::jsonb)
                """, eventType, entityType, entityId, json(payload));
    }

    private void ensureAccountExists(long accountId) {
        jdbc.update("""
                INSERT INTO telegram_accounts (id, name, status)
                VALUES (?, ?, 'AUTH_REQUIRED')
                ON CONFLICT (id) DO NOTHING
                """, accountId, "Telegram account " + accountId);
    }

    private String json(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize pipeline event", error);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private RowMapper<AccountDto> accountMapper() {
        return (rs, rowNum) -> {
            String fullPhone = phonePrivacyService.decrypt(rs.getString("phone_encrypted"));
            return new AccountDto(
                    rs.getLong("id"),
                    fullPhone != null ? fullPhone : rs.getString("phone_masked"),
                    null,
                    null,
                    rs.getString("name"),
                    null,
                    mapFrontendStatus(rs.getString("status")),
                    null,
                    rs.getLong("groups_count"),
                    0L,
                    iso(rs, "created_at")
            );
        };
    }

    private RowMapper<GroupDto> groupMapper() {
        return (rs, rowNum) -> {
            long rawMessagesTotal = rs.getLong("raw_messages_total");
            long rawMessagesLast24h = rs.getLong("raw_messages_last24h");
            boolean activeDialog = rs.getBoolean("active_dialog");
            boolean autoPipelineEnabled = rs.getBoolean("auto_pipeline_enabled");
            return new GroupDto(
                rs.getLong("id"),
                rs.getLong("telegram_chat_id"),
                rs.getString("title"),
                rs.getString("username"),
                sourceType(rs.getString("type")),
                null,
                rs.getBoolean("is_forum"),
                rs.getBoolean("is_enabled"),
                rs.getLong("account_id"),
                rawMessagesLast24h,
                0L,
                iso(rs, "last_message_date"),
                nullableLong(rs, "last_message_id"),
                rawMessagesTotal,
                rawMessagesLast24h,
                iso(rs, "latest_raw_message_at"),
                rs.getLong("topics_count"),
                autoPipelineEnabled,
                activeDialog,
                rawMessagesTotal > 0 ? "HISTORY_AVAILABLE" : "KNOWN_EMPTY",
                activeDialog && autoPipelineEnabled ? "ENABLED_PROCESSABLE" : (activeDialog ? "DISPLAY_ONLY_AUTO_DISABLED" : "DISPLAY_ONLY_NOT_ACTIVE"),
                rs.getString("source")
            );
        };
    }

    private RowMapper<MessageDto> messageMapper() {
        return (rs, rowNum) -> {
            long id = rs.getLong("id");
            List<MessageLinkDto> links = linksForMessage(id);
            String intelligenceJson = messageIntelligenceJson(rs, links);
            MediaSummary media = mediaSummary(id, rs.getString("caption"));
            return new MessageDto(
                    id,
                    rs.getLong("telegram_message_id"),
                    rs.getLong("group_id"),
                    rs.getString("group_title"),
                    nullableLong(rs, "telegram_chat_id"),
                    nullableLong(rs, "sender_id"),
                    rs.getString("sender_name"),
                    rs.getString("sender_username"),
                    rs.getBoolean("sender_is_bot"),
                    preferredText(rs.getString("text"), rs.getString("caption")),
                    rs.getString("caption"),
                    nullableLong(rs, "reply_to_message_id"),
                    rs.getLong("reply_count"),
                    "UNPROCESSED",
                    null,
                    rs.getString("topic_name"),
                    nullableLong(rs, "telegram_topic_id"),
                    telegramMessageUrl(rs.getString("group_username"), rs.getLong("telegram_message_id")),
                    iso(rs, "message_date"),
                    rs.getString("content_type"),
                    rs.getBoolean("has_media") || media.hasMedia(),
                    rs.getBoolean("has_links"),
                    media.mediaType(),
                    media.hasVoice(),
                    media.hasGif(),
                    media.hasDocument(),
                    media.hasPhoto(),
                    media.hasVideo(),
                    media.fileName(),
                    media.mimeType(),
                    media.durationSeconds(),
                    media.mediaJson(),
                    links,
                    intelligenceJson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        };
    }

    private RowMapper<MessageLinkDto> linkMapper() {
        return (rs, rowNum) -> new MessageLinkDto(
                rs.getLong("id"),
                rs.getLong("message_id"),
                rs.getString("url"),
                rs.getString("normalized_url"),
                rs.getString("domain"),
                rs.getString("anchor_text"),
                rs.getString("source"),
                rs.getString("entity_type"),
                nullableInt(rs, "offset_start"),
                nullableInt(rs, "offset_end"),
                rs.getBoolean("is_hidden"),
                rs.getBoolean("is_visible_url"),
                rs.getBoolean("is_telegram_link"),
                rs.getBoolean("is_referral_like"),
                rs.getString("raw_entity_json"),
                iso(rs, "created_at")
        );
    }

    private MediaSummary mediaSummary(long messageId, String caption) {
        List<MediaSummary> rows = jdbc.query("""
                SELECT media_type,
                       mime_type,
                       file_name,
                       raw_media_json::text AS raw_media_json,
                       COALESCE(raw_media_json->>'duration', raw_media_json->>'durationSeconds') AS duration_seconds
                FROM raw_message_media
                WHERE message_id = ?
                ORDER BY id
                LIMIT 1
                """, (rs, rowNum) -> {
            String mediaType = rs.getString("media_type");
            String normalized = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT);
            String mimeType = rs.getString("mime_type");
            return new MediaSummary(
                    true,
                    mediaType,
                    normalized.contains("voice"),
                    normalized.contains("animation") || normalized.contains("gif") || (mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("gif")),
                    normalized.contains("document") || normalized.contains("file"),
                    normalized.contains("photo") || normalized.contains("image"),
                    normalized.contains("video"),
                    rs.getString("file_name"),
                    mimeType,
                    nullableInt(rs, "duration_seconds"),
                    rs.getString("raw_media_json")
            );
        }, messageId);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        return new MediaSummary(false, null, false, false, false, false, false, null, null, null, null);
    }

    private RowMapper<AccountHealthDto> accountHealthMapper() {
        return (rs, rowNum) -> new AccountHealthDto(
                rs.getLong("account_id"),
                rs.getString("status"),
                iso(rs, "last_update_received_at"),
                iso(rs, "last_message_persisted_at"),
                rs.getLong("inbox_pending_count"),
                rs.getLong("reconnect_count"),
                rs.getLong("dropped_update_count"),
                rs.getLong("duplicate_update_count"),
                iso(rs, "flood_wait_until"),
                rs.getString("error_summary"),
                rs.getString("tdlib_database_path"),
                iso(rs, "updated_at")
        );
    }

    private String messageIntelligenceJson(ResultSet rs, List<MessageLinkDto> links) throws SQLException {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("rawMessageId", rs.getLong("id"));
        node.put("telegramChatId", rs.getLong("telegram_chat_id"));
        node.put("telegramMessageId", rs.getLong("telegram_message_id"));
        node.put("contentType", rs.getString("content_type"));
        node.put("caption", rs.getString("caption"));
        ArrayNode linksNode = node.putArray("links");
        for (MessageLinkDto link : links) {
            ObjectNode linkNode = linksNode.addObject();
            linkNode.put("id", link.id());
            linkNode.put("url", link.url());
            linkNode.put("normalizedUrl", link.normalizedUrl());
            linkNode.put("domain", link.domain());
            linkNode.put("anchorText", link.anchorText());
            linkNode.put("source", link.source());
            linkNode.put("entityType", link.entityType());
            linkNode.put("offsetStart", link.offsetStart());
            linkNode.put("offsetEnd", link.offsetEnd());
            linkNode.put("hidden", link.hidden());
            linkNode.put("visibleUrl", link.visibleUrl());
            linkNode.put("telegramLink", link.telegramLink());
            linkNode.put("referralLike", link.referralLike());
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize message intelligence", error);
        }
    }

    private <T> PageResponse<T> page(List<T> content, int page, int size, long total) {
        int totalPages = size <= 0 ? 1 : (int) Math.ceil((double) total / (double) size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 2) {
            return "**";
        }
        return "***" + digits.substring(digits.length() - 2);
    }

    private String mapFrontendStatus(String status) {
        return switch (status) {
            case "WAIT_CODE" -> "WAITING_CODE";
            case "WAIT_PASSWORD" -> "WAITING_PASSWORD";
            case "WAIT_PHONE" -> "DISCONNECTED";
            case "CONNECTED" -> "CONNECTED";
            case "PAUSED" -> "PAUSED";
            case "DISABLED" -> "DISABLED";
            case "DEGRADED", "FLOOD_WAIT" -> "ERROR";
            case "AUTH_REQUIRED", "CONNECTING", "DISCONNECTED" -> "DISCONNECTED";
            default -> status;
        };
    }

    private String sourceType(String type) {
        if ("CHANNEL".equalsIgnoreCase(type)) {
            return "CHANNEL";
        }
        if ("DIRECT_CHAT".equalsIgnoreCase(type) || "PRIVATE".equalsIgnoreCase(type)) {
            return "DIRECT_CHAT";
        }
        return "GROUP";
    }

    private String preferredText(String text, String caption) {
        if (text != null && !text.isBlank()) {
            return text;
        }
        return caption;
    }

    private String telegramMessageUrl(String username, long telegramMessageId) {
        if (username == null || username.isBlank()) {
            return null;
        }
        String clean = username.startsWith("@") ? username.substring(1) : username;
        return "https://t.me/" + clean + "/" + telegramMessageId;
    }

    private Long nullableLong(ResultSet rs, String field) throws SQLException {
        long value = rs.getLong(field);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInt(ResultSet rs, String field) throws SQLException {
        int value = rs.getInt(field);
        return rs.wasNull() ? null : value;
    }

    private String iso(ResultSet rs, String field) throws SQLException {
        OffsetDateTime value = rs.getObject(field, OffsetDateTime.class);
        return value == null ? null : value.toString();
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record AccountDto(
            long id,
            String phone,
            Long telegramUserId,
            String username,
            String firstName,
            String lastName,
            String status,
            Object proxy,
            long groupsCount,
            long tokensUsed,
            String lastActivityAt
    ) {
    }

    public record GroupDto(
            long id,
            long telegramChatId,
            String title,
            String username,
            String sourceType,
            String category,
            boolean forum,
            boolean enabled,
            long accountId,
            long messagesPerDay,
            long guidesFound,
            String lastReadAt,
            Long lastReadMessageId,
            long rawMessagesTotal,
            long rawMessagesLast24h,
            String latestRawMessageAt,
            long topicsCount,
            boolean autoPipelineEnabled,
            boolean activeDialog,
            String displayState,
            String processingState,
            String source
    ) {
    }

    public record ChatPreflightDto(boolean chatExists, boolean chatEnabled) {
    }

    public record TopicDto(long chatId, long forumTopicId, long messageThreadId, String name, boolean general, String titleSource, String syncState) {
    }

    public record TopicReconcileResult(long placeholdersBefore, long resolved, long unresolved, int rawMessagesUpdated, List<String> unresolvedExamples) {
    }

    public record TopicDiagnostics(long accountId, long telegramChatId, long topics, long placeholders, long rawMessages, long rawMessagesWithTopic, List<String> topicExamples, List<String> rawMessageExamples) {
    }

    public record MessageDto(
            long id,
            long telegramMessageId,
            long groupId,
            String groupTitle,
            Long telegramChatId,
            Long senderTelegramUserId,
            String senderName,
            String senderUsername,
            boolean isBot,
            String text,
            String caption,
            Long replyToMessageId,
            long replyCount,
            String processingStatus,
            Long guideId,
            String topicName,
            Long topicId,
            String telegramMessageUrl,
            String date,
            String contentType,
            boolean hasMedia,
            boolean hasLinks,
            String mediaType,
            boolean hasVoice,
            boolean hasGif,
            boolean hasDocument,
            boolean hasPhoto,
            boolean hasVideo,
            String fileName,
            String mimeType,
            Integer durationSeconds,
            String mediaJson,
            List<MessageLinkDto> links,
            String messageIntelligenceJson,
            Double signalScore,
            Double guidePotentialScore,
            Double problemSignalScore,
            Double painScore,
            Double urgencyScore,
            Double willingnessToPayScore,
            Double technicalDepthScore,
            Double spamScore,
            String meaningSummary,
            String problemStatement,
            String solutionHint,
            String mentionedToolsJson,
            String mentionedPricesJson,
            String mentionedErrorsJson,
            String intelligenceReason,
            Boolean clusterCandidate,
            String embeddingStatus,
            Double classifierScore
    ) {
    }

    public record MessageChainDto(long chainId, long groupId, long rootMessageId, List<MessageDto> messages, String chainType) {
    }

    public record MessageLinkDto(
            long id,
            long messageId,
            String url,
            String normalizedUrl,
            String domain,
            String anchorText,
            String source,
            String entityType,
            Integer offsetStart,
            Integer offsetEnd,
            boolean hidden,
            boolean visibleUrl,
            boolean telegramLink,
            boolean referralLike,
            String rawEntityJson,
            String createdAt
    ) {
    }

    private record MediaSummary(
            boolean hasMedia,
            String mediaType,
            boolean hasVoice,
            boolean hasGif,
            boolean hasDocument,
            boolean hasPhoto,
            boolean hasVideo,
            String fileName,
            String mimeType,
            Integer durationSeconds,
            String mediaJson
    ) {
    }

    public record PipelineStatusDto(long rawMessages, long messageLinks, long inboxPending, long pipelineEvents, String mode) {
    }

    public record AccountHealthDto(
            long accountId,
            String status,
            String lastUpdateReceivedAt,
            String lastMessagePersistedAt,
            long inboxPendingCount,
            long reconnectCount,
            long droppedUpdateCount,
            long duplicateUpdateCount,
            String floodWaitUntil,
            String errorSummary,
            String tdlibDatabasePath,
            String updatedAt
    ) {
    }

    public record AccountAuthStateDto(long accountId, String authState, String updatedAt) {
    }

    public record TelegramDiagnosticsDbDto(
            long accountsCount,
            List<AccountAuthStateDto> accountAuthStates,
            String lastAuthStateChange,
            String lastUpdateReceivedAt,
            String lastUpdateNewMessageAt,
            long inboxPendingCount,
            long inboxProcessedCount,
            long inboxErrorCount,
            long rawMessagesCount,
            String lastRawMessageAt,
            String lastError,
            String floodWaitStatus,
            String backfillStatus,
            String liveUpdatesStatus
    ) {
    }

    public record TelegramRuntimeLogDto(
            String timestamp,
            String level,
            Long accountId,
            String eventType,
            String message,
            String errorCode,
            String errorSummary,
            String correlationId
    ) {
    }

    public record TelegramRuntimeEventDto(
            String timestamp,
            String eventType,
            Long accountId,
            Long telegramChatId,
            Long telegramMessageId,
            String correlationId,
            String updateId,
            Long durationMs,
            String payload
    ) {
    }

    public record PipelineEventDto(long id, String eventType, String entityType, Long entityId, String payload, String createdAt) {
    }

    public record ReplayRunSummaryDto(long id, String runName, String status, String startedAt, String finishedAt, String error) {
    }

    public record OperatorStatusDto(
            String backendHealth,
            String dbHealth,
            String tdlibStatus,
            Long primaryAccountId,
            String lastUpdateReceivedAt,
            String lastMessagePersistedAt,
            long rawMessageCount,
            long pipelineEventCount,
            long replayRunCount,
            List<TelegramRuntimeLogDto> recentErrors,
            List<ReplayRunSummaryDto> latestRuns
    ) {
    }
}
