package com.ulticode.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-level regression for the mandatory object-store gate.
 *
 * <p>The gate must run while the context is refreshing: a context that cannot reach the object store fails to start
 * (no local fallback, no serving window), and only an explicit opt-out makes it start without verification.
 */
@DisplayName("StorageAutoConfiguration context")
class StorageAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class));

    private ApplicationContextRunner withS3(String... extra) {
        String[] base = {
                "app.storage.type=s3",
                "app.storage.s3.endpoint=http://127.0.0.1:1",
                "app.storage.s3.region=us-east-1",
                "app.storage.s3.bucket=ulticode",
                "app.storage.s3.access-key=test-access-key",
                "app.storage.s3.secret-key=test-secret-key",
                "app.storage.s3.tls-enabled=false",
                "app.storage.s3.connect-timeout-ms=200",
                "app.storage.s3.request-timeout-ms=300",
                "app.storage.startup-probe.attempts=1",
                "app.storage.startup-probe.delay-ms=0"
        };
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return runner.withPropertyValues(all);
    }

    @Test
    @DisplayName("context refresh fails when the object store never answers")
    void refreshFailsWhenStoreUnreachable() {
        withS3().run(context -> {
            assertThat(context).hasFailed();
            // A failed refresh never exposes beans; the recorded FAILED state is
            // covered by StorageStartupProbeTest.
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("Object-store startup probe failed after 1 attempts");
        });
    }

    @Test
    @DisplayName("explicit opt-out starts the context with the gate recorded as SKIPPED")
    void explicitOptOutStartsUnverified() {
        withS3("app.storage.startup-probe.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FileStoragePort.class);
            assertThat(context).hasSingleBean(StorageReadiness.class);
            assertThat(context.getBean(StorageReadiness.class).state())
                    .isEqualTo(StorageReadiness.State.SKIPPED);
            assertThat(context.getBean(StorageReadiness.class).isReady()).isTrue();
        });
    }

    @Test
    @DisplayName("the gate stays eager under spring.main.lazy-initialization=true")
    void gateIsNotBypassedByLazyInitialization() {
        // The Core owner-context manager propagates lazy-initialization=true, so the
        // gate must not become a lazily created bean that the first object operation
        // could skip.
        withS3("spring.main.lazy-initialization=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("Object-store startup probe failed after 1 attempts");
        });
    }

    @Test
    @DisplayName("the gate still runs under lazy initialization when verification is opted out")
    void gateRunsUnderLazyInitializationWhenOptedOut() {
        withS3("spring.main.lazy-initialization=true", "app.storage.startup-probe.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(StorageReadiness.class).state())
                            .isEqualTo(StorageReadiness.State.SKIPPED);
                });
    }

    @Test
    @DisplayName("missing required configuration fails the context before any probe")
    void missingConfigurationFailsFast() {
        runner.withPropertyValues("app.storage.type=s3", "app.storage.s3.endpoint=http://127.0.0.1:1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("Missing required object-storage properties");
                });
    }

    @Test
    @DisplayName("app.storage.type=local is refused at startup")
    void localTypeIsRefused() {
        runner.withPropertyValues("app.storage.type=local").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("LocalStorage was removed");
        });
    }
}
