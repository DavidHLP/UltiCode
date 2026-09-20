package com.ulticode.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StorageProperties")
class StoragePropertiesTest {

    private StorageProperties complete(String endpoint, Boolean tls) {
        StorageProperties properties = new StorageProperties();
        properties.getS3().setEndpoint(endpoint);
        properties.getS3().setRegion("us-east-1");
        properties.getS3().setBucket("ulticode");
        properties.getS3().setAccessKey("ak");
        properties.getS3().setSecretKey("sk");
        properties.getS3().setTlsEnabled(tls);
        return properties;
    }

    @Test
    void defaultsToS3AndRequiresValues() {
        StorageProperties properties = new StorageProperties();
        assertThat(properties.getType()).isEqualTo(StorageProperties.TYPE_S3);
        assertThat(properties.getS3().getUploadTimeoutMs())
                .isEqualTo(StorageProperties.DEFAULT_UPLOAD_TIMEOUT_MS);
        assertThatThrownBy(properties::validate).hasMessageContaining("app.storage.s3.endpoint")
                .hasMessageContaining("app.storage.s3.tls-enabled");
    }

    @Test
    void localTypeIsRejectedExplicitly() {
        StorageProperties properties = complete("http://localhost:9000", false);
        properties.setType("local");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LocalStorage was removed");
    }

    @Test
    void unknownTypeIsRejected() {
        StorageProperties properties = complete("https://storage.example", true);
        properties.setType("gcs");
        assertThatThrownBy(properties::validate).hasMessageContaining("app.storage.type");
    }

    @Test
    void missingEachRequiredS3PropertyIsNamed() {
        StorageProperties endpoint = complete(" ", false);
        assertThatThrownBy(endpoint::validate).hasMessageContaining("endpoint");
        StorageProperties region = complete("http://localhost:9000", false);
        region.getS3().setRegion(" ");
        assertThatThrownBy(region::validate).hasMessageContaining("region");
        StorageProperties bucket = complete("http://localhost:9000", false);
        bucket.getS3().setBucket(" ");
        assertThatThrownBy(bucket::validate).hasMessageContaining("bucket");
        StorageProperties access = complete("http://localhost:9000", false);
        access.getS3().setAccessKey(" ");
        assertThatThrownBy(access::validate).hasMessageContaining("access-key");
        StorageProperties secret = complete("http://localhost:9000", false);
        secret.getS3().setSecretKey(" ");
        assertThatThrownBy(secret::validate).hasMessageContaining("secret-key");
        StorageProperties tls = complete("http://localhost:9000", null);
        assertThatThrownBy(tls::validate).hasMessageContaining("tls-enabled");
    }

    @Test
    void loopbackPlainHttpIsAllowedOnlyWhenTlsDisabled() {
        assertThatCode(() -> complete("http://localhost:9000", false).validate()).doesNotThrowAnyException();
        assertThatCode(() -> complete("http://127.0.0.1:9000", false).validate()).doesNotThrowAnyException();
        assertThatThrownBy(() -> complete("http://localhost:9000", true).validate())
                .hasMessageContaining("https");
        assertThatThrownBy(() -> complete("http://storage.example:9000", false).validate())
                .hasMessageContaining("loopback");
        assertThatThrownBy(() -> complete("https://storage.example", false).validate())
                .hasMessageContaining("loopback");
        assertThatCode(() -> complete("https://storage.example", true).validate()).doesNotThrowAnyException();
    }
    @Test
    void uploadTimeoutHasAnExplicitUpperBound() {
        StorageProperties properties = complete("http://localhost:9000", false);
        properties.getS3().setUploadTimeoutMs(StorageProperties.MAX_UPLOAD_TIMEOUT_MS + 1);

        assertThatThrownBy(properties::validate).hasMessageContaining("timeout");
    }
}
