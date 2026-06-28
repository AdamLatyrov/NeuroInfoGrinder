package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "telegram.tdlib")
public class TdlibProperties {
    private boolean enabled = false;
    private String libraryName = "tdjni";
    private String libraryPath = "";
    private int apiId;
    private String apiHash = "";
    private String databaseDirectory = "./tdlib-data/backend-2/db";
    private String filesDirectory = "./tdlib-data/backend-2/files";
    private String databaseEncryptionKey = "local-dev-only";
    private boolean useTestDc = false;
    private boolean useFileDatabase = true;
    private boolean useChatInfoDatabase = true;
    private boolean useMessageDatabase = true;
    private boolean useSecretChats = false;
    private String systemLanguageCode = "ru";
    private String deviceModel = "desktop";
    private String systemVersion = "Windows";
    private String applicationVersion = "2.0-local";
    private int chatLimit = 100;
    private int topicLimit = 100;
    private boolean proxyEnabled = false;
    private String proxyType = "HTTP";
    private String proxyHost = "";
    private int proxyPort = 0;
    private String proxyUsername = "";
    private String proxyPassword = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLibraryName() {
        return libraryName;
    }

    public void setLibraryName(String libraryName) {
        this.libraryName = libraryName;
    }

    public String getLibraryPath() {
        return libraryPath;
    }

    public void setLibraryPath(String libraryPath) {
        this.libraryPath = libraryPath;
    }

    public int getApiId() {
        return apiId;
    }

    public void setApiId(int apiId) {
        this.apiId = apiId;
    }

    public String getApiHash() {
        return apiHash;
    }

    public void setApiHash(String apiHash) {
        this.apiHash = apiHash;
    }

    public String getDatabaseDirectory() {
        return databaseDirectory;
    }

    public void setDatabaseDirectory(String databaseDirectory) {
        this.databaseDirectory = databaseDirectory;
    }

    public String getFilesDirectory() {
        return filesDirectory;
    }

    public void setFilesDirectory(String filesDirectory) {
        this.filesDirectory = filesDirectory;
    }

    public String getDatabaseEncryptionKey() {
        return databaseEncryptionKey;
    }

    public void setDatabaseEncryptionKey(String databaseEncryptionKey) {
        this.databaseEncryptionKey = databaseEncryptionKey;
    }

    public boolean isUseTestDc() {
        return useTestDc;
    }

    public void setUseTestDc(boolean useTestDc) {
        this.useTestDc = useTestDc;
    }

    public boolean isUseFileDatabase() {
        return useFileDatabase;
    }

    public void setUseFileDatabase(boolean useFileDatabase) {
        this.useFileDatabase = useFileDatabase;
    }

    public boolean isUseChatInfoDatabase() {
        return useChatInfoDatabase;
    }

    public void setUseChatInfoDatabase(boolean useChatInfoDatabase) {
        this.useChatInfoDatabase = useChatInfoDatabase;
    }

    public boolean isUseMessageDatabase() {
        return useMessageDatabase;
    }

    public void setUseMessageDatabase(boolean useMessageDatabase) {
        this.useMessageDatabase = useMessageDatabase;
    }

    public boolean isUseSecretChats() {
        return useSecretChats;
    }

    public void setUseSecretChats(boolean useSecretChats) {
        this.useSecretChats = useSecretChats;
    }

    public String getSystemLanguageCode() {
        return systemLanguageCode;
    }

    public void setSystemLanguageCode(String systemLanguageCode) {
        this.systemLanguageCode = systemLanguageCode;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getSystemVersion() {
        return systemVersion;
    }

    public void setSystemVersion(String systemVersion) {
        this.systemVersion = systemVersion;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    public void setApplicationVersion(String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }

    public int getChatLimit() {
        return chatLimit;
    }

    public void setChatLimit(int chatLimit) {
        this.chatLimit = chatLimit;
    }

    public int getTopicLimit() {
        return topicLimit;
    }

    public void setTopicLimit(int topicLimit) {
        this.topicLimit = topicLimit;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public String getProxyType() {
        return proxyType;
    }

    public void setProxyType(String proxyType) {
        this.proxyType = proxyType;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }
}
