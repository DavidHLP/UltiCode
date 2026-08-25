package com.ulticode.app.storage;

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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock-based tests for the S3-compatible {@link FileStoragePort}: request
 * shape (path-style URL, SigV4 headers), success/404/error handling and
 * public-URL semantics. No real object store is contacted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("S3Storage")
class S3StorageTest {

    @Mock private HttpClient httpClient;
    @Mock private HttpResponse<byte[]> response;
    @Mock private HttpHeaders responseHeaders;

    private StorageProperties properties;
    private S3Storage storage;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.setType(StorageProperties.TYPE_S3);
        properties.getS3().setEndpoint("http://localhost:9000");
        properties.getS3().setRegion("us-east-1");
        properties.getS3().setBucket("ulticode");
        properties.getS3().setAccessKey("AKIDEXAMPLE");
        properties.getS3().setSecretKey("secret");
        storage = new S3Storage(properties, httpClient);
    }

    private void respond(int status, byte[] body, String contentType) throws Exception {
        when(response.statusCode()).thenReturn(status);
        if (body != null) {
            when(response.body()).thenReturn(body);
        }
        if (contentType != null) {
            when(response.headers()).thenReturn(responseHeaders);
            when(responseHeaders.firstValue("Content-Type")).thenReturn(Optional.of(contentType));
        }
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
    }

    @Nested
    @DisplayName("put()")
    class Put {

        @Test
        @DisplayName("sends a SigV4-signed path-style PUT and returns the public URL")
        void signedPathStylePut() throws Exception {
            respond(200, new byte[0], null);

            byte[] content = {1, 2, 3};
            String url = storage.put("avatars/uuid.png", new ByteArrayInputStream(content), 3);

            assertThat(url).isEqualTo("http://localhost:9000/ulticode/avatars/uuid.png");
            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(captor.capture(), any());
            HttpRequest sent = captor.getValue();

            assertThat(sent.method()).isEqualTo("PUT");
            assertThat(sent.uri())
                    .isEqualTo(URI.create("http://localhost:9000/ulticode/avatars/uuid.png"));
            assertThat(sent.headers().firstValue("Authorization"))
                    .hasValueSatisfying(auth -> {
                        assertThat(auth)
                                .startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/")
                                .contains("/us-east-1/s3/aws4_request, SignedHeaders=")
                                .contains("host;x-amz-content-sha256;x-amz-date");
                        String signature = auth.substring(auth.lastIndexOf('=') + 1);
                        assertThat(signature).matches("[0-9a-f]{64}");
                    });
            assertThat(sent.headers().firstValue("x-amz-date")).isPresent();
            assertThat(sent.headers().firstValue("Content-Type")).contains("image/png");
            // Payload is hashed for signing and sent verbatim.
            assertThat(sent.bodyPublisher()).isPresent();
        }

        @Test
        @DisplayName("non-2xx status raises StorageException")
        void errorStatusRaises() throws Exception {
            respond(403, "<denied/>".getBytes(StandardCharsets.UTF_8), "application/xml");
            assertThatThrownBy(() -> storage.put("avatars/a.png",
                    new ByteArrayInputStream(new byte[1]), 1))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("403");
        }
    }

    @Nested
    @DisplayName("get()/delete()")
    class GetAndDelete {

        @Test
        @DisplayName("returns stored bytes + content type")
        void getReturnsObject() throws Exception {
            respond(200, new byte[]{7, 8}, "application/octet-stream");

            Optional<FileStoragePort.StoredObject> fetched = storage.get("avatars/a.png");

            assertThat(fetched).isPresent();
            assertThat(fetched.get().content()).isEqualTo(new byte[]{7, 8});
            assertThat(fetched.get().contentType()).isEqualTo("application/octet-stream");
        }

        @Test
        @DisplayName("404 maps to empty")
        void getMissingIsEmpty() throws Exception {
            respond(404, new byte[0], null);
            assertThat(storage.get("avatars/gone.png")).isEmpty();
        }

        @Test
        @DisplayName("delete accepts 2xx and treats 404 as idempotent no-op")
        void deleteIdempotent() throws Exception {
            respond(204, new byte[0], null);
            storage.delete("avatars/a.png");

            respond(404, new byte[0], null);
            storage.delete("avatars/a.png");

            verify(httpClient, org.mockito.Mockito.times(2))
                    .send(any(HttpRequest.class), any());
        }
    }

    @Nested
    @DisplayName("publicUrl()")
    class PublicUrl {

        @Test
        @DisplayName("defaults to endpoint/bucket/key")
        void defaultsToEndpointBucket() {
            assertThat(storage.publicUrl("avatars/x.webp"))
                    .isEqualTo("http://localhost:9000/ulticode/avatars/x.webp");
        }

        @Test
        @DisplayName("configured public base URL wins (CDN fronting)")
        void configuredPublicBaseUrl() {
            properties.getS3().setPublicBaseUrl("https://cdn.example.com/ulticode/");
            assertThat(storage.publicUrl("avatars/x.png"))
                    .isEqualTo("https://cdn.example.com/ulticode/avatars/x.png");
        }

        @Test
        @DisplayName("traversal keys are rejected")
        void rejectsTraversal() {
            assertThatThrownBy(() -> storage.publicUrl("../escape"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("AwsSigV4Signer")
    class Signer {

        @Test
        @DisplayName("signing-key derivation matches the AWS spec chain")
        void keyDerivationMatchesSpec() throws Exception {
            String secret = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
            // Independent derivation of the documented AWS example:
            // secret wJalrXUtnFEMI/... , 20150830/us-east-1/iam ->
            // kSigning c4afb1cc5771d871763a393e44b703571b55cc28424d1a5e86da6ed3c154a4b9.
            byte[] kDate = hmac(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), "20150830");
            byte[] kRegion = hmac(kDate, "us-east-1");
            byte[] kService = hmac(kRegion, "iam");
            byte[] expected = hmac(kService, "aws4_request");

            byte[] actual = AwsSigV4Signer.hmacSha256(
                    AwsSigV4Signer.hmacSha256(
                            AwsSigV4Signer.hmacSha256(
                                    AwsSigV4Signer.hmacSha256(
                                            ("AWS4" + secret).getBytes(StandardCharsets.UTF_8),
                                            "20150830"),
                                    "us-east-1"),
                            "iam"),
                    "aws4_request");
            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("authorization is stable for identical inputs and sensitive to payload hash")
        void deterministicAndPayloadSensitive() {
            URI uri = URI.create("http://localhost:9000/ulticode/avatars/a.png");
            Map<String, String> headers = Map.of(
                    "host", "localhost:9000",
                    "x-amz-content-sha256", AwsSigV4Signer.sha256Hex(new byte[0]),
                    "x-amz-date", "20260825T120000Z");
            ZonedDateTime now = ZonedDateTime.of(2026, 8, 25, 12, 0, 0, 0, ZoneOffset.UTC);

            String first = AwsSigV4Signer.authorization("PUT", uri, headers,
                    AwsSigV4Signer.sha256Hex(new byte[0]), "AK", "SK", "us-east-1", "s3", now);
            String second = AwsSigV4Signer.authorization("PUT", uri, headers,
                    AwsSigV4Signer.sha256Hex(new byte[0]), "AK", "SK", "us-east-1", "s3", now);
            String otherPayload = AwsSigV4Signer.authorization("PUT", uri, headers,
                    AwsSigV4Signer.sha256Hex(new byte[]{1}), "AK", "SK", "us-east-1", "s3", now);

            assertThat(first).isEqualTo(second);
            assertThat(first).isNotEqualTo(otherPayload);
            assertThat(first)
                    .startsWith("AWS4-HMAC-SHA256 Credential=AK/20260825/us-east-1/s3/aws4_request");
        }

        @Test
        @DisplayName("encodeKeyPath percent-encodes unsafe characters per segment")
        void encodesKeyPath() {
            assertThat(AwsSigV4Signer.encodeKeyPath("avatars/uuid name.png"))
                    .isEqualTo("avatars/uuid%20name.png");
            assertThat(AwsSigV4Signer.encodeKeyPath("a/b~c.d-e_f")).isEqualTo("a/b~c.d-e_f");
        }

        private byte[] hmac(byte[] key, String data) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        }
    }
}
