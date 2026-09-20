package com.ulticode.common.storage;

import com.ulticode.common.resilience.DependencyGuard;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** S3-compatible, path-style object storage implementation. If configured,
 * {@code app.storage.s3.ca-certificate-path} adds operator CA certificates
 * to the JVM default trust anchors without disabling hostname verification. */
public class S3Storage implements FileStoragePort {

    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);
    private static final int READ_ATTEMPTS = 2;
    private static final String EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final StorageProperties properties;
    private final HttpClient httpClient;
    private final DependencyGuard dependencyGuard;

    public S3Storage(StorageProperties properties) {
        this(properties, createHttpClient(properties));
    }

    private static HttpClient createHttpClient(StorageProperties properties) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getS3().getConnectTimeoutMs()));
        StorageTlsSupport.sslContext(properties.getS3()).ifPresent(builder::sslContext);
        return builder.build();
    }

    S3Storage(StorageProperties properties, HttpClient httpClient) {
        this(properties, httpClient, new DependencyGuard(
                properties.getS3().getMaxConcurrentRequests(), FAILURE_THRESHOLD, OPEN_DURATION));
    }

    S3Storage(StorageProperties properties, HttpClient httpClient, DependencyGuard dependencyGuard) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.dependencyGuard = dependencyGuard;
    }

    @Override
    public void put(String key, InputStream content, long contentLength, String contentType) {
        StorageKeys.validate(key);
        byte[] body;
        try {
            body = content == null ? new byte[0] : content.readAllBytes();
        } catch (IOException exception) {
            throw new StorageException("Failed to read content for object '" + key + "'", exception);
        }
        try {
            requireSuccess(exchange("PUT", objectUri(key), HttpRequest.BodyPublishers.ofByteArray(body),
                    AwsSigV4Signer.sha256Hex(body), contentType, HttpResponse.BodyHandlers.ofByteArray(), 1), key);
        } catch (IOException | InterruptedException exception) {
            restoreInterrupt(exception);
            throw new StorageException("Failed to store object '" + key + "'", exception);
        }
    }

    @Override
    public void putFile(String key, Path file, String contentType) {
        StorageKeys.validate(key);
        if (file == null || !Files.isRegularFile(file)) {
            throw new StorageException("Object-store source file is not readable: " + file);
        }
        try {
            String payloadHash = AwsSigV4Signer.sha256Hex(file);
            requireSuccess(exchange("PUT", objectUri(key), HttpRequest.BodyPublishers.ofFile(file), payloadHash,
                    contentType, HttpResponse.BodyHandlers.ofByteArray(), 1), key);
        } catch (IOException | InterruptedException exception) {
            restoreInterrupt(exception);
            throw new StorageException("Failed to store object '" + key + "'", exception);
        }
    }

    @Override
    public Optional<StoredObject> get(String key) {
        StorageKeys.validate(key);
        try {
            HttpResponse<byte[]> response = exchange("GET", objectUri(key), HttpRequest.BodyPublishers.noBody(),
                    EMPTY_HASH, null, HttpResponse.BodyHandlers.ofByteArray(), READ_ATTEMPTS);
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            requireSuccess(response, key);
            return Optional.of(new StoredObject(response.body(),
                    response.headers().firstValue("Content-Type").orElse(null)));
        } catch (IOException | InterruptedException exception) {
            restoreInterrupt(exception);
            throw new StorageException("Failed to fetch object '" + key + "'", exception);
        }
    }

    @Override
    public Optional<StorageStream> openStream(String key) {
        StorageKeys.validate(key);
        try {
            HttpResponse<InputStream> response = exchange("GET", objectUri(key), HttpRequest.BodyPublishers.noBody(),
                    EMPTY_HASH, null, HttpResponse.BodyHandlers.ofInputStream(), READ_ATTEMPTS);
            if (response.statusCode() == 404) {
                response.body().close();
                return Optional.empty();
            }
            requireSuccess(response, key);
            return Optional.of(new StorageStream(response.body(),
                    response.headers().firstValueAsLong("Content-Length").orElse(-1L),
                    response.headers().firstValue("Content-Type").orElse(null)));
        } catch (IOException | InterruptedException exception) {
            restoreInterrupt(exception);
            throw new StorageException("Failed to stream object '" + key + "'", exception);
        }
    }

    @Override
    public void delete(String key) {
        StorageKeys.validate(key);
        try {
            HttpResponse<byte[]> response = exchange("DELETE", objectUri(key), HttpRequest.BodyPublishers.noBody(),
                    EMPTY_HASH, null, HttpResponse.BodyHandlers.ofByteArray(), 1);
            if (response.statusCode() == 404) {
                return;
            }
            requireSuccess(response, key);
        } catch (IOException | InterruptedException exception) {
            restoreInterrupt(exception);
            throw new StorageException("Failed to delete object '" + key + "'", exception);
        }
    }

    /** Bounded readiness probe for the configured bucket. */
    public void probe() {
        URI bucketUri = URI.create(trimTrailingSlash(properties.getS3().getEndpoint()) + "/"
                + properties.getS3().getBucket() + "?max-keys=0");
        try {
            HttpResponse<byte[]> response = exchange("GET", bucketUri, HttpRequest.BodyPublishers.noBody(),
                    EMPTY_HASH, null, HttpResponse.BodyHandlers.ofByteArray(), 1);
            requireSuccess(response, "bucket probe");
        } catch (IOException | InterruptedException exception) {
            restoreInterrupt(exception);
            throw new StorageException("Object-store startup probe failed", exception);
        }
    }

    private <T> HttpResponse<T> exchange(String method, URI uri, HttpRequest.BodyPublisher body,
                                         String payloadHash, String contentType,
                                         HttpResponse.BodyHandler<T> bodyHandler, int maxAttempts)
            throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            DependencyGuard.Permit permit;
            try {
                permit = dependencyGuard.acquire();
            } catch (DependencyGuard.RejectedException rejected) {
                throw new StorageException("Object store temporarily unavailable: " + rejected.reason(), rejected);
            }
            try (permit) {
                HttpResponse<T> response = sendOnce(method, uri, body, payloadHash, contentType, bodyHandler);
                int status = response.statusCode();
                if (status == 429 || status >= 500) {
                    permit.failure();
                    closeBody(response.body());
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

    private <T> HttpResponse<T> sendOnce(String method, URI uri, HttpRequest.BodyPublisher body,
                                         String payloadHash, String contentType,
                                         HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        StorageProperties.S3 s3 = properties.getS3();
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", AwsSigV4Signer.AMZ_DATE.format(now));
        if (contentType != null && !contentType.isBlank()) {
            headers.put("content-type", contentType);
        }
        String authorization = AwsSigV4Signer.authorization(method, uri, headers, payloadHash,
                s3.getAccessKey(), s3.getSecretKey(), s3.getRegion(), "s3", now);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(s3.getRequestTimeoutMs()))
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", headers.get("x-amz-date"))
                .header("Authorization", authorization);
        if (contentType != null && !contentType.isBlank()) {
            builder.header("Content-Type", contentType);
        }
        builder.method(method, body);
        return httpClient.send(builder.build(), bodyHandler);
    }

    private URI objectUri(String key) {
        StorageKeys.validate(key);
        return URI.create(trimTrailingSlash(properties.getS3().getEndpoint()) + "/"
                + properties.getS3().getBucket() + "/" + AwsSigV4Signer.encodeKeyPath(key));
    }

    private void requireSuccess(HttpResponse<?> response, String key) {
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new StorageException("Object-store request for '" + key + "' failed with HTTP " + status);
        }
    }

    private static void closeBody(Object body) {
        if (body instanceof InputStream input) {
            try {
                input.close();
            } catch (IOException ignored) {
                // The response is already being discarded after a retryable failure.
            }
        }
    }

    private static void restoreInterrupt(Exception exception) {
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
