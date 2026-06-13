package com.ulticode.modules.submission.sandbox.executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-language infrastructure failure detection (ADR-002 §2.5).
 *
 * <p>The pre-M2a {@code SandboxServiceImpl} hosted these as static
 * methods. They moved verbatim into {@link SandboxExecutorImpl};
 * this test class preserves the same fixture so the regression
 * coverage is unchanged.
 */
@DisplayName("SandboxExecutorImpl fork-failure detection (ADR-002 §2.5)")
class SandboxExecutorImplForkDetectionTest {

    @Test
    @DisplayName("Busybox sh 'Cannot fork' is detected as sandbox fork failure")
    void isSandboxForkFailure_busyboxShMessage_detected() {
        assertThat(SandboxExecutorImpl.isSandboxForkFailure("sh: 0: Cannot fork")).isTrue();
    }

    @Test
    @DisplayName("glibc / dockerd 'Resource temporarily unavailable' is detected as sandbox fork failure")
    void isSandboxForkFailure_dockerdResourcePressure_detected() {
        assertThat(SandboxExecutorImpl.isSandboxForkFailure(
                "docker: Error response from daemon: Resource temporarily unavailable.")).isTrue();
    }

    @Test
    @DisplayName("Kernel OOM 'fork: Cannot allocate memory' is detected as sandbox fork failure")
    void isSandboxForkFailure_kernelOomFork_detected() {
        assertThat(SandboxExecutorImpl.isSandboxForkFailure(
                "bash: fork: Cannot allocate memory")).isTrue();
    }

    @Test
    @DisplayName("User Python traceback is NOT classified as sandbox fork failure")
    void isSandboxForkFailure_userTraceback_notDetected() {
        String pythonTraceback = "Traceback (most recent call last):\n"
                + "  File \"/tmp/solution.py\", line 12, in <module>\n"
                + "    raise AttributeError(\"input list is empty\")\n"
                + "AttributeError: input list is empty\n";
        assertThat(SandboxExecutorImpl.isSandboxForkFailure(pythonTraceback)).isFalse();
    }

    @Test
    @DisplayName("Compilation error output is NOT classified as sandbox fork failure")
    void isSandboxForkFailure_compileError_notDetected() {
        String compileError = "solution.cpp:5:1: error: expected ';' before 'return'\n"
                + "    return 0;\n"
                + "    ^~~~~~\n";
        assertThat(SandboxExecutorImpl.isSandboxForkFailure(compileError)).isFalse();
    }

    @Test
    @DisplayName("null / empty output is NOT classified as sandbox fork failure")
    void isSandboxForkFailure_nullOrEmpty_notDetected() {
        assertThat(SandboxExecutorImpl.isSandboxForkFailure(null)).isFalse();
        assertThat(SandboxExecutorImpl.isSandboxForkFailure("")).isFalse();
        assertThat(SandboxExecutorImpl.isSandboxForkFailure("   \n  ")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            // Positive: each marker is unambiguous about a fork failure
            "'Cannot fork'",
            "'fork: Cannot allocate memory'",
            "'pids-limit reached'",
            "'cgroup pids limit exceeded'",
            "'RLIMIT_NPROC exceeded for user 1000'",
            "'docker: Cannot fork: resource temporarily unavailable'"
    })
    @DisplayName("docker daemon messages with unambiguous fork markers are classified as fork failures")
    void isDockerDaemonForkFailure_unambiguousMarkers_detected(String msg) {
        assertThat(SandboxExecutorImpl.isDockerDaemonForkFailure(msg)).isTrue();
    }

    @Test
    @DisplayName("docker daemon configuration warning mentioning 'pids' is NOT classified as fork failure")
    void isDockerDaemonForkFailure_configurationWarning_notDetected() {
        String warning = "WARNING: pids-limit not set, using docker default (no enforcement)";
        assertThat(SandboxExecutorImpl.isDockerDaemonForkFailure(warning)).isFalse();
    }

    @Test
    @DisplayName("docker daemon cgroup controller warning is NOT classified as fork failure")
    void isDockerDaemonForkFailure_cgroupControllerWarning_notDetected() {
        String warning = "pids controller disabled on cgroup v1 host; skipping pids.max enforcement";
        assertThat(SandboxExecutorImpl.isDockerDaemonForkFailure(warning)).isFalse();
    }

    @Test
    @DisplayName("User code mentioning 'pids' in unrelated context is NOT classified as fork failure")
    void isDockerDaemonForkFailure_userCodeMentionPids_notDetected() {
        String userOutput = "Total spawned pids: 42 (debug instrumentation)\n";
        assertThat(SandboxExecutorImpl.isDockerDaemonForkFailure(userOutput)).isFalse();
    }

    @Test
    @DisplayName("null / empty docker daemon message is NOT classified as fork failure")
    void isDockerDaemonForkFailure_nullOrEmpty_notDetected() {
        assertThat(SandboxExecutorImpl.isDockerDaemonForkFailure(null)).isFalse();
        assertThat(SandboxExecutorImpl.isDockerDaemonForkFailure("")).isFalse();
    }
}
