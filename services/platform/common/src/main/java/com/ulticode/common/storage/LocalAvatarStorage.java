package com.ulticode.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared-directory {@link AvatarStorage}. The directory must be backed by a
 * volume shared by every replica that serves avatar reads/writes (compose
 * mounts {@code AVATAR_UPLOAD_DIR}); the public URL prefix stays fixed so
 * historical rows remain valid across storage moves.
 */
public class LocalAvatarStorage implements AvatarStorage {

    /** Fixed public URL prefix; independent of the physical directory. */
    public static final String PUBLIC_URL_PREFIX = "/uploads/avatars/";

    private final Path baseDir;

    public LocalAvatarStorage(String dir) {
        this.baseDir = Paths.get(dir);
    }

    @Override
    public String store(String filename, byte[] content) {
        try {
            Files.createDirectories(baseDir);
            Files.write(baseDir.resolve(filename), content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist asset " + filename, e);
        }
        return PUBLIC_URL_PREFIX + filename;
    }

    @Override
    public void delete(String filename) {
        try {
            Files.deleteIfExists(baseDir.resolve(filename));
        } catch (IOException e) {
            // best-effort by contract
        }
    }
}
