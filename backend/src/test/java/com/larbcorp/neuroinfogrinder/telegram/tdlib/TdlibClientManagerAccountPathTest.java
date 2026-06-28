package com.larbcorp.neuroinfogrinder.telegram.tdlib;

import com.larbcorp.neuroinfogrinder.config.TdlibProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TdlibClientManagerAccountPathTest {

    @TempDir
    Path tempDir;

    @Test
    void accountClientsUseIsolatedSessionDirectories() {
        TdlibProperties properties = new TdlibProperties();
        properties.setDatabaseDirectory(tempDir.resolve("tdlib-data").resolve("db").toString());
        properties.setFilesDirectory(tempDir.resolve("tdlib-data").resolve("files").toString());
        TdlibClientManager manager = new TdlibClientManager(properties, List.of());

        assertThat(manager.databaseDirectoryForAccount(null))
            .isEqualTo(tempDir.resolve("tdlib-data").resolve("db"));
        assertThat(manager.filesDirectoryForAccount(null))
            .isEqualTo(tempDir.resolve("tdlib-data").resolve("files"));

        assertThat(manager.databaseDirectoryForAccount(10L))
            .isEqualTo(tempDir.resolve("tdlib-data").resolve("accounts").resolve("10").resolve("db"));
        assertThat(manager.filesDirectoryForAccount(10L))
            .isEqualTo(tempDir.resolve("tdlib-data").resolve("accounts").resolve("10").resolve("files"));
        assertThat(manager.databaseDirectoryForAccount(20L))
            .isEqualTo(tempDir.resolve("tdlib-data").resolve("accounts").resolve("20").resolve("db"));
    }

    @Test
    void firstAccountUsesLegacyStorageWhenLegacySessionExistsAndAccountStorageIsBlank() throws Exception {
        Path legacyDb = tempDir.resolve("tdlib-data").resolve("db");
        Path legacyFiles = tempDir.resolve("tdlib-data").resolve("files");
        Files.createDirectories(legacyDb);
        Files.createDirectories(legacyFiles);
        Files.write(legacyDb.resolve("db.sqlite"), new byte[2 * 1024 * 1024]);

        TdlibProperties properties = new TdlibProperties();
        properties.setDatabaseDirectory(legacyDb.toString());
        properties.setFilesDirectory(legacyFiles.toString());
        TdlibClientManager manager = new TdlibClientManager(properties, List.of());

        assertThat(manager.databaseDirectoryForAccount(1L)).isEqualTo(legacyDb);
        assertThat(manager.filesDirectoryForAccount(1L)).isEqualTo(legacyFiles);
        assertThat(manager.databaseDirectoryForAccount(2L))
            .isEqualTo(tempDir.resolve("tdlib-data").resolve("accounts").resolve("2").resolve("db"));
    }

    @Test
    void firstAccountKeepsIsolatedStorageWhenItAlreadyHasInitializedDatabase() throws Exception {
        Path tdlibRoot = tempDir.resolve("tdlib-data");
        Path legacyDb = tdlibRoot.resolve("db");
        Path accountDb = tdlibRoot.resolve("accounts").resolve("1").resolve("db");
        Files.createDirectories(legacyDb);
        Files.createDirectories(accountDb);
        Files.write(legacyDb.resolve("db.sqlite"), new byte[2 * 1024 * 1024]);
        Files.write(accountDb.resolve("db.sqlite"), new byte[2 * 1024 * 1024]);

        TdlibProperties properties = new TdlibProperties();
        properties.setDatabaseDirectory(legacyDb.toString());
        properties.setFilesDirectory(tdlibRoot.resolve("files").toString());
        TdlibClientManager manager = new TdlibClientManager(properties, List.of());

        assertThat(manager.databaseDirectoryForAccount(1L)).isEqualTo(accountDb);
    }
}
