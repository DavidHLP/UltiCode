package com.ulticode.common.storage;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Instance-level smoke test against a real RustFS (or any S3-compatible) endpoint.
 *
 * <p>Disabled unless {@code RUSTFS_SMOKE_ENDPOINT} is set so the hermetic unit-test run is unaffected; run it through
 * {@code scripts/dev/rustfs-smoke-test.sh}, which also covers the container-restart persistence case via
 * {@code RUSTFS_SMOKE_MARKER_MODE=write|read}.
 */
@DisplayName("RustFS instance smoke test")
class RustFsSmokeTest {

    private static final String ACCOUNT = "smoke-account";
    private static final String MARKER_KEY = "app/avatars/" + ACCOUNT + "/persistence-marker.bin";
    private static final byte[] MARKER = "rustfs-persistence-marker".getBytes(StandardCharsets.UTF_8);

    private static String env(String name) {
        String value = System.getenv(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), name + " is not set");
        return value;
    }

    private static StorageProperties properties() {
        StorageProperties properties = new StorageProperties();
        properties.setType(StorageProperties.TYPE_S3);
        properties.getS3().setEndpoint(env("RUSTFS_SMOKE_ENDPOINT"));
        properties.getS3().setRegion(System.getenv().getOrDefault("RUSTFS_SMOKE_REGION", "us-east-1"));
        properties.getS3().setBucket(System.getenv().getOrDefault("RUSTFS_SMOKE_BUCKET", "ulticode"));
        properties.getS3().setAccessKey(env("RUSTFS_SMOKE_ACCESS_KEY"));
        properties.getS3().setSecretKey(env("RUSTFS_SMOKE_SECRET_KEY"));
        properties.getS3().setTlsEnabled(Boolean.parseBoolean(System.getenv().getOrDefault("RUSTFS_SMOKE_TLS", "false")));
        properties.getStartupProbe().setEnabled(false);
        properties.validate();
        return properties;
    }

    private static S3Storage storage() {
        return new S3Storage(properties());
    }

    private static byte[] deterministicBytes(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i * 31 + 7);
        }
        return bytes;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Creates the bucket when it does not exist yet; idempotent for the smoke container. */
    private static void ensureBucket(StorageProperties properties) throws IOException, InterruptedException {
        URI endpoint = URI.create(properties.getS3().getEndpoint());
        String host = endpoint.getHost();
        int port = endpoint.getPort() > 0 ? endpoint.getPort()
                : ("https".equalsIgnoreCase(endpoint.getScheme()) ? 443 : 80);
        URI bucketUri = URI.create(properties.getS3().getEndpoint() + "/" + properties.getS3().getBucket());
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", host + ":" + port);
        headers.put("x-amz-content-sha256",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        headers.put("x-amz-date", AwsSigV4Signer.AMZ_DATE.format(now));
        String authorization = AwsSigV4Signer.authorization("PUT", bucketUri, headers,
                headers.get("x-amz-content-sha256"), properties.getS3().getAccessKey(),
                properties.getS3().getSecretKey(), properties.getS3().getRegion(), "s3", now);

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(bucketUri)
                        .header("x-amz-content-sha256", headers.get("x-amz-content-sha256"))
                        .header("x-amz-date", headers.get("x-amz-date"))
                        .header("Authorization", authorization)
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("create-bucket status (409 = already owned): %s", response.body())
                .isIn(200, 204, 409);
    }

    @Test
    @DisplayName("bucket exists or is created through a signed path-style request")
    void bucketIsReady() throws Exception {
        ensureBucket(properties());
    }

    @Test
    @DisplayName("put/get/openStream/putFile/delete round trip against the live instance")
    void putGetDeleteRoundTrip() throws IOException, InterruptedException {
        ensureBucket(properties());
        S3Storage storage = storage();
        byte[] content = deterministicBytes(4096);
        String key = StorageKeys.avatarKey(ACCOUNT, UUID.randomUUID() + ".png");

        storage.put(key, new ByteArrayInputStream(content), content.length, "image/png");
        Optional<FileStoragePort.StoredObject> fetched = storage.get(key);
        assertThat(fetched).isPresent();
        assertThat(sha256(fetched.get().content())).isEqualTo(sha256(content));
        assertThat(fetched.get().contentType()).isEqualTo("image/png");

        Optional<FileStoragePort.StorageStream> streamed = storage.openStream(key);
        assertThat(streamed).isPresent();
        try (InputStream in = streamed.get().content()) {
            assertThat(sha256(in.readAllBytes())).isEqualTo(sha256(content));
            assertThat(streamed.get().contentLength()).isEqualTo(content.length);
        }

        Path tempFile = Files.createTempFile("rustfs-smoke", ".bin");
        try {
            Files.write(tempFile, content);
            String fileKey = StorageKeys.avatarKey(ACCOUNT, UUID.randomUUID() + ".png");
            storage.putFile(fileKey, tempFile, "image/png");
            assertThat(storage.get(fileKey)).isPresent();
            storage.delete(fileKey);
        } finally {
            Files.deleteIfExists(tempFile);
        }

        storage.delete(key);
        assertThat(storage.get(key)).isEmpty();
        storage.delete(key);
    }

    @Test
    @DisplayName("private objects cannot be read through an unsigned URL")
    void unsignedReadIsRefused() throws Exception {
        StorageProperties properties = properties();
        ensureBucket(properties);
        S3Storage storage = new S3Storage(properties);
        byte[] content = deterministicBytes(256);
        String key = StorageKeys.avatarKey(ACCOUNT, UUID.randomUUID() + ".png");
        storage.put(key, new ByteArrayInputStream(content), content.length, "image/png");

        URI endpoint = URI.create(properties.getS3().getEndpoint());
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.getHost(), endpoint.getPort()), 5_000);
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /" + properties.getS3().getBucket() + "/" + key + " HTTP/1.0\r\n"
                    + "Host: " + endpoint.getHost() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            String statusLine = new String(socket.getInputStream().readNBytes(64), StandardCharsets.UTF_8)
                    .lines().findFirst().orElse("");
            assertThat(statusLine).containsAnyOf("403", "401");
        } finally {
            storage.delete(key);
        }
    }

    @Test
    @DisplayName("an unavailable object store fails with a clear storage error")
    void unavailableEndpointFailsClearly() {
        StorageProperties properties = new StorageProperties();
        properties.setType(StorageProperties.TYPE_S3);
        properties.getS3().setEndpoint("http://127.0.0.1:1");
        properties.getS3().setRegion("us-east-1");
        properties.getS3().setBucket("ulticode");
        properties.getS3().setAccessKey("unavailable");
        properties.getS3().setSecretKey("unavailable");
        properties.getS3().setTlsEnabled(false);
        properties.getS3().setConnectTimeoutMs(500);
        properties.getS3().setRequestTimeoutMs(1_000);
        properties.getStartupProbe().setEnabled(false);
        properties.validate();

        S3Storage storage = new S3Storage(properties);
        assertThatThrownBy(() -> storage.put("app/avatars/smoke-account/unreachable.png",
                new ByteArrayInputStream(new byte[]{1}), 1, "image/png"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("app/avatars/smoke-account/unreachable.png");
    }

    @Test
    @DisplayName("restart persistence: write the marker object")
    void writePersistenceMarker() {
        Assumptions.assumeTrue("write".equals(System.getenv("RUSTFS_SMOKE_MARKER_MODE")),
                "RUSTFS_SMOKE_MARKER_MODE != write");
        S3Storage storage = storage();
        storage.put(MARKER_KEY, new ByteArrayInputStream(MARKER), MARKER.length, "application/octet-stream");
        assertThat(storage.get(MARKER_KEY)).isPresent();
    }

    @Test
    @DisplayName("restart persistence: read the marker object written before the restart")
    void readPersistenceMarker() {
        Assumptions.assumeTrue("read".equals(System.getenv("RUSTFS_SMOKE_MARKER_MODE")),
                "RUSTFS_SMOKE_MARKER_MODE != read");
        Optional<FileStoragePort.StoredObject> fetched = storage().get(MARKER_KEY);
        assertThat(fetched).as("marker object must survive the container restart").isPresent();
        assertThat(fetched.get().content()).isEqualTo(MARKER);
    }
}
