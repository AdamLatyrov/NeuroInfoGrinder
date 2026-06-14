package com.larbcorp.neuroinfogrinder.telegram.tdlib;

import com.larbcorp.neuroinfogrinder.config.TdlibProperties;
import com.larbcorp.neuroinfogrinder.telegram.TelegramUpdateListener;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramAuthStateResponse;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramChatDto;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramChatMessagesDto;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessagesBatchResponse;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramTopicDto;
import jakarta.annotation.PreDestroy;
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class TdlibClientManager {

    private static final long REQUEST_TIMEOUT_SECONDS = 30L;
    private static final long CHAT_HISTORY_TIMEOUT_SECONDS = 8L;
    private static final long TDLIB_NATIVE_LOG_MAX_FILE_SIZE = 50L * 1024L * 1024L;
    private static final Logger log = LoggerFactory.getLogger(TdlibClientManager.class);

    private final TdlibProperties properties;
    private final List<TelegramUpdateListener> updateListeners;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicReference<TdApi.AuthorizationState> authorizationState = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicLong authorizationStateVersion = new AtomicLong(0);
    private final ReentrantLock authorizationLock = new ReentrantLock();
    private final Condition authorizationChanged = authorizationLock.newCondition();
    private final ConcurrentMap<Long, TdApi.Chat> chatCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, TdApi.User> userCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, TdApi.Supergroup> supergroupCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ConcurrentMap<Long, String>> topicNameCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ConcurrentMap<Long, TelegramTopicDto>> topicCache = new ConcurrentHashMap<>();

    private volatile Client client;

    public TdlibClientManager(TdlibProperties properties, List<TelegramUpdateListener> updateListeners) {
        this.properties = properties;
        this.updateListeners = updateListeners;
    }

    public TelegramAuthStateResponse getAuthorizationState() {
        ensureStarted();
        String stateName = getAuthorizationStateName();
        return new TelegramAuthStateResponse(
                stateName,
                "AuthorizationStateReady".equals(stateName),
                "AuthorizationStateWaitPhoneNumber".equals(stateName),
                "AuthorizationStateWaitCode".equals(stateName),
                "AuthorizationStateWaitPassword".equals(stateName),
                lastError.get()
        );
    }

    public TelegramAuthStateResponse submitPhoneNumber(String phoneNumber) {
        ensureStarted();
        long previousVersion = authorizationStateVersion.get();
        sendExpectOk(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null));
        awaitAuthorizationStateChange(previousVersion);
        return getAuthorizationState();
    }

    public TelegramAuthStateResponse submitCode(String code) {
        ensureStarted();
        long previousVersion = authorizationStateVersion.get();
        sendExpectOk(new TdApi.CheckAuthenticationCode(code));
        awaitAuthorizationStateChange(previousVersion);
        return getAuthorizationState();
    }

    public TelegramAuthStateResponse submitPassword(String password) {
        ensureStarted();
        long previousVersion = authorizationStateVersion.get();
        sendExpectOk(new TdApi.CheckAuthenticationPassword(password));
        awaitAuthorizationStateChange(previousVersion);
        return getAuthorizationState();
    }

    public List<TelegramChatDto> getChats(int limit) {
        ensureReady();
        loadChats(limit);

        TdApi.Chats chats = send(new TdApi.GetChats(new TdApi.ChatListMain(), normalizeLimit(limit, properties.getChatLimit())));
        List<TelegramChatDto> result = new ArrayList<>();
        if (chats.chatIds != null) {
            for (long chatId : chats.chatIds) {
                result.add(toChatDto(getChat(chatId)));
            }
        }
        result.sort(Comparator.comparing(TelegramChatDto::title, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public List<TelegramTopicDto> getTopics(long chatId, int limit) {
        ensureReady();
        TdApi.ForumTopics forumTopics = send(new TdApi.GetForumTopics(
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
            long compatibilityThreadId = resolveTopicCompatibilityThreadId(topic);
            cacheTopicName(chatId, topic.info.forumTopicId, topic.info.name);
            cacheTopicName(chatId, compatibilityThreadId, topic.info.name);
            TelegramTopicDto dto = new TelegramTopicDto(
                    chatId,
                    topic.info.forumTopicId,
                    compatibilityThreadId,
                    topic.info.name,
                    topic.info.isGeneral
            );
            cacheTopic(chatId, dto);
            topics.add(dto);
        }
        return topics;
    }

    public List<TelegramTopicDto> getCachedTopics(long chatId, int limit) {
        List<TelegramTopicDto> topics = new ArrayList<>(
                Optional.ofNullable(topicCache.get(chatId))
                        .map(cache -> cache.values().stream().distinct().toList())
                        .orElseGet(List::of)
        );
        topics.sort(Comparator
                .comparing(TelegramTopicDto::general).reversed()
                .thenComparing(TelegramTopicDto::name, String.CASE_INSENSITIVE_ORDER));
        if (topics.size() > limit) {
            return topics.subList(0, limit);
        }
        return topics;
    }

    public List<TelegramMessageDto> getMessages(long chatId, long fromMessageId, int limit) {
        ensureReady();
        TdApi.Messages messages;
        try {
            messages = getChatHistoryWithFallback(chatId, fromMessageId, limit);
        } catch (TdlibException exception) {
            Optional<TdApi.Message> fallbackMessage = getLatestChatMessage(chatId);
            if (fallbackMessage.isPresent()) {
                log.warn("Falling back to lastMessage for chat {} after history timeout", chatId);
                List<TelegramMessageDto> singleMessage = new ArrayList<>();
                singleMessage.add(toMessageDto(chatId, fallbackMessage.get()));
                return singleMessage;
            }
            throw exception;
        }

        List<TelegramMessageDto> result = new ArrayList<>();
        if (messages.messages == null) {
            return result;
        }

        for (TdApi.Message message : messages.messages) {
            if (message != null) {
                result.add(toMessageDto(chatId, message));
            }
        }
        return result;
    }

    private Optional<TdApi.Message> getLatestChatMessage(long chatId) {
        try {
            TdApi.Chat chat = getChat(chatId);
            return Optional.ofNullable(chat.lastMessage);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private TdApi.Messages getChatHistoryWithFallback(long chatId, long fromMessageId, int limit) {
        int normalizedLimit = normalizeLimit(limit, 100);
        int[] retryLimits = new int[] {
                normalizedLimit,
                Math.min(normalizedLimit, 20),
                Math.min(normalizedLimit, 10),
                5
        };

        TdlibException lastException = null;
        for (int attemptLimit : retryLimits) {
            if (attemptLimit <= 0) {
                continue;
            }

            try {
                return send(new TdApi.GetChatHistory(
                        chatId,
                        fromMessageId,
                        0,
                        attemptLimit,
                        false
                ), CHAT_HISTORY_TIMEOUT_SECONDS);
            } catch (TdlibException exception) {
                lastException = exception;
                if (!isTimeout(exception) || attemptLimit <= 5) {
                    break;
                }
                log.warn(
                        "TDLib chat history timeout for chat {} with limit {}, retrying with smaller batch",
                        chatId,
                        attemptLimit
                );
            }
        }

        throw lastException != null ? lastException : new TdlibException("Failed to fetch chat history for chat " + chatId);
    }

    private boolean isTimeout(TdlibException exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("timed out");
    }

    public TelegramMessagesBatchResponse getMessagesForChats(List<Long> chatIds, int limitPerChat) {
        ensureReady();
        List<TelegramChatMessagesDto> items = new ArrayList<>();
        for (Long chatId : chatIds) {
            TdApi.Chat chat = getChat(chatId);
            items.add(new TelegramChatMessagesDto(
                    chatId,
                    chat.title,
                    getMessages(chatId, 0, limitPerChat)
            ));
        }
        return new TelegramMessagesBatchResponse(items);
    }

    public Optional<TelegramMessageDto> getLatestMessage(long chatId) {
        ensureReady();
        return getLatestChatMessage(chatId).map(message -> toMessageDto(chatId, message));
    }

    @PreDestroy
    public void shutdown() {
        Client localClient = client;
        if (localClient != null) {
            try {
                localClient.send(new TdApi.Close(), object -> {
                });
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureStarted() {
        if (!properties.isEnabled()) {
            throw new TdlibException("TDLib is disabled. Set telegram.tdlib.enabled=true");
        }
        if (properties.getApiId() <= 0 || properties.getApiHash().isBlank()) {
            throw new TdlibException("TDLib apiId/apiHash are not configured");
        }

        if (started.compareAndSet(false, true)) {
            try {
                createDirectories();
                TdlibNativeLoader.load(properties);
                configureTdlibLogging();
                Client.setLogMessageHandler(0, null);
                client = Client.create(this::handleUpdate, null, throwable -> lastError.set(throwable.getMessage()));
                send(new TdApi.GetAuthorizationState());
            } catch (Exception exception) {
                started.set(false);
                throw new TdlibException("Failed to initialize TDLib JNI client", exception);
            }
        }
    }

    private void ensureReady() {
        ensureStarted();
        String stateName = getAuthorizationStateName();
        if (!"AuthorizationStateReady".equals(stateName)) {
            throw new TdlibException("TDLib is not ready. Current authorization state: " + stateName);
        }
    }

    private void createDirectories() throws IOException {
        Files.createDirectories(Path.of(properties.getDatabaseDirectory()).toAbsolutePath());
        Files.createDirectories(Path.of(properties.getFilesDirectory()).toAbsolutePath());
    }

    private void configureTdlibLogging() {
        Path tdlibLogPath = Path.of("./logs/tdlib-native.log").toAbsolutePath().normalize();
        try {
            Files.createDirectories(tdlibLogPath.getParent());
            Client.execute(new TdApi.SetLogStream(
                    new TdApi.LogStreamFile(tdlibLogPath.toString(), TDLIB_NATIVE_LOG_MAX_FILE_SIZE, true)
            ));
        } catch (Exception exception) {
            log.warn("Failed to redirect TDLib native log stream to {}", tdlibLogPath, exception);
        }
    }

    private void handleUpdate(TdApi.Object object) {
        if (object == null) {
            return;
        }

        if (object instanceof TdApi.UpdateAuthorizationState updateAuthorizationState) {
            onAuthorizationStateUpdated(updateAuthorizationState.authorizationState);
            return;
        }
        if (object instanceof TdApi.UpdateNewChat updateNewChat) {
            chatCache.put(updateNewChat.chat.id, updateNewChat.chat);
            return;
        }
        if (object instanceof TdApi.UpdateUser updateUser) {
            userCache.put(updateUser.user.id, updateUser.user);
            return;
        }
        if (object instanceof TdApi.UpdateChatTitle updateChatTitle) {
            TdApi.Chat chat = chatCache.get(updateChatTitle.chatId);
            if (chat != null) {
                chat.title = updateChatTitle.title;
            }
            return;
        }
        if (object instanceof TdApi.UpdateForumTopicInfo updateForumTopicInfo) {
            cacheTopicName(updateForumTopicInfo.info.chatId, updateForumTopicInfo.info.forumTopicId, updateForumTopicInfo.info.name);
            cacheTopic(updateForumTopicInfo.info.chatId, new TelegramTopicDto(
                    updateForumTopicInfo.info.chatId,
                    updateForumTopicInfo.info.forumTopicId,
                    updateForumTopicInfo.info.forumTopicId,
                    updateForumTopicInfo.info.name,
                    updateForumTopicInfo.info.forumTopicId == 1L
            ));
            return;
        }
        if (object instanceof TdApi.UpdateNewMessage updateNewMessage) {
            TelegramMessageDto message = toRealtimeMessageDto(updateNewMessage.message.chatId, updateNewMessage.message);
            for (TelegramUpdateListener listener : updateListeners) {
                try {
                    listener.onNewMessage(message);
                } catch (Exception exception) {
                    lastError.set(exception.getMessage());
                }
            }
        }
    }

    private void onAuthorizationStateUpdated(TdApi.AuthorizationState state) {
        authorizationState.set(state);
        authorizationStateVersion.incrementAndGet();
        authorizationLock.lock();
        try {
            authorizationChanged.signalAll();
        } finally {
            authorizationLock.unlock();
        }

        if (state instanceof TdApi.AuthorizationStateWaitTdlibParameters) {
            sendWithoutResult(buildTdlibParameters());
            return;
        }
        if (state instanceof TdApi.AuthorizationStateClosed) {
            started.set(false);
        }
    }

    private TdApi.SetTdlibParameters buildTdlibParameters() {
        TdApi.SetTdlibParameters parameters = new TdApi.SetTdlibParameters();
        parameters.useTestDc = properties.isUseTestDc();
        parameters.databaseDirectory = Path.of(properties.getDatabaseDirectory()).toAbsolutePath().toString();
        parameters.filesDirectory = Path.of(properties.getFilesDirectory()).toAbsolutePath().toString();
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

    private void awaitAuthorizationStateChange(long previousVersion) {
        long remainingNanos = TimeUnit.SECONDS.toNanos(REQUEST_TIMEOUT_SECONDS);
        authorizationLock.lock();
        try {
            while (authorizationStateVersion.get() == previousVersion && remainingNanos > 0) {
                remainingNanos = authorizationChanged.awaitNanos(remainingNanos);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TdlibException("Interrupted while waiting for authorization state update", exception);
        } finally {
            authorizationLock.unlock();
        }
    }

    private void loadChats(int limit) {
        TdApi.Object response = sendRaw(new TdApi.LoadChats(new TdApi.ChatListMain(), normalizeLimit(limit, properties.getChatLimit())));
        if (response instanceof TdApi.Error error && error.code != 404) {
            lastError.set(error.message);
            throw new TdlibException("TDLib request failed: " + error.code + " " + error.message);
        }
    }

    private TdApi.Chat getChat(long chatId) {
        TdApi.Chat cachedChat = chatCache.get(chatId);
        if (cachedChat != null) {
            return cachedChat;
        }

        TdApi.Chat chat = send(new TdApi.GetChat(chatId));
        chatCache.put(chat.id, chat);
        return chat;
    }

    private TdApi.Supergroup getSupergroup(long supergroupId) {
        TdApi.Supergroup cachedSupergroup = supergroupCache.get(supergroupId);
        if (cachedSupergroup != null) {
            return cachedSupergroup;
        }

        TdApi.Supergroup supergroup = send(new TdApi.GetSupergroup(supergroupId));
        supergroupCache.put(supergroup.id, supergroup);
        return supergroup;
    }

    private void sendExpectOk(TdApi.Function<TdApi.Ok> function) {
        send(function);
    }

    private void sendWithoutResult(TdApi.Function<?> function) {
        Client localClient = requireClient();
        localClient.send(function, object -> {
            if (object instanceof TdApi.Error error) {
                lastError.set(error.message);
            }
        });
    }

    private <T extends TdApi.Object> T send(TdApi.Function<T> function) {
        TdApi.Object object = sendRaw(function, REQUEST_TIMEOUT_SECONDS);
        if (object instanceof TdApi.Error error) {
            lastError.set(error.message);
            throw new TdlibException("TDLib request failed: " + error.code + " " + error.message);
        }
        @SuppressWarnings("unchecked")
        T casted = (T) object;
        return casted;
    }

    private <T extends TdApi.Object> T send(TdApi.Function<T> function, long timeoutSeconds) {
        TdApi.Object object = sendRaw(function, timeoutSeconds);
        if (object instanceof TdApi.Error error) {
            lastError.set(error.message);
            throw new TdlibException("TDLib request failed: " + error.code + " " + error.message);
        }
        @SuppressWarnings("unchecked")
        T casted = (T) object;
        return casted;
    }

    private TdApi.Object sendRaw(TdApi.Function<?> function) {
        return sendRaw(function, REQUEST_TIMEOUT_SECONDS);
    }

    private TdApi.Object sendRaw(TdApi.Function<?> function, long timeoutSeconds) {
        ensureStartedWithoutRecursion();
        Client localClient = requireClient();
        CompletableFuture<TdApi.Object> future = new CompletableFuture<>();
        localClient.send(function, object -> future.complete(object), throwable -> future.completeExceptionally(throwable));

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TdlibException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TdlibException("Timed out while waiting for TDLib JNI response", exception);
        }
    }

    private void ensureStartedWithoutRecursion() {
        if (!started.get()) {
            ensureStarted();
        }
    }

    private Client requireClient() {
        Client localClient = client;
        if (localClient == null) {
            throw new TdlibException("TDLib client is not initialized");
        }
        return localClient;
    }

    private TelegramChatDto toChatDto(TdApi.Chat chat) {
        boolean forum = false;
        String title = chat.title == null ? "" : chat.title;
        String username = null;
        String sourceType = "GROUP";

        if (chat.type instanceof TdApi.ChatTypePrivate privateType) {
            title = resolveUserDisplayName(privateType.userId);
            username = resolveUserUsername(privateType.userId);
            sourceType = "DIRECT_CHAT";
        } else if (chat.type instanceof TdApi.ChatTypeSecret secretType) {
            title = resolveUserDisplayName(secretType.userId);
            username = resolveUserUsername(secretType.userId);
            sourceType = "DIRECT_CHAT";
        }

        if (chat.type instanceof TdApi.ChatTypeSupergroup supergroupType) {
            TdApi.Supergroup supergroup = getSupergroup(supergroupType.supergroupId);
            forum = supergroup.isForum;
            username = firstActiveUsername(supergroup.usernames);
            sourceType = supergroup.isChannel ? "CHANNEL" : "GROUP";
        } else if (chat.type instanceof TdApi.ChatTypeBasicGroup) {
            sourceType = "GROUP";
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

    private TelegramMessageDto toMessageDto(long chatId, TdApi.Message message) {
        long topicKey = extractTopicKey(message.topicId);
        String topicName = resolveTopicName(chatId, message.topicId);
        String senderName = extractSenderName(message);
        Long senderTelegramUserId = extractSenderUserId(message);
        boolean isBot = message.senderId instanceof TdApi.MessageSenderUser senderUser
                && getUser(senderUser.userId).type instanceof TdApi.UserTypeBot;
        return new TelegramMessageDto(
                message.id,
                chatId,
                topicKey,
                topicName,
                message.content == null ? "Unknown" : message.content.getClass().getSimpleName(),
                extractMessageText(message.content),
                senderName,
                senderTelegramUserId,
                isBot,
                extractReplyToMessageId(message),
                message.date > 0 ? message.date : Instant.now().getEpochSecond()
        );
    }

    private TelegramMessageDto toRealtimeMessageDto(long chatId, TdApi.Message message) {
        long topicKey = extractTopicKey(message.topicId);
        String topicName = resolveTopicName(chatId, message.topicId);
        String senderName = extractSenderName(message);
        Long senderTelegramUserId = extractSenderUserId(message);
        boolean isBot = message.senderId instanceof TdApi.MessageSenderUser senderUser
                && getUser(senderUser.userId).type instanceof TdApi.UserTypeBot;

        return new TelegramMessageDto(
                message.id,
                chatId,
                topicKey,
                topicName,
                message.content == null ? "Unknown" : message.content.getClass().getSimpleName(),
                extractMessageText(message.content),
                senderName,
                senderTelegramUserId,
                isBot,
                extractReplyToMessageId(message),
                message.date > 0 ? message.date : Instant.now().getEpochSecond()
        );
    }

    private long extractReplyToMessageId(TdApi.Message message) {
        if (message.replyTo instanceof TdApi.MessageReplyToMessage replyToMessage) {
            return replyToMessage.messageId;
        }
        return 0L;
    }

    private String extractSenderName(TdApi.Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser senderUser) {
            return resolveUserDisplayName(senderUser.userId);
        }
        if (message.senderId instanceof TdApi.MessageSenderChat senderChat) {
            try {
                TdApi.Chat chat = getChat(senderChat.chatId);
                if (chat.title != null && !chat.title.isBlank()) {
                    return chat.title;
                }
            } catch (TdlibException ignored) {
                TdApi.Chat chat = chatCache.get(senderChat.chatId);
                if (chat != null && chat.title != null && !chat.title.isBlank()) {
                    return chat.title;
                }
            }
        }
        return null;
    }

    private Long extractSenderUserId(TdApi.Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser senderUser) {
            return senderUser.userId;
        }
        return null;
    }

    public String resolveUserDisplayName(long userId) {
        TdApi.User user = getUser(userId);
        String displayName = formatUserDisplayName(user);
        return displayName != null ? displayName : "User " + userId;
    }

    public String resolveUserUsername(long userId) {
        return firstActiveUsername(getUser(userId).usernames);
    }

    private TdApi.User getUser(long userId) {
        TdApi.User cachedUser = userCache.get(userId);
        if (cachedUser != null) {
            return cachedUser;
        }
        try {
            TdApi.User user = send(new TdApi.GetUser(userId));
            userCache.put(user.id, user);
            return user;
        } catch (TdlibException e) {
            TdApi.User fallback = new TdApi.User();
            fallback.id = userId;
            return fallback;
        }
    }

    private String formatUserDisplayName(TdApi.User user) {
        if (user == null) {
            return null;
        }

        StringBuilder name = new StringBuilder();
        if (user.firstName != null && !user.firstName.isBlank()) {
            name.append(user.firstName.trim());
        }
        if (user.lastName != null && !user.lastName.isBlank()) {
            if (!name.isEmpty()) {
                name.append(" ");
            }
            name.append(user.lastName.trim());
        }

        if (!name.isEmpty()) {
            return name.toString();
        }

        String username = firstActiveUsername(user.usernames);
        if (username != null) {
            return "@" + username;
        }

        return null;
    }

    private String firstActiveUsername(TdApi.Usernames usernames) {
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

    private String resolveTopicName(long chatId, TdApi.MessageTopic topic) {
        long topicKey = extractTopicKey(topic);
        if (topicKey <= 0) {
            return null;
        }

        String cached = Optional.ofNullable(topicNameCache.get(chatId))
                .map(cache -> cache.get(topicKey))
                .orElse(null);
        if (cached != null) {
            return cached;
        }

        if (topic instanceof TdApi.MessageTopicForum forumTopic) {
            try {
                TdApi.ForumTopic resolvedTopic = send(new TdApi.GetForumTopic(chatId, forumTopic.forumTopicId));
                if (resolvedTopic.info != null) {
                    cacheTopicName(chatId, forumTopic.forumTopicId, resolvedTopic.info.name);
                    cacheTopicName(chatId, topicKey, resolvedTopic.info.name);
                    return resolvedTopic.info.name;
                }
            } catch (TdlibException ignored) {
                // Fall through to a broader topic refresh below.
            }
        }

        try {
            getTopics(chatId, properties.getTopicLimit());
        } catch (RuntimeException ignored) {
            return null;
        }

        return Optional.ofNullable(topicNameCache.get(chatId))
                .map(cache -> cache.get(topicKey))
                .orElse(null);
    }

    private void cacheTopicName(long chatId, long topicKey, String name) {
        if (topicKey <= 0 || name == null || name.isBlank()) {
            return;
        }
        topicNameCache.computeIfAbsent(chatId, ignored -> new ConcurrentHashMap<>()).put(topicKey, name);
    }

    private void cacheTopic(long chatId, TelegramTopicDto topic) {
        ConcurrentMap<Long, TelegramTopicDto> topics = topicCache.computeIfAbsent(chatId, ignored -> new ConcurrentHashMap<>());
        topics.put(topic.forumTopicId(), topic);
        topics.put(topic.messageThreadId(), topic);
    }

    private long resolveTopicCompatibilityThreadId(TdApi.ForumTopic topic) {
        long topicKey = extractTopicKey(topic.lastMessage == null ? null : topic.lastMessage.topicId);
        if (topicKey > 0) {
            return topicKey;
        }
        return topic.info == null ? 0L : topic.info.forumTopicId;
    }

    private long extractTopicKey(TdApi.MessageTopic topic) {
        if (topic instanceof TdApi.MessageTopicThread messageTopicThread) {
            return messageTopicThread.messageThreadId;
        }
        if (topic instanceof TdApi.MessageTopicForum messageTopicForum) {
            return messageTopicForum.forumTopicId;
        }
        return 0L;
    }

    private String extractMessageText(TdApi.MessageContent content) {
        if (content == null) {
            return "";
        }
        if (content instanceof TdApi.MessageText messageText) {
            return formatFormattedText(messageText.text);
        }
        if (content instanceof TdApi.MessagePhoto messagePhoto) {
            return formatFormattedText(messagePhoto.caption);
        }
        if (content instanceof TdApi.MessageVideo messageVideo) {
            return formatFormattedText(messageVideo.caption);
        }
        if (content instanceof TdApi.MessageDocument messageDocument) {
            return formatFormattedText(messageDocument.caption);
        }
        if (content instanceof TdApi.MessageAnimation messageAnimation) {
            return formatFormattedText(messageAnimation.caption);
        }
        if (content instanceof TdApi.MessageAudio messageAudio) {
            return formatFormattedText(messageAudio.caption);
        }
        if (content instanceof TdApi.MessageVoiceNote messageVoiceNote) {
            return formatFormattedText(messageVoiceNote.caption);
        }
        if (content instanceof TdApi.MessageSticker messageSticker) {
            return messageSticker.sticker == null ? "[sticker]" : messageSticker.sticker.emoji;
        }
        return content.toString();
    }

    private String formatFormattedText(TdApi.FormattedText formattedText) {
        if (formattedText == null || formattedText.text == null) {
            return "";
        }
        if (formattedText.entities == null || formattedText.entities.length == 0) {
            return formattedText.text;
        }

        StringBuilder text = new StringBuilder(formattedText.text);
        List<TdApi.TextEntity> entities = Arrays.stream(formattedText.entities)
                .filter(entity -> entity != null && entity.type != null)
                .sorted((left, right) -> Integer.compare(right.offset, left.offset))
                .toList();

        for (TdApi.TextEntity entity : entities) {
            int start = Math.max(0, Math.min(entity.offset, text.length()));
            int end = Math.max(start, Math.min(entity.offset + entity.length, text.length()));
            if (start >= end) {
                continue;
            }

            if (entity.type instanceof TdApi.TextEntityTypeTextUrl textUrl) {
                String label = text.substring(start, end);
                String replacement = label.equals(textUrl.url)
                        ? textUrl.url
                        : "[" + label + "](" + textUrl.url + ")";
                text.replace(start, end, replacement);
            }
        }

        return text.toString();
    }

    private String getAuthorizationStateName() {
        TdApi.AuthorizationState state = authorizationState.get();
        if (state == null) {
            return "not_initialized";
        }
        return state.getClass().getSimpleName();
    }

    private int normalizeLimit(int limit, int fallback) {
        if (limit <= 0) {
            return fallback;
        }
        return Math.min(limit, 100);
    }
}
