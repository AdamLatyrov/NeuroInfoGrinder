package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

import com.larbcorp.neuroinfogrinder2.api.Stage1ReadService;
import com.larbcorp.neuroinfogrinder2.ingest.TelegramIngestionService;
import com.larbcorp.neuroinfogrinder2.telegram.model.TelegramAuthStateResponse;
import com.larbcorp.neuroinfogrinder2.telegram.model.TelegramChatDto;
import com.larbcorp.neuroinfogrinder2.telegram.model.TelegramTopicDto;
import jakarta.annotation.PreDestroy;
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

@Component
public class TdlibClientManager2 {
    private static final Logger log = LoggerFactory.getLogger(TdlibClientManager2.class);
    private static final long REQUEST_TIMEOUT_SECONDS = 30;
    private static final long HISTORY_TIMEOUT_SECONDS = 45;
    private static final long CHAT_SYNC_BATCH_TIMEOUT_SECONDS = 5;
    private static final long TDLIB_NATIVE_LOG_MAX_FILE_SIZE = 50L * 1024L * 1024L;

    private final TdlibProperties properties;
    private final TdlibMessageMapper messageMapper;
    private final TelegramIngestionService ingestionService;
    private final Stage1ReadService readService;
    private final JdbcTemplate jdbc;
    private final ConcurrentMap<Long, ClientState> states = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ChatSyncJob> chatSyncJobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TopicSyncJob> topicSyncJobs = new ConcurrentHashMap<>();
    private final ExecutorService chatSyncExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "tdlib-chat-sync");
        thread.setDaemon(true);
        return thread;
    });

    public TdlibClientManager2(
            TdlibProperties properties,
            TdlibMessageMapper messageMapper,
            TelegramIngestionService ingestionService,
            Stage1ReadService readService,
            JdbcTemplate jdbc
    ) {
        this.properties = properties;
        this.messageMapper = messageMapper;
        this.ingestionService = ingestionService;
        this.readService = readService;
        this.jdbc = jdbc;
    }

    public TelegramAuthStateResponse authorizationState(long accountId) {
        ClientState state = ensureStarted(accountId);
        refreshAuthorizationStateIfMissing(state);
        return authResponse(state);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public TelegramAuthStateResponse submitPhone(long accountId, String phone) {
        ClientState state = ensureStarted(accountId);
        awaitAuthorizationState(state, authorizationState ->
                        authorizationState instanceof TdApi.AuthorizationStateWaitPhoneNumber
                                || authorizationState instanceof TdApi.AuthorizationStateWaitCode
                                || authorizationState instanceof TdApi.AuthorizationStateWaitPassword
                                || authorizationState instanceof TdApi.AuthorizationStateReady,
                "AuthorizationStateWaitPhoneNumber");
        if (!(state.authorizationState.get() instanceof TdApi.AuthorizationStateWaitPhoneNumber)) {
            return authResponse(state);
        }
        long version = state.authorizationStateVersion.get();
        sendExpectOk(state, new TdApi.SetAuthenticationPhoneNumber(phone, null));
        awaitAuthorizationStateChange(state, version);
        return authorizationState(accountId);
    }

    public TelegramAuthStateResponse submitCode(long accountId, String code) {
        ClientState state = ensureStarted(accountId);
        awaitAuthorizationState(state, authorizationState ->
                        authorizationState instanceof TdApi.AuthorizationStateWaitCode
                                || authorizationState instanceof TdApi.AuthorizationStateWaitPassword
                                || authorizationState instanceof TdApi.AuthorizationStateReady,
                "AuthorizationStateWaitCode");
        if (!(state.authorizationState.get() instanceof TdApi.AuthorizationStateWaitCode)) {
            return authResponse(state);
        }
        long version = state.authorizationStateVersion.get();
        sendExpectOk(state, new TdApi.CheckAuthenticationCode(code));
        awaitAuthorizationStateChange(state, version);
        return authorizationState(accountId);
    }

    public TelegramAuthStateResponse submitPassword(long accountId, String password) {
        ClientState state = ensureStarted(accountId);
        awaitAuthorizationState(state, authorizationState ->
                        authorizationState instanceof TdApi.AuthorizationStateWaitPassword
                                || authorizationState instanceof TdApi.AuthorizationStateReady,
                "AuthorizationStateWaitPassword");
        if (!(state.authorizationState.get() instanceof TdApi.AuthorizationStateWaitPassword)) {
            return authResponse(state);
        }
        long version = state.authorizationStateVersion.get();
        sendExpectOk(state, new TdApi.CheckAuthenticationPassword(password));
        awaitAuthorizationStateChange(state, version);
        return authorizationState(accountId);
    }

    public TelegramAuthStateResponse reconnect(long accountId) {
        ClientState state = clientState(accountId);
        if (state.client != null) {
            try {
                state.client.send(new TdApi.Close(), ignored -> {
                });
            } catch (Exception ignored) {
            }
        }
        state.started.set(false);
        state.client = null;
        state.authorizationState.set(null);
        state.tdlibParametersSent.set(false);
        state.proxyConfiguredInTdlib.set(false);
        state.proxyPingStatus.set("NOT_TESTED");
        state.proxyLastError.set(null);
        return authorizationState(accountId);
    }

    public void disconnectAndForget(long accountId) {
        ClientState state = states.remove(accountId);
        if (state == null || state.client == null) {
            return;
        }
        try {
            state.client.send(new TdApi.LogOut(), ignored -> {
            });
        } catch (Exception error) {
            state.lastError.set(error.getMessage());
        }
        try {
            state.client.send(new TdApi.Close(), ignored -> {
            });
        } catch (Exception ignored) {
        }
        state.started.set(false);
        readService.recordRuntimeEvent("TDLIB_SESSION_DISCONNECTED", accountId, null, null, null, null, null);
    }

    public List<TelegramChatDto> getChats(long accountId, int limit) {
        long started = System.nanoTime();
        ClientState state = ensureReady(accountId);
        try {
            readService.recordRuntimeEvent("CHAT_SYNC_STARTED", accountId, null, null, null, null, null);
            loadChats(state, limit);
            TdApi.Chats chats = send(state, new TdApi.GetChats(new TdApi.ChatListMain(), normalizeLimit(limit, properties.getChatLimit())));
            List<TelegramChatDto> result = new ArrayList<>();
            if (chats.chatIds != null) {
                for (long chatId : chats.chatIds) {
                    TelegramChatDto chat = toChatDto(state, getChat(state, chatId));
                    readService.upsertTelegramChat(accountId, chat);
                    result.add(chat);
                }
            }
            result.sort(Comparator.comparing(TelegramChatDto::title, String.CASE_INSENSITIVE_ORDER));
            readService.recordRuntimeEvent("CHAT_SYNC_COMPLETED", accountId, null, null, null, durationMs(started), null);
            return result;
        } catch (RuntimeException error) {
            readService.recordRuntimeError("ERROR", accountId, null, null, "CHAT_SYNC_FAILED", "Chat sync failed", null, error.getMessage(), null);
            readService.recordRuntimeEvent("CHAT_SYNC_FAILED", accountId, null, null, null, durationMs(started), null);
            throw error;
        }
    }

    public ChatSyncStatus startChatSync(long accountId, int limit) {
        int requestedLimit = Math.max(1, Math.min(limit, 500));
        ChatSyncJob current = chatSyncJobs.get(accountId);
        if (current != null && current.running()) {
            return current.status();
        }
        ChatSyncJob job = new ChatSyncJob(accountId, UUID.randomUUID().toString(), requestedLimit);
        chatSyncJobs.put(accountId, job);
        readService.recordChatSyncEvent("CHAT_SYNC_STARTED", accountId, job.syncId(), null, requestedLimit, null, null, null, null);
        chatSyncExecutor.submit(() -> runProgressiveChatSync(job));
        return job.status();
    }

    public ChatSyncStatus chatSyncStatus(long accountId) {
        ChatSyncJob job = chatSyncJobs.get(accountId);
        if (job == null) {
            return new ChatSyncStatus(accountId, null, "IDLE", null, null, 0, 0, 0, 0, 0, null, null, 0);
        }
        return job.status();
    }

    public ChatSyncStatus cancelChatSync(long accountId) {
        ChatSyncJob job = chatSyncJobs.get(accountId);
        if (job == null) {
            return chatSyncStatus(accountId);
        }
        job.cancelled.set(true);
        job.finish("CANCELLED", null);
        readService.recordChatSyncEvent("CHAT_SYNC_CANCELLED", accountId, job.syncId(), null, job.requestedLimit(), null, job.storedCount.get(), job.durationMs(), null);
        return job.status();
    }

    public ChatTdlibDiagnostic chatTdlibDiagnostic(long accountId, long knownDbChats, Long knownChatId) {
        long started = System.nanoTime();
        boolean clientAlive = false;
        boolean getKnownChatOk = false;
        boolean getChatHistoryLimit1Ok = false;
        boolean loadChatsBatchOk = false;
        Long getKnownChatMs = null;
        Long getChatHistoryLimit1Ms = null;
        Long loadChatsBatchMs = null;
        String loadChatsError = null;
        String getKnownChatError = null;
        String getChatHistoryError = null;
        try {
            ClientState state = ensureReady(accountId);
            clientAlive = state.client != null && state.authorizationState.get() instanceof TdApi.AuthorizationStateReady;
            if (knownChatId != null && readService.isActiveTdlibDialog(accountId, knownChatId)) {
                long step = System.nanoTime();
                try {
                    send(state, new TdApi.GetChat(knownChatId), 8);
                    getKnownChatOk = true;
                } catch (RuntimeException error) {
                    getKnownChatError = error.getMessage();
                }
                getKnownChatMs = durationMs(step);

                step = System.nanoTime();
                try {
                    send(state, new TdApi.GetChatHistory(knownChatId, 0, 0, 1, false), 12);
                    getChatHistoryLimit1Ok = true;
                } catch (RuntimeException error) {
                    getChatHistoryError = error.getMessage();
                }
                getChatHistoryLimit1Ms = durationMs(step);
            } else if (knownChatId != null) {
                getKnownChatError = "CHAT_NOT_ACTIVE_MEMBER";
                getChatHistoryError = "CHAT_NOT_ACTIVE_MEMBER";
            }
            long step = System.nanoTime();
            try {
                loadChats(state, 20, CHAT_SYNC_BATCH_TIMEOUT_SECONDS);
                loadChatsBatchOk = true;
            } catch (RuntimeException error) {
                loadChatsError = error.getMessage();
            }
            loadChatsBatchMs = durationMs(step);
        } catch (RuntimeException error) {
            loadChatsError = loadChatsError == null ? error.getMessage() : loadChatsError;
        }
        String recommendation = getChatHistoryLimit1Ok
                ? "Use DB cache for list, allow backfill by known chatId"
                : "Keep DB cache for list, block backfill until getChatHistory succeeds for known chatId";
        return new ChatTdlibDiagnostic(
                accountId,
                clientAlive,
                knownDbChats,
                knownChatId,
                getKnownChatOk,
                getKnownChatMs,
                getKnownChatError,
                getChatHistoryLimit1Ok,
                getChatHistoryLimit1Ms,
                getChatHistoryError,
                loadChatsBatchOk,
                loadChatsBatchMs,
                loadChatsError,
                recommendation,
                durationMs(started)
        );
    }

    public boolean isReady(long accountId) {
        ClientState state = states.get(accountId);
        return state != null && state.client != null && state.authorizationState.get() instanceof TdApi.AuthorizationStateReady;
    }

    private void runProgressiveChatSync(ChatSyncJob job) {
        long started = System.nanoTime();
        try {
            ClientState state = ensureReady(job.accountId());
            storeCachedChats(job, state, 0, durationMs(started));
            int[] batches = batchPlan(job.requestedLimit());
            int batchNo = 0;
            for (int requested : batches) {
                if (job.cancelled.get()) {
                    return;
                }
                batchNo++;
                job.status.set("RUNNING");
                readService.recordChatSyncEvent("CHAT_SYNC_BATCH_REQUESTED", job.accountId(), job.syncId(), batchNo, requested, null, job.storedCount.get(), null, null);
                List<TelegramChatDto> chats;
                long batchStarted = System.nanoTime();
                try {
                    loadChats(state, requested, CHAT_SYNC_BATCH_TIMEOUT_SECONDS);
                    TdApi.Chats tdChats = send(state, new TdApi.GetChats(new TdApi.ChatListMain(), requested), CHAT_SYNC_BATCH_TIMEOUT_SECONDS);
                    chats = new ArrayList<>();
                    if (tdChats.chatIds != null) {
                        for (long chatId : tdChats.chatIds) {
                            TdApi.Chat chat = state.chatCache.get(chatId);
                            if (chat != null) {
                                chats.add(toChatDtoFast(chat));
                            }
                        }
                    }
                } catch (RuntimeException error) {
                    job.lastError.set(error.getMessage());
                    job.status.set(job.storedCount.get() > 0 ? "PARTIAL" : "FAILED");
                readService.recordChatSyncEvent("CHAT_SYNC_PARTIAL_TIMEOUT", job.accountId(), job.syncId(), batchNo, requested, 0, job.storedCount.get(), durationMs(batchStarted), error.getMessage(), job.storedCount.get() > 0, "DB_CACHE");
                continue;
                }
                int storedThisBatch = 0;
                for (TelegramChatDto chat : chats) {
                    readService.upsertTelegramChat(job.accountId(), chat);
                    storedThisBatch++;
                    job.lastChatId.set(chat.id());
                    readService.recordChatSyncEvent("CHAT_SYNC_CHAT_STORED", job.accountId(), job.syncId(), batchNo, requested, chats.size(), job.storedCount.incrementAndGet(), null, null);
                }
                job.loadedCount.set(Math.max(job.loadedCount.get(), chats.size()));
                updateTypeCounts(job, chats);
                readService.recordChatSyncEvent("CHAT_SYNC_BATCH_RECEIVED", job.accountId(), job.syncId(), batchNo, requested, chats.size(), job.storedCount.get(), durationMs(batchStarted), null);
                readService.recordChatSyncEvent("CHAT_SYNC_PROGRESS", job.accountId(), job.syncId(), batchNo, requested, chats.size(), job.storedCount.get(), durationMs(started), null);
            }
            String finalStatus = job.lastError.get() == null ? "COMPLETED" : (job.storedCount.get() > 0 ? "PARTIAL" : "FAILED");
            job.finish(finalStatus, null);
            String eventType = finalStatus.equals("COMPLETED") ? "CHAT_SYNC_COMPLETED" : (finalStatus.equals("PARTIAL") ? "CHAT_SYNC_PARTIAL" : "CHAT_SYNC_FAILED");
            readService.recordChatSyncEvent(eventType, job.accountId(), job.syncId(), null, job.requestedLimit(), job.loadedCount.get(), job.storedCount.get(), job.durationMs(), job.lastError.get(), job.storedCount.get() > 0, finalStatus.equals("COMPLETED") ? "TDLIB_BATCH" : "DB_CACHE");
        } catch (RuntimeException error) {
            job.lastError.set(error.getMessage());
            job.finish(job.storedCount.get() > 0 ? "PARTIAL" : "FAILED", error.getMessage());
            String eventType = job.storedCount.get() > 0 ? "CHAT_SYNC_PARTIAL" : "CHAT_SYNC_FAILED";
            readService.recordChatSyncEvent(eventType, job.accountId(), job.syncId(), null, job.requestedLimit(), job.loadedCount.get(), job.storedCount.get(), job.durationMs(), error.getMessage(), job.storedCount.get() > 0, job.storedCount.get() > 0 ? "DB_CACHE" : "TDLIB_BATCH");
        }
    }

    private void storeCachedChats(ChatSyncJob job, ClientState state, int batchNo, long durationMs) {
        List<TelegramChatDto> chats = state.chatCache.values().stream()
                .map(this::toChatDtoFast)
                .sorted(Comparator.comparing(TelegramChatDto::title, String.CASE_INSENSITIVE_ORDER))
                .limit(job.requestedLimit())
                .toList();
        if (chats.isEmpty()) {
            return;
        }
        for (TelegramChatDto chat : chats) {
            readService.upsertTelegramChat(job.accountId(), chat);
            job.lastChatId.set(chat.id());
            job.storedCount.incrementAndGet();
        }
        job.loadedCount.set(Math.max(job.loadedCount.get(), chats.size()));
        updateTypeCounts(job, chats);
        readService.recordChatSyncEvent("CHAT_SYNC_BATCH_RECEIVED", job.accountId(), job.syncId(), batchNo, job.requestedLimit(), chats.size(), job.storedCount.get(), durationMs, null, true, "UPDATES");
        readService.recordChatSyncEvent("CHAT_SYNC_PROGRESS", job.accountId(), job.syncId(), batchNo, job.requestedLimit(), chats.size(), job.storedCount.get(), durationMs, null, true, "DB_CACHE");
    }

    private int[] batchPlan(int limit) {
        List<Integer> batches = new ArrayList<>();
        int current = Math.min(20, limit);
        while (current < limit) {
            batches.add(current);
            current = Math.min(limit, current + 30);
        }
        batches.add(limit);
        return batches.stream().mapToInt(Integer::intValue).distinct().toArray();
    }

    private void updateTypeCounts(ChatSyncJob job, List<TelegramChatDto> chats) {
        int groups = 0;
        int channels = 0;
        int privateChats = 0;
        for (TelegramChatDto chat : chats) {
            if ("CHANNEL".equals(chat.sourceType())) {
                channels++;
            } else if ("DIRECT_CHAT".equals(chat.sourceType())) {
                privateChats++;
            } else {
                groups++;
            }
        }
        job.groupsCount.set(groups);
        job.channelsCount.set(channels);
        job.privateCount.set(privateChats);
    }

    public List<TelegramTopicDto> getTopics(long accountId, long chatId, int limit) {
        ClientState state = ensureReady(accountId);
        TdApi.ForumTopics forumTopics = send(state, new TdApi.GetForumTopics(
                chatId,
                "",
                0,
                0,
                0,
                normalizeLimit(limit, properties.getTopicLimit())
        ));
        List<TelegramTopicDto> topics = new ArrayList<>();
        if (forumTopics.topics == null) {
            return topics;
        }
        for (TdApi.ForumTopic topic : forumTopics.topics) {
            if (topic == null || topic.info == null) {
                continue;
            }
            long messageThreadId = topic.lastMessage == null ? topic.info.forumTopicId : topicId(topic.lastMessage.topicId, topic.info.forumTopicId);
            TelegramTopicDto dto = new TelegramTopicDto(
                    chatId,
                    topic.info.forumTopicId,
                    messageThreadId,
                    topic.info.name,
                    topic.info.isGeneral
            );
            state.topicCache.computeIfAbsent(chatId, ignored -> new ConcurrentHashMap<>()).put(dto.messageThreadId(), dto);
            readService.upsertTelegramTopic(accountId, dto);
            topics.add(dto);
        }
        return topics;
    }

    public TopicSyncStatus startTopicSync(long accountId, long chatId, int limit) {
        String key = accountId + ":" + chatId;
        TopicSyncJob current = topicSyncJobs.get(key);
        if (current != null && current.running()) {
            return current.status();
        }
        TopicSyncJob job = new TopicSyncJob(accountId, chatId, UUID.randomUUID().toString(), Math.max(1, Math.min(limit, 50)));
        topicSyncJobs.put(key, job);
        chatSyncExecutor.submit(() -> runTopicSync(job));
        return job.status();
    }

    public TopicSyncStatus topicSyncStatus(long accountId, long chatId) {
        TopicSyncJob job = topicSyncJobs.get(accountId + ":" + chatId);
        if (job == null) {
            return new TopicSyncStatus(accountId, chatId, null, "IDLE", null, null, 0, 0, 0, null, true, null, 0);
        }
        return job.status();
    }

    public List<TopicSyncStatus> startAllForumTopicSync(long accountId) {
        List<Long> forumChatIds = jdbc.queryForList("SELECT telegram_chat_id FROM telegram_chats WHERE account_id = ? AND is_forum = true ORDER BY updated_at DESC", Long.class, accountId);
        List<TopicSyncStatus> statuses = new ArrayList<>();
        for (Long chatId : forumChatIds) {
            statuses.add(startTopicSync(accountId, chatId, 50));
        }
        return statuses;
    }

    private void runTopicSync(TopicSyncJob job) {
        try {
            ClientState state = ensureReady(job.accountId());
            int offsetDate = 0;
            long offsetMessageId = 0;
            int offsetForumTopicId = 0;
            int page = 0;
            while (page < 20 && !job.cancelled.get()) {
                page++;
                long started = System.nanoTime();
                TdApi.ForumTopics response = send(state, new TdApi.GetForumTopics(job.chatId(), "", offsetDate, offsetMessageId, offsetForumTopicId, job.limit()), 10);
                int received = response.topics == null ? 0 : response.topics.length;
                job.loadedCount.addAndGet(received);
                job.pagesLoaded.incrementAndGet();
                if (response.topics != null) {
                    for (TdApi.ForumTopic topic : response.topics) {
                        if (topic == null || topic.info == null) {
                            continue;
                        }
                        long messageThreadId = topic.lastMessage == null ? topic.info.forumTopicId : topicId(topic.lastMessage.topicId, topic.info.forumTopicId);
                        readService.upsertTelegramTopic(job.accountId(), new TelegramTopicDto(job.chatId(), topic.info.forumTopicId, messageThreadId, topic.info.name, topic.info.isGeneral));
                        job.storedCount.incrementAndGet();
                    }
                }
                readService.recordRuntimeEvent("TOPIC_SYNC_PAGE_STORED", job.accountId(), job.chatId(), null, job.syncId(), durationMs(started), null);
                if (received == 0 || (response.nextOffsetDate == 0 && response.nextOffsetMessageId == 0 && response.nextOffsetForumTopicId == 0)) {
                    job.finish("COMPLETED", null, true);
                    readService.recordRuntimeEvent("TOPIC_SYNC_COMPLETED", job.accountId(), job.chatId(), null, job.syncId(), job.durationMs(), null);
                    return;
                }
                offsetDate = response.nextOffsetDate;
                offsetMessageId = response.nextOffsetMessageId;
                offsetForumTopicId = response.nextOffsetForumTopicId;
                Thread.sleep(300);
            }
            job.finish("PARTIAL", "max pages reached", job.storedCount.get() > 0);
            readService.recordRuntimeEvent("TOPIC_SYNC_PARTIAL", job.accountId(), job.chatId(), null, job.syncId(), job.durationMs(), null);
        } catch (Exception error) {
            job.finish(job.storedCount.get() > 0 ? "PARTIAL" : "FAILED", error.getMessage(), job.storedCount.get() > 0);
            readService.recordRuntimeError("ERROR", job.accountId(), job.chatId(), null, "TOPIC_SYNC_FAILED", "Topic sync failed", null, error.getMessage(), job.syncId());
            readService.recordRuntimeEvent(job.storedCount.get() > 0 ? "TOPIC_SYNC_PARTIAL" : "TOPIC_SYNC_FAILED", job.accountId(), job.chatId(), null, job.syncId(), job.durationMs(), null);
        }
    }

    public BackfillResult backfill(long accountId, long telegramChatId, int limit) {
        long started = System.nanoTime();
        if (!readService.isActiveTdlibDialog(accountId, telegramChatId)) {
            readService.recordRuntimeEvent("BACKFILL_BLOCKED_CHAT_NOT_ACTIVE_MEMBER", accountId, telegramChatId, null, null, null, null);
            throw new TdlibException("CHAT_NOT_ACTIVE_MEMBER");
        }
        ClientState state = ensureReady(accountId);
        int normalizedLimit = Math.min(Math.max(limit, 1), 100);
        readService.recordRuntimeEvent("BACKFILL_STARTED", accountId, telegramChatId, null, null, null, null);
        TdApi.Messages messages;
        try {
            messages = send(state, new TdApi.GetChatHistory(
                    telegramChatId,
                    0,
                    0,
                    normalizedLimit,
                    false
            ), HISTORY_TIMEOUT_SECONDS);
        } catch (TdlibException error) {
            if (isFloodWait(error)) {
                log.warn("TDLib backfill hit FLOOD_WAIT accountId={} chatId={}", accountId, telegramChatId);
                readService.updateAccountTdlibState(accountId, "FLOOD_WAIT", databaseDirectory(state).toString(), stateName(state), "FLOOD_WAIT");
                readService.recordRuntimeEvent("FLOOD_WAIT", accountId, telegramChatId, null, null, durationMs(started), null);
                readService.recordRuntimeEvent("BACKFILL_PAUSED_FLOOD_WAIT", accountId, telegramChatId, null, null, durationMs(started), null);
            }
            readService.recordRuntimeError("ERROR", accountId, telegramChatId, null, "BACKFILL_FAILED", "TDLib backfill failed", null, error.getMessage(), null);
            readService.recordRuntimeEvent("BACKFILL_FAILED", accountId, telegramChatId, null, null, durationMs(started), null);
            throw error;
        }
        int persisted = 0;
        if (messages.messages != null) {
            for (TdApi.Message message : messages.messages) {
                if (message == null) {
                    continue;
                }
                persistMessage(state, message, false);
                persisted++;
            }
        }
        readService.recordRuntimeEvent("BACKFILL_BATCH_STORED", accountId, telegramChatId, null, null, durationMs(started), null);
        readService.recordRuntimeEvent("BACKFILL_COMPLETED", accountId, telegramChatId, null, null, durationMs(started), null);
        return new BackfillResult(true, persisted, telegramChatId);
    }

    @PreDestroy
    public void shutdown() {
        for (ClientState state : states.values()) {
            if (state.client == null) {
                continue;
            }
            try {
                state.client.send(new TdApi.Close(), ignored -> {
                });
            } catch (Exception ignored) {
            }
        }
        chatSyncExecutor.shutdownNow();
    }

    private ClientState ensureStarted(long accountId) {
        validateConfiguration();
        ClientState state = clientState(accountId);
        if (state.started.compareAndSet(false, true)) {
            try {
                readService.recordRuntimeEvent("TDLIB_CONFIG_CHECK", accountId, null, null, null, null, null);
                state.tdlibParametersSent.set(false);
                createDirectories(state);
                readService.recordRuntimeEvent("TDLIB_NATIVE_LOAD_START", accountId, null, null, null, null, null);
                TdlibNativeLoader.load(properties);
                readService.recordRuntimeEvent("TDLIB_NATIVE_LOAD_SUCCESS", accountId, null, null, null, null, null);
                configureTdlibLogging();
                Client.setLogMessageHandler(0, null);
                state.client = Client.create(
                        object -> handleUpdate(state, object),
                        null,
                        throwable -> {
                            state.lastError.set(throwable.getMessage());
                            readService.updateAccountTdlibState(accountId, "DEGRADED", databaseDirectory(state).toString(), stateName(state), throwable.getMessage());
                        }
                );
                TdApi.AuthorizationState authorizationState = send(state, new TdApi.GetAuthorizationState());
                onAuthorizationStateUpdated(state, authorizationState);
                configureProxyIfNeeded(state);
                log.info("TDLib account actor started accountId={}", accountId);
            } catch (Exception error) {
                state.started.set(false);
                state.client = null;
                readService.updateAccountTdlibState(accountId, "DEGRADED", databaseDirectory(state).toString(), stateName(state), error.getMessage());
                readService.recordRuntimeError("ERROR", accountId, null, null, "TDLib_ERROR", "Failed to initialize TDLib client", null, error.getMessage(), null);
                readService.recordRuntimeEvent("TDLIB_NATIVE_LOAD_FAILED", accountId, null, null, null, null, null);
                throw new TdlibException("Failed to initialize TDLib client", error);
            }
        }
        return state;
    }

    private ClientState ensureReady(long accountId) {
        ClientState state = ensureStarted(accountId);
        refreshAuthorizationStateIfMissing(state);
        if (!(state.authorizationState.get() instanceof TdApi.AuthorizationStateReady)) {
            throw new TdlibException("TDLib is not ready. Current authorization state: " + stateName(state));
        }
        return state;
    }

    private void validateConfiguration() {
        if (!properties.isEnabled()) {
            throw new TdlibException("TDLib is disabled. Set TDLIB_ENABLED=true for real Telegram auth");
        }
        if (properties.getApiId() <= 0 || properties.getApiHash() == null || properties.getApiHash().isBlank()) {
            throw new TdlibException("TDLib api id/hash are not configured");
        }
    }

    private void handleUpdate(ClientState state, TdApi.Object object) {
        if (object == null) {
            return;
        }
        if (object instanceof TdApi.UpdateAuthorizationState updateAuthorizationState) {
            onAuthorizationStateUpdated(state, updateAuthorizationState.authorizationState);
            return;
        }
        if (object instanceof TdApi.UpdateNewChat updateNewChat && updateNewChat.chat != null) {
            state.chatCache.put(updateNewChat.chat.id, updateNewChat.chat);
            upsertChatFromUpdate(state, updateNewChat.chat, "UPDATE_NEW_CHAT");
            return;
        }
        if (object instanceof TdApi.UpdateChatTitle updateChatTitle) {
            TdApi.Chat cached = state.chatCache.get(updateChatTitle.chatId);
            if (cached != null) {
                cached.title = updateChatTitle.title;
                upsertChatFromUpdate(state, cached, "UPDATE_CHAT_TITLE");
            }
            readService.updateTelegramChatTitle(state.accountId, updateChatTitle.chatId, updateChatTitle.title);
            readService.recordRuntimeEvent("CHAT_CACHE_UPDATED", state.accountId, updateChatTitle.chatId, null, null, null, null);
            return;
        }
        if (object instanceof TdApi.UpdateChatLastMessage updateChatLastMessage) {
            TdApi.Chat cached = state.chatCache.get(updateChatLastMessage.chatId);
            if (cached != null) {
                cached.lastMessage = updateChatLastMessage.lastMessage;
                upsertChatFromUpdate(state, cached, "UPDATE_CHAT_LAST_MESSAGE");
            }
            readService.updateTelegramChatLastMessage(
                    state.accountId,
                    updateChatLastMessage.chatId,
                    updateChatLastMessage.lastMessage == null ? null : updateChatLastMessage.lastMessage.id,
                    updateChatLastMessage.lastMessage == null ? null : java.time.OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(updateChatLastMessage.lastMessage.date), java.time.ZoneOffset.UTC)
            );
            readService.recordRuntimeEvent("CHAT_CACHE_UPDATED", state.accountId, updateChatLastMessage.chatId, updateChatLastMessage.lastMessage == null ? null : updateChatLastMessage.lastMessage.id, null, null, null);
            return;
        }
        if (object instanceof TdApi.UpdateChatPosition updateChatPosition) {
            readService.updateTelegramChatPosition(state.accountId, updateChatPosition.chatId, chatListName(updateChatPosition.position == null ? null : updateChatPosition.position.list), updateChatPosition.position == null ? null : updateChatPosition.position.order);
            readService.recordRuntimeEvent("CHAT_CACHE_UPDATED", state.accountId, updateChatPosition.chatId, null, null, null, null);
            return;
        }
        if (object instanceof TdApi.UpdateChatAddedToList updateChatAddedToList) {
            readService.updateTelegramChatPosition(state.accountId, updateChatAddedToList.chatId, chatListName(updateChatAddedToList.chatList), null);
            readService.recordRuntimeEvent("CHAT_CACHE_UPDATED", state.accountId, updateChatAddedToList.chatId, null, null, null, null);
            return;
        }
        if (object instanceof TdApi.UpdateUser updateUser && updateUser.user != null) {
            state.userCache.put(updateUser.user.id, updateUser.user);
            return;
        }
        if (object instanceof TdApi.UpdateSupergroup updateSupergroup && updateSupergroup.supergroup != null) {
            state.supergroupCache.put(updateSupergroup.supergroup.id, updateSupergroup.supergroup);
            readService.updateSupergroupMetadata(state.accountId, updateSupergroup.supergroup.id, updateSupergroup.supergroup.isChannel, updateSupergroup.supergroup.isForum, updateSupergroup.supergroup.isForum || updateSupergroup.supergroup.hasForumTabs);
            readService.recordRuntimeEvent("SUPERGROUP_CACHE_UPDATED", state.accountId, null, null, null, null, null);
            return;
        }
        if (object instanceof TdApi.UpdateSupergroupFullInfo updateSupergroupFullInfo && updateSupergroupFullInfo.supergroupFullInfo != null) {
            readService.recordRuntimeEvent("SUPERGROUP_FULL_INFO_UPDATED", state.accountId, null, null, null, null, null);
            return;
        }
        if (object instanceof TdApi.UpdateForumTopicInfo updateForumTopicInfo && updateForumTopicInfo.info != null) {
            TelegramTopicDto topic = new TelegramTopicDto(
                    updateForumTopicInfo.info.chatId,
                    updateForumTopicInfo.info.forumTopicId,
                    updateForumTopicInfo.info.forumTopicId,
                    updateForumTopicInfo.info.name,
                    updateForumTopicInfo.info.isGeneral
            );
            state.topicCache.computeIfAbsent(topic.chatId(), ignored -> new ConcurrentHashMap<>()).put(topic.messageThreadId(), topic);
            readService.upsertTelegramTopic(state.accountId, topic);
            return;
        }
        if (object instanceof TdApi.UpdateNewMessage updateNewMessage && updateNewMessage.message != null) {
            readService.recordRuntimeEvent("UPDATE_RECEIVED", state.accountId, updateNewMessage.message.chatId, updateNewMessage.message.id, null, null, null);
            if (!readService.isChatEnabled(state.accountId, updateNewMessage.message.chatId)) {
                incrementDropped(state.accountId);
                return;
            }
            persistMessage(state, updateNewMessage.message, true);
        }
    }

    private void persistMessage(ClientState state, TdApi.Message message, boolean live) {
        try {
            TdApi.Chat chat = state.chatCache.get(message.chatId);
            if (chat != null) {
                readService.upsertTelegramChat(state.accountId, toChatDto(state, chat));
            }
            ingestionService.ingestLocalMessage(messageMapper.toLocalMessageInput(
                    state.accountId,
                    message,
                    cachedSenderName(state, message),
                    cachedSenderUsername(state, message),
                    cachedSenderIsBot(state, message)
            ));
            if (live) {
                log.info("[TDLIB_UPDATE_RECEIVED] accountId={} chatId={} telegramMessageId={}", state.accountId, message.chatId, message.id);
            }
        } catch (Exception error) {
            state.lastError.set(error.getMessage());
            readService.updateAccountTdlibState(state.accountId, "DEGRADED", databaseDirectory(state).toString(), stateName(state), error.getMessage());
            readService.recordRuntimeError("ERROR", state.accountId, message.chatId, message.id, "TDLIB_UPDATE_PROCESS_FAILED", "TDLib update processing failed", null, error.getMessage(), null);
            throw error;
        }
    }

    private void upsertChatFromUpdate(ClientState state, TdApi.Chat chat, String sourceEvent) {
        readService.upsertTelegramChat(state.accountId, toChatDto(state, chat));
        if (chat.type instanceof TdApi.ChatTypeSupergroup supergroupType) {
            readService.linkChatToSupergroup(state.accountId, chat.id, supergroupType.supergroupId, supergroupType.isChannel);
            TdApi.Supergroup supergroup = state.supergroupCache.get(supergroupType.supergroupId);
            if (supergroup != null) {
                readService.updateSupergroupMetadata(state.accountId, supergroup.id, supergroup.isChannel, supergroup.isForum, supergroup.isForum || supergroup.hasForumTabs);
            }
        }
        readService.recordRuntimeEvent("CHAT_CACHE_UPDATED", state.accountId, chat.id, chat.lastMessage == null ? null : chat.lastMessage.id, sourceEvent, null, null);
    }

    private String chatListName(TdApi.ChatList chatList) {
        if (chatList == null) {
            return null;
        }
        if (chatList instanceof TdApi.ChatListMain) {
            return "MAIN";
        }
        if (chatList instanceof TdApi.ChatListArchive) {
            return "ARCHIVE";
        }
        return chatList.getClass().getSimpleName();
    }

    private void onAuthorizationStateUpdated(ClientState state, TdApi.AuthorizationState authorizationState) {
        state.authorizationState.set(authorizationState);
        state.authorizationStateVersion.incrementAndGet();
        state.authorizationLock.lock();
        try {
            state.authorizationChanged.signalAll();
        } finally {
            state.authorizationLock.unlock();
        }

        if (authorizationState instanceof TdApi.AuthorizationStateWaitTdlibParameters
                && state.tdlibParametersSent.compareAndSet(false, true)) {
            sendWithoutResult(state, buildTdlibParameters(state));
        }
        if (!(authorizationState instanceof TdApi.AuthorizationStateWaitTdlibParameters)) {
            configureProxyIfNeeded(state);
        }
        if (authorizationState instanceof TdApi.AuthorizationStateClosed) {
            state.started.set(false);
        }
        String status = accountStatus(authorizationState);
        readService.updateAccountTdlibState(state.accountId, status, databaseDirectory(state).toString(), stateName(state), state.lastError.get());
        readService.recordRuntimeEvent("AUTH_STATE_CHANGED", state.accountId, null, null, null, null, null);
        readService.recordRuntimeEvent("TDLIB_ACCOUNT_AUTH_STATE", state.accountId, null, null, null, null, null);
        if (authorizationState instanceof TdApi.AuthorizationStateReady) {
            readService.recordRuntimeEvent("ACCOUNT_READY", state.accountId, null, null, null, null, null);
            readService.recordRuntimeEvent("TDLIB_ACCOUNT_READY", state.accountId, null, null, null, null, null);
        }
        log.info("[TDLIB_ACCOUNT_AUTH_STATE] accountId={} state={} status={}", state.accountId, stateName(state), status);
    }

    private long durationMs(long startedNano) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNano);
    }

    private TdApi.SetTdlibParameters buildTdlibParameters(ClientState state) {
        TdApi.SetTdlibParameters parameters = new TdApi.SetTdlibParameters();
        parameters.useTestDc = properties.isUseTestDc();
        parameters.databaseDirectory = databaseDirectory(state).toAbsolutePath().toString();
        parameters.filesDirectory = filesDirectory(state).toAbsolutePath().toString();
        parameters.databaseEncryptionKey = properties.getDatabaseEncryptionKey().getBytes(StandardCharsets.UTF_8);
        parameters.useFileDatabase = properties.isUseFileDatabase();
        parameters.useChatInfoDatabase = properties.isUseChatInfoDatabase();
        parameters.useMessageDatabase = properties.isUseMessageDatabase();
        parameters.useSecretChats = properties.isUseSecretChats();
        parameters.apiId = properties.getApiId();
        parameters.apiHash = properties.getApiHash();
        parameters.systemLanguageCode = properties.getSystemLanguageCode();
        parameters.deviceModel = properties.getDeviceModel();
        parameters.systemVersion = properties.getSystemVersion();
        parameters.applicationVersion = properties.getApplicationVersion();
        return parameters;
    }

    private void configureProxyIfNeeded(ClientState state) {
        if (!properties.isProxyEnabled()) {
            state.proxyPingStatus.compareAndSet(null, "NOT_TESTED");
            return;
        }
        if (state.client == null || !state.proxySetupStarted.compareAndSet(false, true)) {
            return;
        }
        readService.recordRuntimeEvent("TDLIB_PROXY_CONFIG_PRESENT", state.accountId, null, null, null, null, null);
        readService.recordRuntimeEvent("TDLIB_PROXY_SETUP_START", state.accountId, null, null, null, null, null);
        try {
            TdApi.Proxy proxy = buildProxy();
            TdApi.AddedProxy addedProxy = send(state, new TdApi.AddProxy(proxy, true, "NeuroInfoGrinder TDLib proxy"));
            state.proxyConfiguredInTdlib.set(true);
            state.proxyLastError.set(null);
            readService.recordRuntimeEvent("TDLIB_PROXY_SETUP_SUCCESS", state.accountId, null, null, null, null, null);
            if (addedProxy != null && addedProxy.id > 0) {
                sendExpectOk(state, new TdApi.EnableProxy(addedProxy.id));
            }
            pingProxy(state, proxy);
        } catch (RuntimeException error) {
            state.proxySetupStarted.set(false);
            state.proxyConfiguredInTdlib.set(false);
            state.proxyPingStatus.set("FAILED");
            state.proxyLastError.set(sanitizeProxyError(error.getMessage()));
            readService.recordRuntimeError("ERROR", state.accountId, null, null, "TDLIB_PROXY_SETUP_FAILED", "TDLib proxy setup failed", null, sanitizeProxyError(error.getMessage()), null);
            readService.recordRuntimeEvent("TDLIB_PROXY_SETUP_FAILED", state.accountId, null, null, null, null, null);
        }
    }

    private TdApi.Proxy buildProxy() {
        TdApi.ProxyType proxyType = switch (safeProxyType()) {
            case "HTTP" -> new TdApi.ProxyTypeHttp(blankToEmpty(properties.getProxyUsername()), blankToEmpty(properties.getProxyPassword()), false);
            case "SOCKS5" -> new TdApi.ProxyTypeSocks5(blankToEmpty(properties.getProxyUsername()), blankToEmpty(properties.getProxyPassword()));
            default -> throw new TdlibException("Unsupported TDLib proxy type: " + safeProxyType());
        };
        return new TdApi.Proxy(properties.getProxyHost(), properties.getProxyPort(), proxyType);
    }

    private void pingProxy(ClientState state, TdApi.Proxy proxy) {
        readService.recordRuntimeEvent("TDLIB_PROXY_PING_START", state.accountId, null, null, null, null, null);
        try {
            TdApi.Seconds seconds = send(state, new TdApi.PingProxy(proxy), 10);
            state.proxyPingStatus.set("OK");
            state.proxyLastError.set(null);
            readService.recordRuntimeEvent("TDLIB_PROXY_PING_SUCCESS", state.accountId, null, null, null, seconds == null ? null : Math.round(seconds.seconds * 1000), null);
        } catch (RuntimeException error) {
            state.proxyPingStatus.set("FAILED");
            state.proxyLastError.set(sanitizeProxyError(error.getMessage()));
            readService.recordRuntimeError("WARN", state.accountId, null, null, "TDLIB_PROXY_PING_FAILED", "TDLib proxy ping failed", null, sanitizeProxyError(error.getMessage()), null);
            readService.recordRuntimeEvent("TDLIB_PROXY_PING_FAILED", state.accountId, null, null, null, null, null);
        }
    }

    public ProxyRuntimeState proxyRuntimeState() {
        boolean configured = states.values().stream().anyMatch(state -> state.proxyConfiguredInTdlib.get());
        String status = states.values().stream()
                .map(state -> state.proxyPingStatus.get())
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> !"NOT_TESTED".equals(value))
                .findFirst()
                .orElse("NOT_TESTED");
        String lastError = states.values().stream()
                .map(state -> state.proxyLastError.get())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        return new ProxyRuntimeState(configured, status, lastError);
    }

    private String safeProxyType() {
        return properties.getProxyType() == null ? "HTTP" : properties.getProxyType().trim().toUpperCase(Locale.ROOT);
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String sanitizeProxyError(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value;
        if (properties.getProxyPassword() != null && !properties.getProxyPassword().isBlank()) {
            sanitized = sanitized.replace(properties.getProxyPassword(), "[REDACTED]");
        }
        if (properties.getProxyUsername() != null && !properties.getProxyUsername().isBlank()) {
            sanitized = sanitized.replace(properties.getProxyUsername(), "[REDACTED]");
        }
        return sanitized;
    }

    private TelegramAuthStateResponse authResponse(ClientState state) {
        TdApi.AuthorizationState authorizationState = state.authorizationState.get();
        String stateName = stateName(state);
        return new TelegramAuthStateResponse(
                stateName,
                accountStatus(authorizationState),
                authorizationState instanceof TdApi.AuthorizationStateReady,
                authorizationState instanceof TdApi.AuthorizationStateWaitPhoneNumber,
                authorizationState instanceof TdApi.AuthorizationStateWaitCode,
                authorizationState instanceof TdApi.AuthorizationStateWaitPassword,
                state.lastError.get()
        );
    }

    private String accountStatus(TdApi.AuthorizationState state) {
        if (state instanceof TdApi.AuthorizationStateReady) {
            return "CONNECTED";
        }
        if (state instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            return "WAIT_PHONE";
        }
        if (state instanceof TdApi.AuthorizationStateWaitCode) {
            return "WAIT_CODE";
        }
        if (state instanceof TdApi.AuthorizationStateWaitPassword) {
            return "WAIT_PASSWORD";
        }
        if (state instanceof TdApi.AuthorizationStateWaitTdlibParameters || state instanceof TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
            return "CONNECTING";
        }
        if (state instanceof TdApi.AuthorizationStateClosed || state instanceof TdApi.AuthorizationStateClosing) {
            return "DISCONNECTED";
        }
        return "AUTH_REQUIRED";
    }

    private void refreshAuthorizationStateIfMissing(ClientState state) {
        if (state.authorizationState.get() != null) {
            return;
        }
        onAuthorizationStateUpdated(state, send(state, new TdApi.GetAuthorizationState()));
    }

    private void awaitAuthorizationStateChange(ClientState state, long previousVersion) {
        long remaining = TimeUnit.SECONDS.toNanos(REQUEST_TIMEOUT_SECONDS);
        state.authorizationLock.lock();
        try {
            while (state.authorizationStateVersion.get() == previousVersion && remaining > 0) {
                remaining = state.authorizationChanged.awaitNanos(remaining);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new TdlibException("Interrupted while waiting for TDLib authorization state", error);
        } finally {
            state.authorizationLock.unlock();
        }
    }

    private void awaitAuthorizationState(ClientState state, Predicate<TdApi.AuthorizationState> predicate, String expectedState) {
        long remaining = TimeUnit.SECONDS.toNanos(REQUEST_TIMEOUT_SECONDS);
        state.authorizationLock.lock();
        try {
            while (!predicate.test(state.authorizationState.get()) && remaining > 0) {
                remaining = state.authorizationChanged.awaitNanos(remaining);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new TdlibException("Interrupted while waiting for TDLib authorization state", error);
        } finally {
            state.authorizationLock.unlock();
        }
        if (!predicate.test(state.authorizationState.get())) {
            throw new TdlibException("Timed out waiting for " + expectedState + ". Current authorization state: " + stateName(state));
        }
    }

    private void loadChats(ClientState state, int limit) {
        loadChats(state, limit, REQUEST_TIMEOUT_SECONDS);
    }

    private void loadChats(ClientState state, int limit, long timeoutSeconds) {
        TdApi.Object response = sendRaw(state, new TdApi.LoadChats(new TdApi.ChatListMain(), normalizeLimit(limit, properties.getChatLimit())), timeoutSeconds);
        if (response instanceof TdApi.Error error && error.code != 404) {
            state.lastError.set(error.message);
            throw new TdlibException("TDLib LoadChats failed: " + error.code + " " + error.message);
        }
    }

    private TelegramChatDto toChatDto(ClientState state, TdApi.Chat chat) {
        boolean forum = false;
        String title = chat.title == null ? "" : chat.title;
        String username = null;
        String sourceType = "GROUP";
        if (chat.type instanceof TdApi.ChatTypePrivate privateType) {
            TdApi.User user = getUserSafe(state, privateType.userId);
            title = displayName(user, "User " + privateType.userId);
            username = firstUsername(user == null ? null : user.usernames);
            sourceType = "DIRECT_CHAT";
        } else if (chat.type instanceof TdApi.ChatTypeSecret secretType) {
            TdApi.User user = getUserSafe(state, secretType.userId);
            title = displayName(user, "User " + secretType.userId);
            username = firstUsername(user == null ? null : user.usernames);
            sourceType = "DIRECT_CHAT";
        } else if (chat.type instanceof TdApi.ChatTypeSupergroup supergroupType) {
            TdApi.Supergroup supergroup = getSupergroupSafe(state, supergroupType.supergroupId);
            if (supergroup != null) {
                forum = supergroup.isForum;
                username = firstUsername(supergroup.usernames);
                sourceType = supergroup.isChannel ? "CHANNEL" : "GROUP";
            }
        }
        return new TelegramChatDto(
                chat.id,
                title,
                username,
                chat.type == null ? "Unknown" : chat.type.getClass().getSimpleName(),
                sourceType,
                forum,
                chat.lastMessage == null ? 0L : chat.lastMessage.id
        );
    }

    private TelegramChatDto toChatDtoFast(TdApi.Chat chat) {
        String sourceType = "GROUP";
        if (chat.type instanceof TdApi.ChatTypePrivate || chat.type instanceof TdApi.ChatTypeSecret) {
            sourceType = "DIRECT_CHAT";
        }
        return new TelegramChatDto(
                chat.id,
                chat.title == null || chat.title.isBlank() ? "Chat " + chat.id : chat.title,
                null,
                chat.type == null ? "Unknown" : chat.type.getClass().getSimpleName(),
                sourceType,
                false,
                chat.lastMessage == null ? 0L : chat.lastMessage.id
        );
    }

    private TdApi.Chat getChat(ClientState state, long chatId) {
        TdApi.Chat cached = state.chatCache.get(chatId);
        if (cached != null) {
            return cached;
        }
        TdApi.Chat chat = send(state, new TdApi.GetChat(chatId));
        state.chatCache.put(chat.id, chat);
        return chat;
    }

    private TdApi.User getUserSafe(ClientState state, long userId) {
        TdApi.User cached = state.userCache.get(userId);
        if (cached != null) {
            return cached;
        }
        try {
            TdApi.User user = send(state, new TdApi.GetUser(userId));
            state.userCache.put(user.id, user);
            return user;
        } catch (Exception ignored) {
            return null;
        }
    }

    private TdApi.Supergroup getSupergroupSafe(ClientState state, long supergroupId) {
        TdApi.Supergroup cached = state.supergroupCache.get(supergroupId);
        if (cached != null) {
            return cached;
        }
        try {
            TdApi.Supergroup supergroup = send(state, new TdApi.GetSupergroup(supergroupId));
            state.supergroupCache.put(supergroup.id, supergroup);
            return supergroup;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String cachedSenderName(ClientState state, TdApi.Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser senderUser) {
            TdApi.User user = state.userCache.get(senderUser.userId);
            return displayName(user, "User " + senderUser.userId);
        }
        if (message.senderId instanceof TdApi.MessageSenderChat senderChat) {
            TdApi.Chat chat = state.chatCache.get(senderChat.chatId);
            return chat == null ? "Chat " + senderChat.chatId : chat.title;
        }
        return null;
    }

    private String cachedSenderUsername(ClientState state, TdApi.Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser senderUser) {
            TdApi.User user = state.userCache.get(senderUser.userId);
            return firstUsername(user == null ? null : user.usernames);
        }
        return null;
    }

    private boolean cachedSenderIsBot(ClientState state, TdApi.Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser senderUser) {
            TdApi.User user = state.userCache.get(senderUser.userId);
            return user != null && user.type instanceof TdApi.UserTypeBot;
        }
        return false;
    }

    private String displayName(TdApi.User user, String fallback) {
        if (user == null) {
            return fallback;
        }
        String name = ((user.firstName == null ? "" : user.firstName.trim()) + " " + (user.lastName == null ? "" : user.lastName.trim())).trim();
        if (!name.isBlank()) {
            return name;
        }
        String username = firstUsername(user.usernames);
        return username == null ? fallback : "@" + username;
    }

    private String firstUsername(TdApi.Usernames usernames) {
        if (usernames == null || usernames.activeUsernames == null) {
            return null;
        }
        for (String username : usernames.activeUsernames) {
            if (username != null && !username.isBlank()) {
                return username.trim();
            }
        }
        return null;
    }

    private long topicId(TdApi.MessageTopic topic, long fallback) {
        if (topic instanceof TdApi.MessageTopicThread thread) {
            return thread.messageThreadId;
        }
        if (topic instanceof TdApi.MessageTopicForum forum) {
            return forum.forumTopicId;
        }
        return fallback;
    }

    private <T extends TdApi.Object> T send(ClientState state, TdApi.Function<T> function) {
        return send(state, function, REQUEST_TIMEOUT_SECONDS);
    }

    private <T extends TdApi.Object> T send(ClientState state, TdApi.Function<T> function, long timeoutSeconds) {
        TdApi.Object object = sendRaw(state, function, timeoutSeconds);
        if (object instanceof TdApi.Error error) {
            state.lastError.set(error.message);
            throw new TdlibException("TDLib request failed: " + error.code + " " + error.message);
        }
        @SuppressWarnings("unchecked")
        T casted = (T) object;
        return casted;
    }

    private TdApi.Object sendRaw(ClientState state, TdApi.Function<?> function, long timeoutSeconds) {
        Client client = state.client;
        if (client == null) {
            throw new TdlibException("TDLib client is not initialized");
        }
        CompletableFuture<TdApi.Object> future = new CompletableFuture<>();
        client.send(function, future::complete, future::completeExceptionally);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception error) {
            throw new TdlibException("Timed out while waiting for TDLib response", error);
        }
    }

    private void sendExpectOk(ClientState state, TdApi.Function<TdApi.Ok> function) {
        send(state, function);
    }

    private void sendWithoutResult(ClientState state, TdApi.Function<?> function) {
        Client client = state.client;
        if (client == null) {
            return;
        }
        client.send(function, object -> {
            if (object instanceof TdApi.Error error) {
                state.lastError.set(error.message);
            }
        });
    }

    private void createDirectories(ClientState state) throws IOException {
        Files.createDirectories(databaseDirectory(state).toAbsolutePath());
        Files.createDirectories(filesDirectory(state).toAbsolutePath());
    }

    private void configureTdlibLogging() {
        try {
            Path path = Path.of("./logs/backend-2-tdlib-native.log").toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            Client.execute(new TdApi.SetLogStream(new TdApi.LogStreamFile(path.toString(), TDLIB_NATIVE_LOG_MAX_FILE_SIZE, true)));
        } catch (Exception error) {
            log.warn("Failed to configure TDLib native log stream: {}", error.getMessage());
        }
    }

    private Path databaseDirectory(ClientState state) {
        return Path.of(properties.getDatabaseDirectory()).resolve(String.valueOf(state.accountId)).resolve("db");
    }

    private Path filesDirectory(ClientState state) {
        return Path.of(properties.getFilesDirectory()).resolve(String.valueOf(state.accountId)).resolve("files");
    }

    private ClientState clientState(long accountId) {
        return states.computeIfAbsent(accountId, ClientState::new);
    }

    private String stateName(ClientState state) {
        TdApi.AuthorizationState authorizationState = state.authorizationState.get();
        return authorizationState == null ? "not_initialized" : authorizationState.getClass().getSimpleName();
    }

    private int normalizeLimit(int limit, int fallback) {
        if (limit <= 0) {
            return fallback;
        }
        return Math.min(limit, 100);
    }

    private boolean isFloodWait(Throwable error) {
        String message = error.getMessage();
        return message != null && message.toUpperCase().contains("FLOOD_WAIT");
    }

    private void incrementDropped(long accountId) {
        jdbc.update("""
                INSERT INTO telegram_account_health (account_id, dropped_update_count, updated_at)
                VALUES (?, 1, now())
                ON CONFLICT (account_id)
                DO UPDATE SET dropped_update_count = telegram_account_health.dropped_update_count + 1, updated_at = now()
                """, accountId);
    }

    public record BackfillResult(boolean scheduled, int persisted, long telegramChatId) {
    }

    public record ProxyRuntimeState(boolean configuredInTdlib, String pingStatus, String lastError) {
    }

    public record ChatSyncStatus(long accountId, String syncId, String status, String startedAt, String finishedAt, int loadedCount, int storedCount, int groupsCount, int channelsCount, int privateCount, Long lastChatId, String lastError, long durationMs) {
    }

    public record ChatTdlibDiagnostic(long accountId, boolean clientAlive, long knownDbChats, Long knownChatId, boolean getKnownChatOk, Long getKnownChatMs, String getKnownChatError, boolean getChatHistoryLimit1Ok, Long getChatHistoryLimit1Ms, String getChatHistoryError, boolean loadChatsBatchOk, Long loadChatsBatchMs, String loadChatsError, String recommendation, long durationMs) {
    }

    public record TopicSyncStatus(long accountId, long chatId, String syncId, String status, String startedAt, String finishedAt, int loadedCount, int storedCount, int pagesLoaded, String lastError, boolean usableTopics, String recommendation, long durationMs) {
    }

    private static final class TopicSyncJob {
        private final long accountId;
        private final long chatId;
        private final String syncId;
        private final int limit;
        private final Instant startedAt = Instant.now();
        private volatile Instant finishedAt;
        private final AtomicReference<String> status = new AtomicReference<>("RUNNING");
        private final AtomicInteger loadedCount = new AtomicInteger(0);
        private final AtomicInteger storedCount = new AtomicInteger(0);
        private final AtomicInteger pagesLoaded = new AtomicInteger(0);
        private final AtomicReference<String> lastError = new AtomicReference<>();
        private final AtomicBoolean usableTopics = new AtomicBoolean(false);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private TopicSyncJob(long accountId, long chatId, String syncId, int limit) {
            this.accountId = accountId;
            this.chatId = chatId;
            this.syncId = syncId;
            this.limit = limit;
        }

        private long accountId() { return accountId; }
        private long chatId() { return chatId; }
        private String syncId() { return syncId; }
        private int limit() { return limit; }
        private boolean running() { return "RUNNING".equals(status.get()); }
        private long durationMs() { return java.time.Duration.between(startedAt, finishedAt == null ? Instant.now() : finishedAt).toMillis(); }
        private void finish(String finalStatus, String error, boolean usable) { status.set(finalStatus); finishedAt = Instant.now(); lastError.set(error); usableTopics.set(usable); }
        private TopicSyncStatus status() { return new TopicSyncStatus(accountId, chatId, syncId, status.get(), startedAt.toString(), finishedAt == null ? null : finishedAt.toString(), loadedCount.get(), storedCount.get(), pagesLoaded.get(), lastError.get(), usableTopics.get(), usableTopics.get() ? "Use stored topics from DB cache" : null, durationMs()); }
    }

    private static final class ChatSyncJob {
        private final long accountId;
        private final String syncId;
        private final int requestedLimit;
        private final Instant startedAt = Instant.now();
        private volatile Instant finishedAt;
        private final AtomicReference<String> status = new AtomicReference<>("RUNNING");
        private final AtomicInteger loadedCount = new AtomicInteger(0);
        private final AtomicInteger storedCount = new AtomicInteger(0);
        private final AtomicInteger groupsCount = new AtomicInteger(0);
        private final AtomicInteger channelsCount = new AtomicInteger(0);
        private final AtomicInteger privateCount = new AtomicInteger(0);
        private final AtomicReference<Long> lastChatId = new AtomicReference<>();
        private final AtomicReference<String> lastError = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private ChatSyncJob(long accountId, String syncId, int requestedLimit) {
            this.accountId = accountId;
            this.syncId = syncId;
            this.requestedLimit = requestedLimit;
        }

        private long accountId() { return accountId; }
        private String syncId() { return syncId; }
        private int requestedLimit() { return requestedLimit; }
        private boolean running() { return "RUNNING".equals(status.get()); }
        private long durationMs() { return java.time.Duration.between(startedAt, finishedAt == null ? Instant.now() : finishedAt).toMillis(); }

        private void finish(String finalStatus, String error) {
            status.set(finalStatus);
            finishedAt = Instant.now();
            if (error != null) {
                lastError.set(error);
            }
        }

        private ChatSyncStatus status() {
            return new ChatSyncStatus(
                    accountId,
                    syncId,
                    status.get(),
                    startedAt.toString(),
                    finishedAt == null ? null : finishedAt.toString(),
                    loadedCount.get(),
                    storedCount.get(),
                    groupsCount.get(),
                    channelsCount.get(),
                    privateCount.get(),
                    lastChatId.get(),
                    lastError.get(),
                    durationMs()
            );
        }
    }

    private static final class ClientState {
        private final long accountId;
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicReference<TdApi.AuthorizationState> authorizationState = new AtomicReference<>();
        private final AtomicReference<String> lastError = new AtomicReference<>();
        private final AtomicLong authorizationStateVersion = new AtomicLong(0);
        private final AtomicBoolean tdlibParametersSent = new AtomicBoolean(false);
        private final AtomicBoolean proxySetupStarted = new AtomicBoolean(false);
        private final AtomicBoolean proxyConfiguredInTdlib = new AtomicBoolean(false);
        private final AtomicReference<String> proxyPingStatus = new AtomicReference<>("NOT_TESTED");
        private final AtomicReference<String> proxyLastError = new AtomicReference<>();
        private final ReentrantLock authorizationLock = new ReentrantLock();
        private final Condition authorizationChanged = authorizationLock.newCondition();
        private final ConcurrentMap<Long, TdApi.Chat> chatCache = new ConcurrentHashMap<>();
        private final ConcurrentMap<Long, TdApi.User> userCache = new ConcurrentHashMap<>();
        private final ConcurrentMap<Long, TdApi.Supergroup> supergroupCache = new ConcurrentHashMap<>();
        private final ConcurrentMap<Long, Map<Long, TelegramTopicDto>> topicCache = new ConcurrentHashMap<>();
        private volatile Client client;

        private ClientState(long accountId) {
            this.accountId = accountId;
        }
    }
}
