package com.ulticode.app.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the config-driven bean switch between local and S3 storage:
 * local must stay the matchIfMissing default so existing deployments are
 * unaffected, and only {@code app.storage.type=s3} activates S3Storage.
 */
@DisplayName("storage implementation selection")
class StorageImplementationSelectionTest {

    @Test
    @DisplayName("LocalStorage is the default (matchIfMissing)")
    void localIsDefault() {
        ConditionalOnProperty condition =
                LocalStorage.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("app.storage.type");
        assertThat(condition.havingValue()).isEqualTo(StorageProperties.TYPE_LOCAL);
        assertThat(condition.matchIfMissing()).isTrue();
    }

    @Test
    @DisplayName("S3Storage activates only for type=s3")
    void s3IsOptIn() {
        ConditionalOnProperty condition =
                S3Storage.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("app.storage.type");
        assertThat(condition.havingValue()).isEqualTo(StorageProperties.TYPE_S3);
        assertThat(condition.matchIfMissing()).isFalse();
    }
}
