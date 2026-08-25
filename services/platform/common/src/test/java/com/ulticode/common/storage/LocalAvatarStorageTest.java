package com.ulticode.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalAvatarStorage")
class LocalAvatarStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("stores under the configured dir and returns the fixed public URL prefix")
    void storesAndReturnsPublicUrl() throws Exception {
        LocalAvatarStorage storage = new LocalAvatarStorage(tempDir.resolve("avatars").toString());

        String url = storage.store("abc.png", new byte[]{1, 2, 3});

        assertThat(url).isEqualTo("/uploads/avatars/abc.png");
        assertThat(Files.readAllBytes(tempDir.resolve("avatars").resolve("abc.png"))).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("delete is best-effort and tolerates a missing asset")
    void deleteToleratesMissing() {
        LocalAvatarStorage storage = new LocalAvatarStorage(tempDir.toString());
        storage.store("x.gif", new byte[]{9});
        storage.delete("x.gif");
        storage.delete("never-existed.gif");

        assertThat(tempDir.resolve("x.gif")).doesNotExist();
    }
}
