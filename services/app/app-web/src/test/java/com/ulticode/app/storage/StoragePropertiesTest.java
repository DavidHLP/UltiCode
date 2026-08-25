package com.ulticode.app.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StorageProperties")
class StoragePropertiesTest {

    @Test
    @DisplayName("defaults to local with legacy uploads/ + /uploads contract")
    void defaultsToLocal() {
        StorageProperties properties = new StorageProperties();
        assertThat(properties.getType()).isEqualTo("local");
        assertThat(properties.isS3()).isFalse();
        assertThat(properties.getLocal().getRootDir()).isEqualTo("uploads");
        assertThat(properties.getLocal().getPublicUrlPrefix()).isEqualTo("/uploads");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unknown type is rejected")
    void unknownTypeRejected() {
        StorageProperties properties = new StorageProperties();
        properties.setType("gcs");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.storage.type");
    }

    @Test
    @DisplayName("s3 type requires endpoint, bucket and credentials")
    void s3RequiresConfiguration() {
        StorageProperties base = new StorageProperties();
        base.setType("s3");

        assertThatThrownBy(base::validate).hasMessageContaining("endpoint");

        StorageProperties missingBucket = new StorageProperties();
        missingBucket.setType("s3");
        missingBucket.getS3().setEndpoint("http://localhost:9000");
        assertThatThrownBy(missingBucket::validate).hasMessageContaining("bucket");

        StorageProperties missingCredentials = new StorageProperties();
        missingCredentials.setType("s3");
        missingCredentials.getS3().setEndpoint("http://localhost:9000");
        missingCredentials.getS3().setBucket("ulticode");
        assertThatThrownBy(missingCredentials::validate).hasMessageContaining("access-key");

        StorageProperties complete = new StorageProperties();
        complete.setType("s3");
        complete.getS3().setEndpoint("http://localhost:9000");
        complete.getS3().setBucket("ulticode");
        complete.getS3().setAccessKey("ak");
        complete.getS3().setSecretKey("sk");
        assertThat(complete.isS3()).isTrue();
        assertThatCode(complete::validate).doesNotThrowAnyException();
    }
}
