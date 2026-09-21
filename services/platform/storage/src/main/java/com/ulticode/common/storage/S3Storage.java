package com.ulticode.common.storage;

import com.ulticode.common.resilience.DependencyGuard;

import java.io.FilterInputStream;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** S3-compatible, path-style object storage implementation. If configured,
 * {@code app.storage.s3.ca-certificate-path} adds operator CA certificates
 * to the JVM default trust anchors without disabling hostname verification. */
public class S3Storage implements FileStoragePort {

    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);
    private static final int READ_ATTEMPTS = 2;
    private static final String EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private final ExecutorService streamReadExecutor;

    private final StorageProperties properties;
    private final HttpClient httpClient;
    private final DependencyGuard dependencyGuard;
    private final StorageReadiness readiness;

    public S3Storage(StorageProperties properties) {
        this(properties, createHttpClient(properties), new StorageReadiness());
    }

    public S3Storage(StorageProperties properties, StorageReadiness readiness) {
        this(properties, createHttpClient(properties), new DependencyGuard(
                properties.getS3().getMaxConcurrentRequests(), FAILURE_THRESHOLD, OPEN_DURATION), readiness);
    }

    private static HttpClient createHttpClient(StorageProperties properties) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getS3().getConnectTimeoutMs()));
        StorageTlsSupport.sslContext(properties.getS3()).ifPresent(builder::sslContext);
        return builder.build();
    }

    private static ExecutorService createStreamReadExecutor(int maxConcurrentRequests) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                maxConcurrentRequests,
                maxConcurrentRequests,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(maxConcurrentRequests),
                runnable -> {
                    Thread thread = new Thread(runnable, "s3-stream-read");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    S3Storage(StorageProperties properties, HttpClient httpClient) {
        this(properties, httpClient, new StorageReadiness());
    }

    S3Storage(StorageProperties properties, HttpClient httpClient, StorageReadiness readiness) {
        this(properties, httpClient, new DependencyGuard(
                properties.getS3().getMaxConcurrentRequests(), FAILURE_THRESHOLD, OPEN_DURATION), readiness);
    }

    S3Storage(StorageProperties properties, HttpClient httpClient, DependencyGuard dependencyGuard) {
        this(properties, httpClient, dependencyGuard, new StorageReadiness());
    }

    S3Storage(StorageProperties properties, HttpClient httpClient, DependencyGuard dependencyGuard,
              StorageReadiness readiness) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.dependencyGuard = dependencyGuard;
        this.readiness = readiness;
        readiness.setRecoveryProbe(this::probeForRecovery);
        this.streamReadExecutor = createStreamReadExecutor(properties.getS3().getMaxConcurrentRequests());
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
        } catch (StorageException exception) {
            readiness.markFailed(exception.getMessage());
            throw exception;
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
                    contentType, HttpResponse.BodyHandlers.ofByteArray(), 1,
                    properties.getS3().getUploadTimeoutMs()), key);
        } catch (IOException | InterruptedException exception) {
            restoreInterrupt(exception);
            throw new StorageException("Failed to store object '" + key + "'", exception);
        } catch (StorageException exception) {
            readiness.markFailed(exception.getMessage());
            throw exception;
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
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= READ_ATTEMPTS; attempt++) {
            DependencyGuard.Permit permit;
            try {
                permit = dependencyGuard.acquire();
            } catch (DependencyGuard.RejectedException rejected) {
                String detail = "Object store temporarily unavailable: " + rejected.reason();
                readiness.markFailed(detail);
                throw new StorageException(detail, rejected);
            }
            HttpResponse<InputStream> response = null;
            try {
                response = sendOnce("GET", objectUri(key), HttpRequest.BodyPublishers.noBody(), EMPTY_HASH, null,
                        HttpResponse.BodyHandlers.ofInputStream(), properties.getS3().getRequestTimeoutMs());
                int status = response.statusCode();
                if (status == 404) {
                    closeStreamingBody(response.body());
                    readiness.markReady();
                    permit.success();
                    return Optional.empty();
                }
                if (status == 429 || status >= 500) {
                    readiness.markFailed("Object store returned HTTP " + status);
                    permit.failure();
                    closeStreamingBody(response.body());
                    if (attempt < READ_ATTEMPTS) {
                        continue;
                    }
                    requireSuccess(response, key);
                } else if (status / 100 == 2) {
                    InputStream body = response.body();
                    if (body == null) {
                        throw new IOException("Object-store response body is missing");
                    }
                    long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    String contentType = response.headers().firstValue("Content-Type").orElse(null);
                    readiness.markReady();
                    return Optional.of(new StorageStream(
                            new GuardedInputStream(
                                    new TimeoutInputStream(
                                            body, properties.getS3().getRequestTimeoutMs(), streamReadExecutor),
                                    permit, readiness),
                            contentLength, contentType));
                } else {
                    readiness.markFailed("Object store returned HTTP " + status);
                    permit.failure();
                    closeStreamingBody(response.body());
                    requireSuccess(response, key);
                }
            } catch (InterruptedException exception) {
                permit.ignore();
                restoreInterrupt(exception);
                throw new StorageException("Failed to stream object '" + key + "'", exception);
            } catch (IOException exception) {
                readiness.markFailed(exception.getMessage());
                permit.failure();
                lastFailure = exception;
                if (attempt == READ_ATTEMPTS) {
                    throw new StorageException("Failed to stream object '" + key + "'", exception);
                }
            } catch (StorageException exception) {
                readiness.markFailed(exception.getMessage());
                permit.failure();
                throw exception;
            } catch (RuntimeException exception) {
                readiness.markFailed(exception.getMessage());
                permit.failure();
                if (response != null) {
                    try {
                        closeStreamingBody(response.body());
                    } catch (IOException closeFailure) {
                        exception.addSuppressed(closeFailure);
                    }
                }
                throw new StorageException("Failed to stream object '" + key + "'", exception);
            }
        }
        throw new StorageException("Failed to stream object '" + key + "'", lastFailure);
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

    /**
     * Bounded readiness probe for the configured bucket.
     *
     * <p>Use the bucket-location subresource instead of a bare HeadBucket
     * request so prefix-scoped IAM users do not need unrestricted ListBucket.
     */
    public void probe() {
        probeBucket(true);
    }

    private void probeForRecovery() {
        probeBucket(false);
    }

    private void probeBucket(boolean updateReadiness) {
        URI bucketLocationUri = URI.create(trimTrailingSlash(properties.getS3().getEndpoint()) + "/"
                + properties.getS3().getBucket() + "?location=");
        try {
            HttpResponse<Void> response = exchange("GET", bucketLocationUri,
                    HttpRequest.BodyPublishers.noBody(), EMPTY_HASH, null,
                    HttpResponse.BodyHandlers.discarding(), 1,
                    properties.getS3().getRequestTimeoutMs(), updateReadiness);
            requireSuccess(response, "bucket probe");
        } catch (IOException | InterruptedException exception) {
            restoreInterrupt(exception);
            throw new StorageException("Object-store startup probe failed", exception);
        } catch (StorageException exception) {
            if (updateReadiness) {
                readiness.markFailed(exception.getMessage());
            }
            throw exception;
        }
    }

    private <T> HttpResponse<T> exchange(String method, URI uri, HttpRequest.BodyPublisher body,
                                         String payloadHash, String contentType,
                                         HttpResponse.BodyHandler<T> bodyHandler, int maxAttempts)
            throws IOException, InterruptedException {
        return exchange(method, uri, body, payloadHash, contentType, bodyHandler, maxAttempts,
                properties.getS3().getRequestTimeoutMs());
    }

    private <T> HttpResponse<T> exchange(String method, URI uri, HttpRequest.BodyPublisher body,
                                         String payloadHash, String contentType,
                                         HttpResponse.BodyHandler<T> bodyHandler, int maxAttempts, int timeoutMs)
            throws IOException, InterruptedException {
        return exchange(method, uri, body, payloadHash, contentType, bodyHandler, maxAttempts, timeoutMs, true);
    }

    private <T> HttpResponse<T> exchange(String method, URI uri, HttpRequest.BodyPublisher body,
                                         String payloadHash, String contentType,
                                         HttpResponse.BodyHandler<T> bodyHandler, int maxAttempts, int timeoutMs,
                                         boolean updateReadiness)
            throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            DependencyGuard.Permit permit;
            try {
                permit = dependencyGuard.acquire();
            } catch (DependencyGuard.RejectedException rejected) {
                String detail = "Object store temporarily unavailable: " + rejected.reason();
                if (updateReadiness) {
                    readiness.markFailed(detail);
                }
                throw new StorageException(detail, rejected);
            }
            try (permit) {
                HttpResponse<T> response = sendOnce(method, uri, body, payloadHash, contentType, bodyHandler, timeoutMs);
                int status = response.statusCode();
                if (status / 100 == 2 || status == 404) {
                    if (updateReadiness) {
                        readiness.markReady();
                    }
                    permit.success();
                } else {
                    if (updateReadiness) {
                        readiness.markFailed("Object store returned HTTP " + status);
                    }
                    permit.failure();
                    closeBody(response.body());
                    if (status == 429 || status >= 500) {
                        if (attempt < maxAttempts) {
                            continue;
                        }
                    }
                }
                return response;
            } catch (InterruptedException interrupted) {
                permit.ignore();
                throw interrupted;
            } catch (IOException failure) {
                if (updateReadiness) {
                    readiness.markFailed(failure.getMessage());
                }
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
        return sendOnce(method, uri, body, payloadHash, contentType, bodyHandler,
                properties.getS3().getRequestTimeoutMs());
    }

    private <T> HttpResponse<T> sendOnce(String method, URI uri, HttpRequest.BodyPublisher body,
                                         String payloadHash, String contentType,
                                         HttpResponse.BodyHandler<T> bodyHandler, int timeoutMs)
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
                .timeout(Duration.ofMillis(timeoutMs))
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

    @FunctionalInterface
    private interface ReadOperation<T> {
        T execute() throws IOException;
    }

    private static final class TimeoutInputStream extends FilterInputStream {

        private final int timeoutMs;
        private final ExecutorService executor;

        private TimeoutInputStream(InputStream delegate, int timeoutMs, ExecutorService executor) {
            super(delegate);
            this.timeoutMs = timeoutMs;
            this.executor = executor;
        }

        @Override
        public int read() throws IOException {
            return withTimeout(in::read);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return withTimeout(() -> in.read(bytes, offset, length));
        }

        @Override
        public long skip(long count) throws IOException {
            return withTimeout(() -> in.skip(count));
        }

        private <T> T withTimeout(ReadOperation<T> operation) throws IOException {
            Future<T> future;
            try {
                future = executor.submit(operation::execute);
            } catch (RejectedExecutionException exception) {
                closeDelegate();
                throw new IOException("Object-store stream read capacity exhausted", exception);
            }
            try {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                future.cancel(true);
                closeDelegate();
                throw new IOException("Object-store stream read timed out after " + timeoutMs + " ms", exception);
            } catch (InterruptedException exception) {
                future.cancel(true);
                closeDelegate();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading object-store response body", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof IOException ioException) {
                    throw ioException;
                }
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IOException("Failed to read object-store response body", cause);
            }
        }

        private void closeDelegate() {
            try {
                in.close();
            } catch (IOException ignored) {
                // The read is already failing; the caller records the failure.
            }
        }
    }

    private static final class GuardedInputStream extends FilterInputStream {

        private final DependencyGuard.Permit permit;
        private final StorageReadiness readiness;
        private boolean closed;
        private boolean failed;

        private GuardedInputStream(InputStream delegate, DependencyGuard.Permit permit, StorageReadiness readiness) {
            super(delegate);
            this.permit = permit;
            this.readiness = readiness;
        }

        @Override
        public int read() throws IOException {
            try {
                int result = super.read();
                if (!failed) {
                    readiness.markReady();
                }
                if (result == -1) {
                    close();
                }
                return result;
            } catch (IOException exception) {
                failed = true;
                readiness.markFailed(exception.getMessage());
                permit.failure();
                throw exception;
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            try {
                int result = super.read(bytes, offset, length);
                if (!failed) {
                    readiness.markReady();
                }
                if (result == -1) {
                    close();
                }
                return result;
            } catch (IOException exception) {
                failed = true;
                readiness.markFailed(exception.getMessage());
                permit.failure();
                throw exception;
            }
        }

        @Override
        public long skip(long count) throws IOException {
            try {
                long result = super.skip(count);
                if (!failed) {
                    readiness.markReady();
                }
                return result;
            } catch (IOException exception) {
                failed = true;
                readiness.markFailed(exception.getMessage());
                permit.failure();
                throw exception;
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                super.close();
                if (!failed) {
                    readiness.markReady();
                    permit.success();
                }
            } catch (IOException exception) {
                failed = true;
                readiness.markFailed(exception.getMessage());
                permit.failure();
                throw exception;
            }
        }
    }
    private static void closeStreamingBody(InputStream body) throws IOException {
        if (body != null) {
            body.close();
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
