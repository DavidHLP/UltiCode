package com.ulticode.common.storage;

/**
 * Storage seam for user-visible binary assets (currently avatars).
 *
 * <p>Review 2026-08-25 FINAL P2: App/Admin replicas previously wrote uploads
 * into process-local {@code working-dir/uploads/avatars}, which breaks as soon
 * as more than one replica serves traffic. The default implementation persists
 * to a configurable directory that deployments back with a shared volume;
 * object-storage implementations can replace it via the same interface
 * ({@code app.storage.mode}).</p>
 */
public interface AvatarStorage {

    /**
     * Persists one asset under an opaque key.
     *
     * @param filename safe, server-generated key (UUID + sanitized extension)
     * @param content asset bytes
     * @return stable public URL path to store in owner data
     */
    String store(String filename, byte[] content);

    /**
     * Best-effort removal of a previously stored asset. Implementations must
     * not fail when the asset does not exist.
     */
    default void delete(String filename) {
    }
}
