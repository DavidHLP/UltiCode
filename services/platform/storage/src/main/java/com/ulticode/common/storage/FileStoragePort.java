package com.ulticode.common.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/** Private object-storage port shared by the owner services. */
public interface FileStoragePort {

    /** Stores content under key, replacing any existing object. */
    void put(String key, InputStream content, long contentLength, String contentType);

    /** Streams a local file to object storage without buffering it in heap memory. */
    void putFile(String key, Path file, String contentType);

    /** Reads a small object into memory. */
    Optional<StoredObject> get(String key);

    /** Opens a streaming read for a large object. */
    Optional<StorageStream> openStream(String key);

    /** Deletes key; deleting an absent key is a no-op. */
    void delete(String key);

    record StoredObject(byte[] content, String contentType) {
    }

    record StorageStream(InputStream content, long contentLength, String contentType) {
    }
}
