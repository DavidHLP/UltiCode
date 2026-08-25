package com.ulticode.app.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Binary-storage seam for user-uploaded state (avatars and similar blobs).
 *
 * <p>The App service previously wrote uploads to a process-local directory,
 * which pinned every uploaded byte to one host and blocked horizontal
 * scaling of App replicas. Implementations either preserve that local
 * behavior (default) or push blobs to an S3-compatible object store shared
 * by all replicas.
 *
 * <p>Keys are opaque, slash-separated identifiers owned by callers
 * (for example {@code avatars/<uuid>.png}). Implementations must reject keys
 * that could escape their namespace (traversal segments, absolute paths).
 */
public interface FileStoragePort {

    /**
     * Stores the content under {@code key} and returns the publicly
     * addressable URL for it. Existing content under the same key is replaced.
     *
     * @throws com.ulticode.common.exception.BusinessException-adjacent runtime
     *         failures surface as {@link StorageException}
     */
    String put(String key, InputStream content, long contentLength);

    /**
     * Fetches the object stored under {@code key}.
     *
     * @return the object, or empty when no such object exists
     */
    Optional<StoredObject> get(String key);

    /**
     * Deletes the object under {@code key}. Deleting an absent key is a no-op.
     */
    void delete(String key);

    /** Publicly addressable URL for {@code key}, without storing anything. */
    String publicUrl(String key);

    /** A fetched binary object. */
    record StoredObject(byte[] content, String contentType) {}
}
