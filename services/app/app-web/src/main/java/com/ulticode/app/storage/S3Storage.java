package com.ulticode.app.storage;

import com.ulticode.common.resilience.DependencyGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * S3-compatible {@link FileStoragePort} (AWS S3, MinIO, ...), activated by
 * {@code app.storage.type=s3}. Uses path-style addressing
 * ({endpoint}/{bucket}/{key}) and a hand-rolled AWS SigV4 signer, so no
 * additional dependency is required. Objects are addressed by their public
 * base URL ({endpoint}/{bucket} unless overridden); clients fetch bytes
 * directly from the object store, so App replicas stay stateless.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = StorageProperties.TYPE_S3)
public class S3Storage implements FileStoragePort {

    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);
    private static final int READ_ATTEMPTS = 2;

    private final StorageProperties properties;
    private final HttpClient httpClient;
    private final DependencyGuard dependencyGuard;

    public S3Storage(StorageProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getS3().getConnectTimeoutMs()))
                .build());
    }

    S3Storage(StorageProperties properties, HttpClient httpClient) {
        this(properties, httpClient, new DependencyGuard(
                properties.getS3().getMaxConcurrentRequests(),
                FAILURE_THRESHOLD,
                OPEN_DURATION));
    }

    S3Storage(
            StorageProperties properties,
            HttpClient httpClient,
            DependencyGuard dependencyGuard) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.dependencyGuard = dependencyGuard;
    }

    @Override
    public String put(String key, InputStream content, long contentLength) {
        byte[] body;
        try {
            body = content.readAllBytes();
        } catch (IOException e) {
            throw new StorageException("Failed to read content for object '" + key + "'", e);
        }
        String contentType = guessContentType(key);
        request("PUT", key, body != null && body.length > 0 ? body : new byte[0], contentType);
        return publicUrl(key);
    }

    @Override
    public Optional<StoredObject> get(String key) {
        try {
            HttpResponse<byte[]> response = exchange("GET", key, null, null);
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            requireSuccess(response, key);
            return Optional.of(new StoredObject(response.body(),
                    response.headers().firstValue("Content-Type").orElse(null)));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new StorageException("Failed to fetch object '" + key + "'", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            HttpResponse<byte[]> response = exchange("DELETE", key, null, null);
            if (response.statusCode() == 404) {
                return; // idempotent delete
            }
            requireSuccess(response, key);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new StorageException("Failed to delete object '" + key + "'", e);
        }
    }

    @Override
    public String publicUrl(String key) {
        validateKey(key);
        StorageProperties.S3 s3 = properties.getS3();
        String base = s3.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            base = trimTrailingSlash(s3.getEndpoint()) + "/" + s3.getBucket();
        }
        return trimTrailingSlash(base) + "/" + key;
    }

    private void request(String method, String key, byte[] body, String contentType) {
        try {
            requireSuccess(exchange(method, key, body, contentType), key);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new StorageException("Failed to store object '" + key + "'", e);
        }
    }

    private HttpResponse<byte[]> exchange(String method, String key, byte[] body, String contentType)
            throws IOException, InterruptedException {
        int maxAttempts = "GET".equals(method) ? READ_ATTEMPTS : 1;
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            DependencyGuard.Permit permit;
            try {
                permit = dependencyGuard.acquire();
            } catch (DependencyGuard.RejectedException rejected) {
                throw new StorageException("Object store temporarily unavailable: "
                        + rejected.reason(), rejected);
            }
            try (permit) {
                HttpResponse<byte[]> response = sendOnce(method, key, body, contentType);
                int status = response.statusCode();
                if (status == 429 || status >= 500) {
                    permit.failure();
                    if (attempt < maxAttempts) {
                        continue;
                    }
                } else {
                    permit.success();
                }
                return response;
            } catch (InterruptedException interrupted) {
                permit.ignore();
                throw interrupted;
            } catch (IOException failure) {
                permit.failure();
                lastFailure = failure;
                if (attempt == maxAttempts) {
                    throw failure;
                }
            }
        }
        throw lastFailure == null ? new IOException("Object store request failed") : lastFailure;
    }

    private HttpResponse<byte[]> sendOnce(String method, String key, byte[] body, String contentType)
            throws IOException, InterruptedException {
        StorageProperties.S3 s3 = properties.getS3();
        URI uri = URI.create(trimTrailingSlash(s3.getEndpoint()) + "/"
                + s3.getBucket() + "/" + AwsSigV4Signer.encodeKeyPath(key));
        validateKey(key);

        String payloadHash = AwsSigV4Signer.sha256Hex(body == null ? new byte[0] : body);
        ZonedDateTime now = ZonedDateTime.now(java.time.ZoneOffset.UTC);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", AwsSigV4Signer.AMZ_DATE.format(now));
        if (contentType != null) {
            headers.put("Content-Type", contentType);
        }
        String authorization = AwsSigV4Signer.authorization(method, uri, headers, payloadHash,
                s3.getAccessKey(), s3.getSecretKey(), s3.getRegion(), "s3", now);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(s3.getRequestTimeoutMs()))
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", headers.get("x-amz-date"))
                .header("Authorization", authorization);
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        switch (method) {
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(body));
            case "DELETE" -> builder.DELETE();
            default -> builder.GET();
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private void requireSuccess(HttpResponse<byte[]> response, String key) {
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new StorageException(
                    "Object-store request for '" + key + "' failed with HTTP " + status,
                    null);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")) {
            throw new IllegalArgumentException("Illegal storage key: " + key);
        }
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String guessContentType(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }
}
