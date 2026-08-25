package com.ulticode.app.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused tests for the default local {@link FileStoragePort}: roundtrip,
 * overwrite, delete, URL contract and key-traversal hardening.
 */
@DisplayName("LocalStorage")
class LocalStorageTest {

    @TempDir
    Path tempDir;

    private LocalStorage storage(String rootDir) {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setRootDir(rootDir);
        return new LocalStorage(properties);
    }

    @Nested
    @DisplayName("put/get/delete roundtrip")
    class Roundtrip {

        @Test
        @DisplayName("stores, reads and deletes an object")
        void roundtrip() {
            LocalStorage storage = storage(tempDir.toString());

            String url = storage.put("avatars/a.png", new ByteArrayInputStream(new byte[]{1, 2}), 2);

            assertThat(url).isEqualTo("/uploads/avatars/a.png");
            Optional<FileStoragePort.StoredObject> fetched = storage.get("avatars/a.png");
            assertThat(fetched).isPresent();
            assertThat(fetched.get().content()).isEqualTo(new byte[]{1, 2});
            assertThat(fetched.get().contentType()).isEqualTo("image/png");

            storage.delete("avatars/a.png");
            assertThat(storage.get("avatars/a.png")).isEmpty();
        }

        @Test
        @DisplayName("overwrites existing content under the same key")
        void overwrite() {
            LocalStorage storage = storage(tempDir.toString());
            storage.put("avatars/a.png", new ByteArrayInputStream(new byte[]{1}), 1);

            storage.put("avatars/a.png", new ByteArrayInputStream(new byte[]{9, 9, 9}), 3);

            assertThat(storage.get("avatars/a.png")).isPresent();
            assertThat(storage.get("avatars/a.png").get().content()).isEqualTo(new byte[]{9, 9, 9});
        }
    }

    @Nested
    @DisplayName("URL contract")
    class UrlContract {

        @Test
        @DisplayName("legacy defaults: uploads root + /uploads prefix")
        void legacyDefaults() {
            LocalStorage legacy = storage(tempDir.resolve("uploads").toString());
            assertThat(legacy.publicUrl("avatars/x.gif")).isEqualTo("/uploads/avatars/x.gif");
        }

        @Test
        @DisplayName("configured prefix is honored without double slash")
        void configuredPrefix() {
            StorageProperties properties = new StorageProperties();
            properties.getLocal().setRootDir(tempDir.toString());
            properties.getLocal().setPublicUrlPrefix("/cdn/");
            assertThat(new LocalStorage(properties).publicUrl("avatars/x.png"))
                    .isEqualTo("/cdn/avatars/x.png");
        }
    }

    @Nested
    @DisplayName("key validation (traversal hardening)")
    class KeyValidation {

        @Test
        @DisplayName("rejects traversal, absolute and blank keys")
        void rejectsTraversal() {
            LocalStorage storage = storage(tempDir.toString());
            assertThatThrownBy(() -> storage.put("../escape.txt", new ByteArrayInputStream(new byte[0]), 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.put("avatars/../../escape.txt",
                    new ByteArrayInputStream(new byte[0]), 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.put("/abs/path",
                    new ByteArrayInputStream(new byte[0]), 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.put("a\\b", new ByteArrayInputStream(new byte[0]), 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.put(" ", new ByteArrayInputStream(new byte[0]), 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delete of an absent key is a no-op")
        void deleteAbsentIsNoop() {
            LocalStorage storage = storage(tempDir.toString());
            storage.delete("avatars/never-existed.png");
        }
    }
}
