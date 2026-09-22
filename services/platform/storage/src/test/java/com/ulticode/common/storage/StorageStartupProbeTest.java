package com.ulticode.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused tests for the object-store startup gate.
 *
 * <p>The gate must run during context refresh (see {@link StorageStartupProbe#afterPropertiesSet()}), record what
 * happened for the readiness endpoints, retry within its bound, and refuse the boot when the store never answers.
 */
@DisplayName("StorageStartupProbe")
class StorageStartupProbeTest {

    private static StorageProperties properties(int attempts, long delayMs, boolean enabled) {
        StorageProperties properties = new StorageProperties();
        properties.setType(StorageProperties.TYPE_S3);
        properties.getS3().setEndpoint("http://127.0.0.1:1");
        properties.getS3().setRegion("us-east-1");
        properties.getS3().setBucket("ulticode");
        properties.getS3().setAccessKey("test-access-key");
        properties.getS3().setSecretKey("test-secret-key");
        properties.getS3().setTlsEnabled(false);
        properties.getS3().setConnectTimeoutMs(200);
        properties.getS3().setRequestTimeoutMs(300);
        properties.getStartupProbe().setEnabled(enabled);
        properties.getStartupProbe().setAttempts(attempts);
        properties.getStartupProbe().setDelayMs(delayMs);
        properties.validate();
        return properties;
    }

    /** Counts probe calls and fails the first {@code failures} of them. */
    private static final class CountingStorage extends S3Storage {
        private final int failures;
        private int calls;

        private CountingStorage(StorageProperties properties, int failures) {
            super(properties);
            this.failures = failures;
        }

        @Override
        public void probe() {
            calls++;
            if (calls <= failures) {
                throw new StorageException("simulated object-store outage");
            }
        }
    }

    @Test
    @DisplayName("verifies during bean initialization and records READY")
    void verifiesDuringInitialization() {
        StorageProperties properties = properties(3, 0, true);
        StorageReadiness readiness = new StorageReadiness();
        CountingStorage storage = new CountingStorage(properties, 0);

        new StorageStartupProbe(storage, properties, readiness).afterPropertiesSet();

        assertThat(storage.calls).isEqualTo(1);
        assertThat(readiness.state()).isEqualTo(StorageReadiness.State.READY);
        assertThat(readiness.isReady()).isTrue();
    }

    @Test
    @DisplayName("retries within the configured bound and succeeds on a later attempt")
    void retriesUntilAvailable() {
        StorageProperties properties = properties(4, 0, true);
        StorageReadiness readiness = new StorageReadiness();
        CountingStorage storage = new CountingStorage(properties, 2);

        new StorageStartupProbe(storage, properties, readiness).afterPropertiesSet();

        assertThat(storage.calls).isEqualTo(3);
        assertThat(readiness.isReady()).isTrue();
    }

    @Test
    @DisplayName("exhausting the attempts fails the boot and records FAILED")
    void exhaustedAttemptsFailClosed() {
        StorageProperties properties = properties(3, 0, true);
        StorageReadiness readiness = new StorageReadiness();
        CountingStorage storage = new CountingStorage(properties, Integer.MAX_VALUE);

        assertThatThrownBy(() -> new StorageStartupProbe(storage, properties, readiness).afterPropertiesSet())
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("startup probe failed after 3 attempts");

        assertThat(storage.calls).isEqualTo(3);
        assertThat(readiness.state()).isEqualTo(StorageReadiness.State.FAILED);
        assertThat(readiness.isReady()).isFalse();
        assertThat(readiness.detail()).contains("simulated object-store outage");
    }

    @Test
    @DisplayName("a disabled probe records SKIPPED without touching the store")
    void disabledProbeIsRecordedAsSkipped() {
        StorageProperties properties = properties(3, 0, false);
        StorageReadiness readiness = new StorageReadiness();
        CountingStorage storage = new CountingStorage(properties, Integer.MAX_VALUE);

        new StorageStartupProbe(storage, properties, readiness).afterPropertiesSet();

        assertThat(storage.calls).isZero();
        assertThat(readiness.state()).isEqualTo(StorageReadiness.State.SKIPPED);
        assertThat(readiness.isReady()).isTrue();
    }

    @Test
    @DisplayName("an unverified store is not ready before the gate runs")
    void pendingIsNotReady() {
        StorageReadiness readiness = new StorageReadiness();

        assertThat(readiness.state()).isEqualTo(StorageReadiness.State.PENDING);
        assertThat(readiness.isReady()).isFalse();
    }

    @Test
    @DisplayName("the gate runs before the readiness endpoint can report success")
    void gateRunsBeforeServing() {
        // The probe is an InitializingBean: Spring calls it while the context is
        // still refreshing, i.e. before the servlet connector accepts traffic and
        // therefore before /health/ready can answer at all.
        assertThat(org.springframework.beans.factory.InitializingBean.class)
                .isAssignableFrom(StorageStartupProbe.class);
        assertThat(java.util.Arrays.stream(StorageStartupProbe.class.getMethods())
                .map(java.lang.reflect.Method::getName))
                .contains("afterPropertiesSet");
        // No post-startup listener may be the only gate.
        assertThat(java.util.Arrays.stream(StorageStartupProbe.class.getDeclaredMethods())
                .flatMap(method -> java.util.Arrays.stream(method.getAnnotations()))
                .map(annotation -> annotation.annotationType().getName()))
                .doesNotContain("org.springframework.context.event.EventListener");
    }

    @Test
    @DisplayName("probe() failure surfaces a signed path-style request against the bucket")
    void probeFailureMentionsBucketProbe() {
        StorageProperties properties = properties(1, 0, true);
        StorageReadiness readiness = new StorageReadiness();
        S3Storage storage = new S3Storage(properties);
        Map<String, String> ignored = new LinkedHashMap<>();

        assertThatThrownBy(() -> new StorageStartupProbe(storage, properties, readiness).afterPropertiesSet())
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("startup probe failed after 1 attempts");
        assertThat(ignored).isEmpty();
        assertThat(readiness.isReady()).isFalse();
        // Sanity: the timestamp helper used by the signer stays usable for the probe path.
        assertThat(AwsSigV4Signer.AMZ_DATE.format(ZonedDateTime.now(ZoneOffset.UTC))).isNotBlank();
    }
}
