package com.larbcorp.neuroinfogrinder2.api;

import com.larbcorp.neuroinfogrinder2.sse.PipelineEventBroadcaster;
import com.larbcorp.neuroinfogrinder2.telegram.model.TelegramAuthStateResponse;
import com.larbcorp.neuroinfogrinder2.telegram.model.TelegramChatDto;
import com.larbcorp.neuroinfogrinder2.telegram.tdlib.TdlibClientManager2;
import com.larbcorp.neuroinfogrinder2.telegram.tdlib.TdlibNativeLoader;
import com.larbcorp.neuroinfogrinder2.telegram.tdlib.TdlibProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin
public class Stage1Api {
    private final Stage1ReadService readService;
    private final PipelineEventBroadcaster eventBroadcaster;
    private final TdlibClientManager2 tdlibClientManager;
    private final TdlibProperties tdlibProperties;

    public Stage1Api(
            Stage1ReadService readService,
            PipelineEventBroadcaster eventBroadcaster,
            TdlibClientManager2 tdlibClientManager,
            TdlibProperties tdlibProperties
    ) {
        this.readService = readService;
        this.eventBroadcaster = eventBroadcaster;
        this.tdlibClientManager = tdlibClientManager;
        this.tdlibProperties = tdlibProperties;
    }

    @PostMapping("/api/v1/auth/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        String username = request.username() == null || request.username().isBlank() ? "local" : request.username();
        return new LoginResponse("local-dev-token", 1L, username, "ADMIN");
    }

    @GetMapping("/api/v1/auth/me")
    public CurrentUser me() {
        return new CurrentUser(1L, "local", "ADMIN");
    }

    @GetMapping("/api/v1/system/info")
    public Map<String, Object> systemInfo() {
        return Map.of(
                "name", "NeuroInfoGrinder backend 2.0",
                "mode", "LOCAL_STAGE1",
                "time", OffsetDateTime.now().toString(),
                "tdlibAdapter", "isolated",
                "localReplay", true
        );
    }

    @GetMapping("/api/v1/accounts")
    public List<Stage1ReadService.AccountDto> accounts() {
        return readService.accounts();
    }

    @GetMapping("/api/v2/telegram/accounts")
    public List<Stage1ReadService.AccountDto> telegramAccounts() {
        return readService.accounts();
    }

    @PostMapping("/api/v1/accounts")
    public Stage1ReadService.AccountDto createAccount(@RequestBody CreateAccountRequest request) {
        Stage1ReadService.AccountDto account = readService.createAccount(request.phone());
        if (tdlibClientManager.isEnabled() && request.phone() != null && !request.phone().isBlank()) {
            tdlibClientManager.submitPhone(account.id(), request.phone());
            return readService.account(account.id());
        }
        return account;
    }

    @PostMapping("/api/v1/accounts/{id}/code")
    public Stage1ReadService.AccountDto submitCode(@PathVariable long id, @RequestBody SubmitCodeRequest request) {
        tdlibClientManager.submitCode(id, request.code());
        return readService.account(id);
    }

    @PostMapping("/api/v1/accounts/{id}/password")
    public Stage1ReadService.AccountDto submitPassword(@PathVariable long id, @RequestBody SubmitPasswordRequest request) {
        tdlibClientManager.submitPassword(id, request.password());
        return readService.account(id);
    }

    @PostMapping("/api/v1/accounts/{id}/reconnect")
    public Stage1ReadService.AccountDto reconnect(@PathVariable long id) {
        tdlibClientManager.reconnect(id);
        return readService.account(id);
    }

    @DeleteMapping("/api/v1/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable long id) {
        tdlibClientManager.disconnectAndForget(id);
        readService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v2/telegram/accounts/{accountId}")
    public ResponseEntity<Void> deleteTelegramAccount(@PathVariable long accountId) {
        tdlibClientManager.disconnectAndForget(accountId);
        readService.deleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v2/telegram/accounts/{accountId}/pause")
    public Stage1ReadService.AccountDto pauseTelegramAccount(@PathVariable long accountId) {
        return readService.updateAccountOperationalStatus(accountId, "PAUSED", "TELEGRAM_ACCOUNT_PAUSED");
    }

    @PostMapping("/api/v2/telegram/accounts/{accountId}/resume")
    public Stage1ReadService.AccountDto resumeTelegramAccount(@PathVariable long accountId) {
        return readService.updateAccountOperationalStatus(accountId, "CONNECTED", "TELEGRAM_ACCOUNT_RESUMED");
    }

    @PostMapping("/api/v2/telegram/accounts/{accountId}/disable")
    public Stage1ReadService.AccountDto disableTelegramAccount(@PathVariable long accountId) {
        return readService.updateAccountOperationalStatus(accountId, "DISABLED", "TELEGRAM_ACCOUNT_DISABLED");
    }

    @PostMapping("/api/v2/telegram/accounts/{accountId}/enable")
    public Stage1ReadService.AccountDto enableTelegramAccount(@PathVariable long accountId) {
        return readService.updateAccountOperationalStatus(accountId, "AUTH_REQUIRED", "TELEGRAM_ACCOUNT_ENABLED");
    }

    @GetMapping("/api/v1/groups")
    public Stage1ReadService.PageResponse<Stage1ReadService.GroupDto> groups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long accountId
    ) {
        return readService.groups(page, size, enabled, search, accountId);
    }

    @PostMapping("/api/v1/groups/sync")
    public TdlibClientManager2.ChatSyncStatus syncGroups(@RequestParam(defaultValue = "1") long accountId) {
        return tdlibClientManager.startChatSync(accountId, 100);
    }

    @PostMapping("/api/v2/telegram/chats/sync/start")
    public TdlibClientManager2.ChatSyncStatus startTelegramChatSync(
            @RequestParam(defaultValue = "1") long accountId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return tdlibClientManager.startChatSync(accountId, limit);
    }

    @GetMapping("/api/v2/telegram/chats/sync/status")
    public TdlibClientManager2.ChatSyncStatus telegramChatSyncStatus(@RequestParam(defaultValue = "1") long accountId) {
        return tdlibClientManager.chatSyncStatus(accountId);
    }

    @PostMapping("/api/v2/telegram/chats/sync/cancel")
    public TdlibClientManager2.ChatSyncStatus cancelTelegramChatSync(@RequestParam(defaultValue = "1") long accountId) {
        return tdlibClientManager.cancelChatSync(accountId);
    }

    @PostMapping("/api/v2/telegram/chats/tdlib-diagnostic")
    public TdlibClientManager2.ChatTdlibDiagnostic telegramChatTdlibDiagnostic(@RequestParam(defaultValue = "1") long accountId) {
        long knownDbChats = readService.telegramChatCount(accountId);
        Long knownChatId = readService.latestTelegramChatId(accountId);
        return tdlibClientManager.chatTdlibDiagnostic(accountId, knownDbChats, knownChatId);
    }

    @PostMapping("/api/v2/telegram/sync/topics/start")
    public TdlibClientManager2.TopicSyncStatus startTopicSync(
            @RequestParam(defaultValue = "1") long accountId,
            @RequestParam long chatId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return tdlibClientManager.startTopicSync(accountId, chatId, limit);
    }

    @GetMapping("/api/v2/telegram/sync/topics/status")
    public TdlibClientManager2.TopicSyncStatus topicSyncStatus(
            @RequestParam(defaultValue = "1") long accountId,
            @RequestParam long chatId
    ) {
        return tdlibClientManager.topicSyncStatus(accountId, chatId);
    }

    @PostMapping("/api/v2/telegram/sync/topics/all-forums/start")
    public List<TdlibClientManager2.TopicSyncStatus> startAllForumTopicSync(@RequestParam(defaultValue = "1") long accountId) {
        return tdlibClientManager.startAllForumTopicSync(accountId);
    }

    @GetMapping("/api/v2/telegram/topics")
    public List<Stage1ReadService.TopicDto> telegramTopics(
            @RequestParam(defaultValue = "1") long accountId,
            @RequestParam long chatId
    ) {
        return readService.topicsByTelegramChatId(accountId, chatId);
    }

    @PostMapping("/api/v2/telegram/topics/reconcile")
    public Stage1ReadService.TopicReconcileResult reconcileTelegramTopics(
            @RequestParam(defaultValue = "1") long accountId,
            @RequestParam long chatId
    ) {
        return readService.reconcileTelegramTopics(accountId, chatId);
    }

    @GetMapping("/api/v2/telegram/topics/diagnostics")
    public Stage1ReadService.TopicDiagnostics telegramTopicDiagnostics(
            @RequestParam(defaultValue = "1") long accountId,
            @RequestParam long chatId
    ) {
        return readService.topicDiagnostics(accountId, chatId);
    }

    @GetMapping("/api/v2/telegram/sync/diagnostics")
    public TelegramSyncDiagnosticsResponse telegramSyncDiagnostics(@RequestParam(defaultValue = "1") long accountId) {
        Stage1ReadService.TelegramDiagnosticsDbDto db = readService.telegramDiagnosticsDb();
        TdlibClientManager2.ChatSyncStatus sync = tdlibClientManager.chatSyncStatus(accountId);
        TdlibClientManager2.ProxyRuntimeState proxy = tdlibClientManager.proxyRuntimeState();
        long dbChatsCount = readService.telegramChatCount(accountId);
        return new TelegramSyncDiagnosticsResponse(
                accountId,
                tdlibClientManager.isReady(accountId),
                db.lastUpdateReceivedAt() != null,
                db.lastUpdateReceivedAt(),
                db.lastUpdateNewMessageAt(),
                dbChatsCount,
                db.rawMessagesCount(),
                sync.status(),
                sync.syncId(),
                sync.loadedCount(),
                sync.storedCount(),
                sync.lastError(),
                tdlibProperties.isProxyEnabled(),
                proxy.configuredInTdlib(),
                proxy.pingStatus(),
                "DB_CACHE"
        );
    }

    @GetMapping("/api/v2/telegram/backfill/preflight")
    public BackfillPreflightResponse backfillPreflight(
            @RequestParam(defaultValue = "1") long accountId,
            @RequestParam long chatId
    ) {
        Stage1ReadService.ChatPreflightDto chat = readService.chatPreflight(accountId, chatId);
        Stage1ReadService.TelegramDiagnosticsDbDto db = readService.telegramDiagnosticsDb();
        boolean accountConnected = db.accountAuthStates().stream()
                .anyMatch(state -> state.accountId() == accountId && ("CONNECTED".equals(state.authState()) || "READY".equals(state.authState())));
        TdlibClientManager2.ProxyRuntimeState proxy = tdlibClientManager.proxyRuntimeState();
        boolean proxyOk = !tdlibProperties.isProxyEnabled() || proxy.configuredInTdlib() || "OK".equals(proxy.pingStatus());
        boolean tdlibClientActive = tdlibClientManager.isReady(accountId);
        TdlibClientManager2.ChatSyncStatus sync = tdlibClientManager.chatSyncStatus(accountId);
        boolean canBackfill = accountConnected && chat.chatExists() && chat.chatEnabled() && tdlibClientActive && proxyOk;
        return new BackfillPreflightResponse(
                accountId,
                chatId,
                accountConnected,
                chat.chatExists(),
                chat.chatEnabled(),
                tdlibClientActive,
                proxyOk,
                canBackfill,
                sync.status(),
                "PARTIAL".equals(sync.status()) ? "Chat sync is partial; known DB chat can still be used for limited backfill." : null
        );
    }

    @PostMapping("/api/v1/groups/bulk-toggle")
    public ResponseEntity<Void> bulkToggle(@RequestBody BulkToggleRequest request) {
        for (String id : request.groupIds()) {
            readService.updateGroup(Long.parseLong(id), request.enabled(), null);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/groups/bulk-assign")
    public ResponseEntity<Void> bulkAssign(@RequestBody BulkAssignRequest request) {
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/v1/groups/{id}")
    public Stage1ReadService.GroupDto updateGroup(@PathVariable long id, @RequestBody UpdateGroupRequest request) {
        return readService.updateGroup(id, request.enabled(), request.title());
    }

    @GetMapping("/api/v1/groups/{groupId}/messages")
    public Stage1ReadService.PageResponse<Stage1ReadService.MessageDto> messages(
            @PathVariable long groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size,
            @RequestParam(required = false) Long topicId
    ) {
        return readService.messages(groupId, page, size, topicId);
    }

    @GetMapping("/api/v1/groups/{groupId}/messages/{messageId}")
    public Stage1ReadService.MessageDto message(@PathVariable long groupId, @PathVariable long messageId) {
        return readService.message(groupId, messageId);
    }

    @GetMapping("/api/v1/groups/{groupId}/messages/{messageId}/chain")
    public Stage1ReadService.MessageChainDto messageChain(@PathVariable long groupId, @PathVariable long messageId) {
        return readService.messageChain(groupId, messageId);
    }

    @GetMapping("/api/v1/messages/{rawId}/pipeline-detail")
    public Map<String, Object> messagePipelineDetail(@PathVariable long rawId) {
        return readService.messagePipelineDetail(rawId);
    }

    @GetMapping("/api/v1/messages/pipeline-detail")
    public Map<String, Object> messagePipelineDetailByQuery(@RequestParam long rawId) {
        return readService.messagePipelineDetail(rawId);
    }

    @PostMapping("/api/v1/groups/{groupId}/messages/{messageId}/enqueue")
    public ResponseEntity<Void> enqueueMessage(@PathVariable long groupId, @PathVariable long messageId) {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/groups/{groupId}/messages/sync")
    public MessageSyncResponse syncMessages(@PathVariable long groupId) {
        Stage1ReadService.GroupDto group = readService.group(groupId);
        int limit = 50;
        readService.recordBackfillStarted(group.accountId(), group.telegramChatId(), groupId, limit);
        try {
            TdlibClientManager2.BackfillResult result = tdlibClientManager.backfill(group.accountId(), group.telegramChatId(), limit);
            readService.recordBackfillCompleted(group.accountId(), group.telegramChatId(), groupId, result.persisted());
            return new MessageSyncResponse(result.scheduled(), "scheduled", groupId, result.persisted());
        } catch (RuntimeException error) {
            readService.recordBackfillFailed(group.accountId(), group.telegramChatId(), groupId, error.getMessage());
            throw error;
        }
    }

    @GetMapping("/api/v1/telegram/auth/state")
    public TelegramAuthStateResponse telegramAuthState(@RequestParam(defaultValue = "1") long accountId) {
        return tdlibClientManager.authorizationState(accountId);
    }

    @PostMapping("/api/v1/telegram/auth/phone")
    public TelegramAuthStateResponse submitTelegramPhone(@RequestBody TelegramAuthRequest request) {
        return tdlibClientManager.submitPhone(request.effectiveAccountId(), request.valueOrPhone());
    }

    @PostMapping("/api/v1/telegram/auth/code")
    public TelegramAuthStateResponse submitTelegramCode(@RequestBody TelegramAuthRequest request) {
        return tdlibClientManager.submitCode(request.effectiveAccountId(), request.valueOrCode());
    }

    @PostMapping("/api/v1/telegram/auth/password")
    public TelegramAuthStateResponse submitTelegramPassword(@RequestBody TelegramAuthRequest request) {
        return tdlibClientManager.submitPassword(request.effectiveAccountId(), request.valueOrPassword());
    }

    @PostMapping("/api/v1/telegram/auth/logout")
    public TelegramAuthStateResponse logoutTelegram(@RequestBody TelegramAuthRequest request) {
        return tdlibClientManager.reconnect(request.effectiveAccountId());
    }

    @GetMapping("/api/v1/telegram/chats")
    public List<TelegramChatDto> telegramChats(
            @RequestParam(defaultValue = "1") long accountId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return readService.telegramChats(accountId, limit);
    }

    @PostMapping("/api/v1/telegram/chats/{chatId}/ingest")
    public Stage1ReadService.GroupDto updateTelegramChatIngest(
            @PathVariable long chatId,
            @RequestParam(required = false) Long accountId,
            @RequestBody UpdateGroupRequest request
    ) {
        Stage1ReadService.GroupDto group = readService.groupByTelegramChatId(chatId);
        if (accountId != null && group.accountId() != accountId) {
            throw new IllegalArgumentException("Chat belongs to another account");
        }
        return readService.updateGroup(group.id(), request.enabled(), request.title());
    }

    @GetMapping("/api/v1/telegram/chats/{chatId}/topics")
    public List<Stage1ReadService.TopicDto> topics(
            @PathVariable long chatId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        if (accountId != null) {
            tdlibClientManager.getTopics(accountId, chatId, limit);
        }
        return readService.topicsByTelegramChatId(accountId, chatId);
    }

    @GetMapping("/api/v1/telegram/chats/{chatId}/messages")
    public Stage1ReadService.PageResponse<Stage1ReadService.MessageDto> telegramMessages(
            @PathVariable long chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size
    ) {
        Stage1ReadService.GroupDto group = readService.groupByTelegramChatId(chatId);
        return readService.messages(group.id(), page, size, null);
    }

    @GetMapping(path = "/api/v1/pipeline/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPipelineEvents() {
        return eventBroadcaster.stream();
    }

    @GetMapping("/api/v1/pipeline/status")
    public Stage1ReadService.PipelineStatusDto pipelineStatus() {
        return readService.pipelineStatus();
    }

    @GetMapping("/api/v2/operator/status")
    public Stage1ReadService.OperatorStatusDto operatorStatus() {
        return readService.operatorStatus();
    }

    @GetMapping("/api/v2/telegram/health")
    public List<Stage1ReadService.AccountHealthDto> telegramHealth() {
        return readService.telegramHealth();
    }

    @GetMapping("/api/v2/telegram/diagnostics")
    public TelegramDiagnosticsResponse telegramDiagnostics() {
        Stage1ReadService.TelegramDiagnosticsDbDto db = readService.telegramDiagnosticsDb();
        Path libraryPath = tdlibProperties.getLibraryPath() == null || tdlibProperties.getLibraryPath().isBlank()
                ? null
                : Path.of(tdlibProperties.getLibraryPath());
        Path databaseDir = Path.of(tdlibProperties.getDatabaseDirectory());
        Path filesDir = Path.of(tdlibProperties.getFilesDirectory());
        TdlibClientManager2.ProxyRuntimeState proxyRuntime = tdlibClientManager.proxyRuntimeState();
        return new TelegramDiagnosticsResponse(
                tdlibProperties.isEnabled(),
                tdlibProperties.getLibraryPath() != null && !tdlibProperties.getLibraryPath().isBlank(),
                libraryPath != null && Files.exists(libraryPath),
                TdlibNativeLoader.isLoaded(),
                Files.exists(databaseDir),
                Files.exists(databaseDir) && Files.isWritable(databaseDir),
                Files.exists(filesDir),
                Files.exists(filesDir) && Files.isWritable(filesDir),
                db.accountsCount(),
                db.accountAuthStates(),
                db.lastAuthStateChange(),
                db.lastUpdateReceivedAt(),
                db.lastUpdateNewMessageAt(),
                db.inboxPendingCount(),
                db.inboxProcessedCount(),
                db.inboxErrorCount(),
                db.rawMessagesCount(),
                db.lastRawMessageAt(),
                db.lastError(),
                db.floodWaitStatus(),
                db.backfillStatus(),
                db.liveUpdatesStatus(),
                tdlibProperties.isProxyEnabled(),
                tdlibProperties.getProxyType(),
                tdlibProperties.getProxyHost() != null && !tdlibProperties.getProxyHost().isBlank(),
                tdlibProperties.getProxyPort(),
                tdlibProperties.getProxyUsername() != null && !tdlibProperties.getProxyUsername().isBlank(),
                tdlibProperties.getProxyPassword() != null && !tdlibProperties.getProxyPassword().isBlank(),
                proxyRuntime.configuredInTdlib(),
                proxyRuntime.pingStatus(),
                proxyRuntime.lastError()
        );
    }

    @GetMapping("/api/v2/telegram/logs/recent")
    public List<Stage1ReadService.TelegramRuntimeLogDto> recentTelegramLogs(@RequestParam(defaultValue = "200") int limit) {
        return readService.recentTelegramLogs(limit);
    }

    @GetMapping("/api/v2/telegram/events/recent")
    public List<Stage1ReadService.TelegramRuntimeEventDto> recentTelegramEvents(@RequestParam(defaultValue = "200") int limit) {
        return readService.recentTelegramEvents(limit);
    }

    @GetMapping("/api/v2/telegram/messages/recent")
    public List<Stage1ReadService.MessageDto> recentTelegramMessages(@RequestParam(defaultValue = "100") int limit) {
        return readService.recentRawMessages(limit);
    }

    @GetMapping("/api/v2/pipeline/events/recent")
    public List<Stage1ReadService.PipelineEventDto> recentPipelineEvents(@RequestParam(defaultValue = "100") int limit) {
        return readService.recentPipelineEvents(limit);
    }

    @PostMapping("/api/v2/telegram/backfill/start")
    public MessageSyncResponse startBackfill(@RequestBody BackfillRequest request) {
        long accountId = request.accountId() == null ? 1L : request.accountId();
        int limit = request.limit() == null ? 50 : Math.max(1, Math.min(request.limit(), 500));
        if (Boolean.TRUE.equals(request.dryRun())) {
            return new MessageSyncResponse(false, "dry_run", null, 0);
        }
        Stage1ReadService.GroupDto group = readService.groupByTelegramChatId(request.chatId());
        if (group.accountId() != accountId) {
            throw new IllegalArgumentException("Chat belongs to another account");
        }
        readService.recordBackfillStarted(accountId, request.chatId(), group.id(), limit);
        try {
            TdlibClientManager2.BackfillResult result = tdlibClientManager.backfill(accountId, request.chatId(), limit);
            readService.recordBackfillCompleted(accountId, request.chatId(), group.id(), result.persisted());
            return new MessageSyncResponse(result.scheduled(), "scheduled", group.id(), result.persisted());
        } catch (RuntimeException error) {
            readService.recordBackfillFailed(accountId, request.chatId(), group.id(), error.getMessage());
            throw error;
        }
    }

    @GetMapping("/api/v2/telegram/accounts/{accountId}/health")
    public Stage1ReadService.AccountHealthDto accountHealth(@PathVariable long accountId) {
        return readService.accountHealth(accountId);
    }

    @GetMapping("/api/v2/messages/{messageId}/links")
    public List<Stage1ReadService.MessageLinkDto> messageLinks(@PathVariable long messageId) {
        return readService.linksForMessage(messageId);
    }

    public record TelegramDiagnosticsResponse(
            boolean tdlibEnabled,
            boolean nativeLibraryConfigured,
            boolean nativeLibraryPathExists,
            boolean nativeLibraryLoaded,
            boolean databaseDirExists,
            boolean databaseDirWritable,
            boolean filesDirExists,
            boolean filesDirWritable,
            long accountsCount,
            List<Stage1ReadService.AccountAuthStateDto> accountAuthStates,
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
            String liveUpdatesStatus,
            boolean proxyEnabled,
            String proxyType,
            boolean proxyHostPresent,
            int proxyPort,
            boolean proxyUsernamePresent,
            boolean proxyPasswordPresent,
            boolean proxyConfiguredInTdlib,
            String proxyPingStatus,
            String proxyLastError
    ) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String token, long userId, String username, String role) {
    }

    public record CurrentUser(long userId, String username, String role) {
    }

    public record CreateAccountRequest(String phone) {
    }

    public record SubmitCodeRequest(String code) {
    }

    public record SubmitPasswordRequest(String password) {
    }

    public record UpdateGroupRequest(Boolean enabled, String title) {
    }

    public record BulkToggleRequest(List<String> groupIds, boolean enabled) {
    }

    public record BulkAssignRequest(List<String> groupIds, String readerAccountId) {
    }

    public record MessageSyncResponse(boolean scheduled, String reason, Long groupId, int persisted) {
    }

    public record BackfillRequest(Long accountId, long chatId, Integer limit, String direction, Long topicId, Boolean dryRun) {
    }

    public record BackfillPreflightResponse(long accountId, long chatId, boolean accountConnected, boolean chatExistsInDb, boolean chatEnabled, boolean tdlibClientActive, boolean proxyOk, boolean canBackfill, String chatSyncStatus, String warning) {
    }

    public record TelegramSyncDiagnosticsResponse(long accountId, boolean tdlibClientAlive, boolean updateLoopRunning, String lastUpdateReceivedAt, String lastMessageUpdateAt, long dbChatsCount, long rawMessagesCount, String chatSyncStatus, String chatSyncId, int chatSyncLoadedCount, int chatSyncStoredCount, String chatSyncLastError, boolean proxyEnabled, boolean proxyConfiguredInTdlib, String proxyPingStatus, String sourceOfTruth) {
    }

    public record TelegramAuthRequest(Long accountId, String phone, String code, String password, String value) {
        long effectiveAccountId() {
            return accountId == null ? 1L : accountId;
        }

        String valueOrPhone() {
            return phone != null ? phone : value;
        }

        String valueOrCode() {
            return code != null ? code : value;
        }

        String valueOrPassword() {
            return password != null ? password : value;
        }
    }
}
