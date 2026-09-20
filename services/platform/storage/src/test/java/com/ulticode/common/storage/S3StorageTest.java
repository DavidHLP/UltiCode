package com.ulticode.common.storage;

import com.ulticode.common.resilience.DependencyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("S3Storage")
class S3StorageTest {

    @Mock private HttpClient httpClient;
    @Mock private HttpResponse<byte[]> response;
    @Mock private HttpResponse<InputStream> streamResponse;
    @Mock private InputStream responseBody;
    @Mock private HttpHeaders responseHeaders;

    private StorageProperties properties;
    private S3Storage storage;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.getS3().setEndpoint("http://localhost:9000");
        properties.getS3().setRegion("us-east-1");
        properties.getS3().setBucket("ulticode");
        properties.getS3().setAccessKey("AKIDEXAMPLE");
        properties.getS3().setSecretKey("secret");
        properties.getS3().setTlsEnabled(false);
        storage = new S3Storage(properties, httpClient);
    }

    private void respond(int status, byte[] body, String contentType) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(responseHeaders);
        when(responseHeaders.firstValue("Content-Type"))
                .thenReturn(contentType == null ? Optional.empty() : Optional.of(contentType));
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
    }

    @Nested
    @DisplayName("put()")
    class Put {
        @Test
        @DisplayName("sends a SigV4-signed path-style PUT")
        void signedPathStylePut() throws Exception {
            respond(200, new byte[0], null);
            storage.put("avatars/uuid.png", new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "image/png");

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            HttpRequest sent = captor.getValue();
            assertThat(sent.method()).isEqualTo("PUT");
            assertThat(sent.timeout()).contains(Duration.ofSeconds(30));
            assertThat(sent.uri()).isEqualTo(URI.create("http://localhost:9000/ulticode/avatars/uuid.png"));
            assertThat(sent.headers().firstValue("Authorization")).hasValueSatisfying(auth -> {
                assertThat(auth).startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/")
                        .contains("/us-east-1/s3/aws4_request, SignedHeaders=")
                        .contains("host;x-amz-content-sha256;x-amz-date");
                assertThat(auth.substring(auth.lastIndexOf('=') + 1)).matches("[0-9a-f]{64}");
            });
            assertThat(sent.headers().firstValue("Content-Type")).contains("image/png");
            assertThat(sent.bodyPublisher()).isPresent();
        }

        @Test
        void errorStatusRaises() throws Exception {
            respond(403, "<denied/>".getBytes(StandardCharsets.UTF_8), "application/xml");
            assertThatThrownBy(() -> storage.put("avatars/a.png", new ByteArrayInputStream(new byte[1]), 1,
                    "image/png")).isInstanceOf(StorageException.class).hasMessageContaining("403");
        }

        @Test
        void putFileUsesAFileBodyPublisher() throws Exception {
            respond(200, new byte[0], null);
            Path file = Files.createTempFile("storage-test", ".bin");
            try {
                Files.write(file, new byte[]{1, 2, 3});
                storage.putFile("admin/backups/a.sql", file, "application/sql");
                ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
                verify(httpClient).send(captor.capture(), any());
                assertThat(captor.getValue().bodyPublisher()).isPresent();
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

    @Nested
    @DisplayName("get()/openStream()/delete()")
    class GetAndDelete {
        @Test
        void getReturnsObject() throws Exception {
            respond(200, new byte[]{7, 8}, "application/octet-stream");
            Optional<FileStoragePort.StoredObject> fetched = storage.get("avatars/a.png");
            assertThat(fetched).isPresent();
            assertThat(fetched.get().content()).isEqualTo(new byte[]{7, 8});
            assertThat(fetched.get().contentType()).isEqualTo("application/octet-stream");
        }

        @Test
        void getMissingIsEmpty() throws Exception {
            respond(404, new byte[0], null);
            assertThat(storage.get("avatars/gone.png")).isEmpty();
        }
        @Test
        void openStreamClosesErrorBody() throws Exception {
            when(streamResponse.statusCode()).thenReturn(500);
            when(streamResponse.body()).thenReturn(responseBody);
            doReturn(streamResponse).when(httpClient).send(any(HttpRequest.class), any());

            assertThatThrownBy(() -> storage.openStream("avatars/broken.png"))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("500");

            // A 5xx is retried, so the body of every failed attempt must be closed;
            // assert "closed at least once" instead of an exact invocation count.
            verify(responseBody, atLeastOnce()).close();
        }

        @Test
        void deleteIsIdempotent() throws Exception {
            respond(204, new byte[0], null);
            storage.delete("avatars/a.png");
            respond(404, new byte[0], null);
            storage.delete("avatars/a.png");
            verify(httpClient, times(2)).send(any(HttpRequest.class), any());
        }

        @Test
        void retryBudgetDependsOnIdempotency() throws Exception {
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(new byte[]{7});
            when(response.headers()).thenReturn(responseHeaders);
            when(responseHeaders.firstValue("Content-Type")).thenReturn(Optional.empty());
            doThrow(new IOException("reset")).doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
            assertThat(storage.get("avatars/read.png")).isPresent();
            verify(httpClient, times(2)).send(any(HttpRequest.class), any());

            HttpClient writeClient = mock(HttpClient.class);
            doThrow(new IOException("reset")).when(writeClient).send(any(HttpRequest.class), any());
            S3Storage writeStorage = new S3Storage(properties, writeClient);
            assertThatThrownBy(() -> writeStorage.put("avatars/write.png", new ByteArrayInputStream(new byte[]{1}), 1,
                    "image/png")).isInstanceOf(StorageException.class);
            verify(writeClient).send(any(HttpRequest.class), any());
        }

        @Test
        void serverFailuresOpenCircuit() throws Exception {
            respond(503, new byte[0], null);
            S3Storage guarded = new S3Storage(properties, httpClient,
                    new DependencyGuard(1, 1, Duration.ofSeconds(30)));
            assertThatThrownBy(() -> guarded.get("avatars/a.png")).isInstanceOf(StorageException.class);
            assertThatThrownBy(() -> guarded.get("avatars/a.png")).isInstanceOf(StorageException.class)
                    .hasMessageContaining("temporarily unavailable");
            verify(httpClient).send(any(HttpRequest.class), any());
        }
    }

    @Nested
    @DisplayName("AwsSigV4Signer")
    class Signer {
        @Test
        void keyDerivationMatchesSpec() throws Exception {
            String secret = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
            byte[] kDate = hmac(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), "20150830");
            byte[] kRegion = hmac(kDate, "us-east-1");
            byte[] kService = hmac(kRegion, "iam");
            byte[] expected = hmac(kService, "aws4_request");
            byte[] actual = AwsSigV4Signer.hmacSha256(
                    AwsSigV4Signer.hmacSha256(
                            AwsSigV4Signer.hmacSha256(
                                    AwsSigV4Signer.hmacSha256(("AWS4" + secret).getBytes(StandardCharsets.UTF_8),
                                            "20150830"), "us-east-1"), "iam"), "aws4_request");
            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void authorizationIsDeterministic() {
            URI uri = URI.create("http://localhost:9000/ulticode/avatars/a.png");
            Map<String, String> headers = Map.of("host", "localhost:9000",
                    "x-amz-content-sha256", AwsSigV4Signer.sha256Hex(new byte[0]),
                    "x-amz-date", "20260825T120000Z");
            ZonedDateTime now = ZonedDateTime.of(2026, 8, 25, 12, 0, 0, 0, ZoneOffset.UTC);
            String first = AwsSigV4Signer.authorization("PUT", uri, headers, AwsSigV4Signer.sha256Hex(new byte[0]),
                    "AK", "SK", "us-east-1", "s3", now);
            String second = AwsSigV4Signer.authorization("PUT", uri, headers, AwsSigV4Signer.sha256Hex(new byte[0]),
                    "AK", "SK", "us-east-1", "s3", now);
            assertThat(first).isEqualTo(second).startsWith("AWS4-HMAC-SHA256 Credential=AK/");
        }

        @Test
        void encodesKeyPath() {
            assertThat(AwsSigV4Signer.encodeKeyPath("avatars/uuid name.png")).isEqualTo("avatars/uuid%20name.png");
            assertThat(AwsSigV4Signer.encodeKeyPath("a/b~c.d-e_f")).isEqualTo("a/b~c.d-e_f");
        }

        private byte[] hmac(byte[] key, String data) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        }
    }
}
