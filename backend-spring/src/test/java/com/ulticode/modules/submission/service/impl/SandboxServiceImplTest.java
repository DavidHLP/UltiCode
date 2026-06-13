package com.ulticode.modules.submission.service.impl;

import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.service.CodeExecutionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("SandboxServiceImpl")
class SandboxServiceImplTest {

    private final SandboxServiceImpl sandboxService = new SandboxServiceImpl(
            new DockerSandboxConfig(
                    true,
                    "ulticode-sandbox:latest",
                    "128m",
                    "1.0",
                    30,
                    128,
                    "/tmp/seccomp",
                    null,            // d-form: null → D-form disabled, Form A path is exercised
                    Map.of()
            ),
            mock(CodeExecutionHelper.class)
    );





    @Test
    @DisplayName("Busybox sh 'Cannot fork' is detected as sandbox fork failure")
    void isSandboxForkFailure_busyboxShMessage_detected() {
        assertThat(SandboxServiceImpl.isSandboxForkFailure("sh: 0: Cannot fork")).isTrue();
    }

    @Test
    @DisplayName("glibc / dockerd 'Resource temporarily unavailable' is detected as sandbox fork failure")
    void isSandboxForkFailure_dockerdResourcePressure_detected() {
        assertThat(SandboxServiceImpl.isSandboxForkFailure(
                "docker: Error response from daemon: Resource temporarily unavailable.")).isTrue();
    }

    @Test
    @DisplayName("Kernel OOM 'fork: Cannot allocate memory' is detected as sandbox fork failure")
    void isSandboxForkFailure_kernelOomFork_detected() {
        assertThat(SandboxServiceImpl.isSandboxForkFailure(
                "bash: fork: Cannot allocate memory")).isTrue();
    }

    @Test
    @DisplayName("User Python traceback is NOT classified as sandbox fork failure")
    void isSandboxForkFailure_userTraceback_notDetected() {
        String pythonTraceback = "Traceback (most recent call last):\n"
                + "  File \"/tmp/solution.py\", line 12, in <module>\n"
                + "    raise AttributeError(\"input list is empty\")\n"
                + "AttributeError: input list is empty\n";
        assertThat(SandboxServiceImpl.isSandboxForkFailure(pythonTraceback)).isFalse();
    }

    @Test
    @DisplayName("Compilation error output is NOT classified as sandbox fork failure")
    void isSandboxForkFailure_compileError_notDetected() {
        String compileError = "solution.cpp:5:1: error: expected ';' before 'return'\n"
                + "    return 0;\n"
                + "    ^~~~~~\n";
        assertThat(SandboxServiceImpl.isSandboxForkFailure(compileError)).isFalse();
    }

    @Test
    @DisplayName("null / empty output is NOT classified as sandbox fork failure")
    void isSandboxForkFailure_nullOrEmpty_notDetected() {
        assertThat(SandboxServiceImpl.isSandboxForkFailure(null)).isFalse();
        assertThat(SandboxServiceImpl.isSandboxForkFailure("")).isFalse();
        assertThat(SandboxServiceImpl.isSandboxForkFailure("   \n  ")).isFalse();
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
        assertThat(SandboxServiceImpl.isDockerDaemonForkFailure(msg)).isTrue();
    }

    @Test
    @DisplayName("docker daemon configuration warning mentioning 'pids' is NOT classified as fork failure")
    void isDockerDaemonForkFailure_configurationWarning_notDetected() {
        // Pre-fix would have incorrectly flagged this as fork failure (H1 root cause)
        String warning = "WARNING: pids-limit not set, using docker default (no enforcement)";
        assertThat(SandboxServiceImpl.isDockerDaemonForkFailure(warning)).isFalse();
    }

    @Test
    @DisplayName("docker daemon cgroup controller warning is NOT classified as fork failure")
    void isDockerDaemonForkFailure_cgroupControllerWarning_notDetected() {
        String warning = "pids controller disabled on cgroup v1 host; skipping pids.max enforcement";
        assertThat(SandboxServiceImpl.isDockerDaemonForkFailure(warning)).isFalse();
    }

    @Test
    @DisplayName("User code mentioning 'pids' in unrelated context is NOT classified as fork failure")
    void isDockerDaemonForkFailure_userCodeMentionPids_notDetected() {
        String userOutput = "Total spawned pids: 42 (debug instrumentation)\n";
        assertThat(SandboxServiceImpl.isDockerDaemonForkFailure(userOutput)).isFalse();
    }

    @Test
    @DisplayName("null / empty docker daemon message is NOT classified as fork failure")
    void isDockerDaemonForkFailure_nullOrEmpty_notDetected() {
        assertThat(SandboxServiceImpl.isDockerDaemonForkFailure(null)).isFalse();
        assertThat(SandboxServiceImpl.isDockerDaemonForkFailure("")).isFalse();
    }
}
