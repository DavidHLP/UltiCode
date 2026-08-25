package com.ulticode.app.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Default {@link FileStoragePort}: writes objects under a configured local
 * root directory and addresses them through a fixed URL prefix.
 *
 * <p>This preserves the legacy avatar behavior byte-for-byte
 * ({@code uploads/avatars/<uuid>.<ext>} on disk, {@code /uploads/avatars/...}
 * returned to clients) but keeps the seam explicit so replicas can switch to
 * shared object storage without touching call sites.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = StorageProperties.TYPE_LOCAL, matchIfMissing = true)
public class LocalStorage implements FileStoragePort {

    private final Path root;
    private final String publicUrlPrefix;

    public LocalStorage(StorageProperties properties) {
        this.root = Paths.get(properties.getLocal().getRootDir()).toAbsolutePath().normalize();
        String prefix = properties.getLocal().getPublicUrlPrefix();
        this.publicUrlPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    @Override
    public String put(String key, InputStream content, long contentLength) {
        Path target = resolve(key);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Failed to store object '" + key + "'", e);
        }
        return publicUrl(key);
    }

    @Override
    public Optional<StoredObject> get(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            String contentType = Files.probeContentType(target);
            return Optional.of(new StoredObject(Files.readAllBytes(target), contentType));
        } catch (IOException e) {
            throw new StorageException("Failed to read object '" + key + "'", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Failed to delete object '" + key + "'", e);
        }
    }

    @Override
    public String publicUrl(String key) {
        resolve(key); // validate the key even when only building a URL
        return publicUrlPrefix + "/" + key;
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage key is required");
        }
        if (key.startsWith("/") || key.contains("\\") || key.contains("..")) {
            throw new IllegalArgumentException("Illegal storage key: " + key);
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Illegal storage key: " + key);
        }
        return resolved;
    }
}
