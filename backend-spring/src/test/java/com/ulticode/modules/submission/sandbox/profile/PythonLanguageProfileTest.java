package com.ulticode.modules.submission.sandbox.profile;

import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.SandboxLimits;
import com.ulticode.modules.submission.sandbox.UnsupportedLanguageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Python language profile (ADR-002 §2.2). Mirrors
 * {@link JavaLanguageProfileTest} but covers the Python-specific
 * bits (lowercase file name, no compile step, harness invocation).
 */
@DisplayName("PythonLanguageProfile (ADR-002 §2.2)")
class PythonLanguageProfileTest {

    private final DockerSandboxConfig config = new DockerSandboxConfig(
            true, "ulticode-sandbox:latest", "256m", "1.0", 30, 128,
            "/tmp/seccomp/profile.json",
            new DockerSandboxConfig.DForm(true, "/opt/harness"),
            Map.of()
    );
    private final PythonLanguageProfile profile = new PythonLanguageProfile(config);

    private static SandboxJob job(String code) {
        return new SandboxJob("run-1", "user-1", "sub-1", 0L,
                "python", code, 2, 256);
    }

    @Test
    @DisplayName("languageId is 'python'")
    void languageId_python() {
        assertThat(profile.languageId()).isEqualTo("python");
    }

    @Test
    @DisplayName("materializeWorkspace writes solution.py with read-only mode bits")
    void materializeWorkspace_createsSolutionFile() throws IOException {
        Path tempDir = Files.createTempDirectory("py-profile-test-");
        try {
            profile.materializeWorkspace(tempDir, "def solution(): return 42");
            Path solution = tempDir.resolve("solution.py");
            assertThat(Files.exists(solution)).isTrue();
            assertThat(Files.readString(solution)).contains("def solution");
            assertThat(Files.getPosixFilePermissions(solution))
                    .containsExactlyInAnyOrder(
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                            java.nio.file.attribute.PosixFilePermission.OTHERS_READ);
        } finally {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted((a, b) -> b.toString().length() - a.toString().length())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
    }

    @Test
    @DisplayName("dockerCommand includes the harness invocation, no compile step")
    void dockerCommand_pythonDispatch() {
        List<String> cmd = profile.dockerCommand(job(""), Path.of("/tmp/job"));
        assertThat(cmd).hasSize(4);
        assertThat(cmd.get(0)).isEqualTo("ulticode-sandbox:latest");
        assertThat(cmd.get(3))
                .contains("SOLUTION_DIR=/job python3 /opt/harness/python/main.py /job/input.json")
                .doesNotContain("javac");  // no compile step
    }

    @Test
    @DisplayName("isCompileFailure is always false (no compile step in Python)")
    void isCompileFailure_alwaysFalse() {
        assertThat(profile.isCompileFailure("anything")).isFalse();
        assertThat(profile.isCompileFailure(null)).isFalse();
        assertThat(profile.isCompileFailure("SyntaxError: bad token")).isFalse();
    }

    @Test
    @DisplayName("effectiveLimits delegates to job")
    void effectiveLimits_delegatesToJob() {
        SandboxLimits limits = profile.effectiveLimits(job(""));
        assertThat(limits.timeoutSeconds()).isEqualTo(2);
        assertThat(limits.memoryMb()).isEqualTo(256);
    }
}
