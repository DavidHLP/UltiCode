package com.ulticode.modules.backup.service.impl;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

@Slf4j
final class BackupTempFiles {

    static final String TEMP_DIR_PROPERTY = "${backup.temp-dir:${java.io.tmpdir}/ulticode-backups}";

    private BackupTempFiles() {
    }

    static Path createSecureTempFile(String backupTempDir, String prefix, String suffix) throws IOException {
        Path directory = Paths.get(backupTempDir).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        restrictPermissions(directory, "rwx------");
        Path file = Files.createTempFile(directory, prefix, suffix);
        restrictPermissions(file, "rw-------");
        return file;
    }

    private static void restrictPermissions(Path path, String permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        } catch (UnsupportedOperationException ignored) {
            // POSIX permissions are unavailable on some local development hosts.
        }
    }

    static void deleteTempFile(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Failed to clean up backup temp file: {}", file, exception);
        }
    }
}
