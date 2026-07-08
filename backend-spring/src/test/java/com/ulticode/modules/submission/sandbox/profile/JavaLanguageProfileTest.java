package com.ulticode.modules.submission.sandbox.profile;

import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.SandboxLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java language profile (ADR-002 §2.2).
 *
 * <p>Verifies the three behaviours the strategy contract promises:
 * <ol>
 *   <li>Workspace materialization creates a {@code Solution.java}
 *       file with read-only mode bits.</li>
 *   <li>{@code dockerCommand} returns the right image and the
 *       D-form dispatch shell (compile to {@code /tmp/classes} +
 *       run {@code Main}).</li>
 *   <li>{@code isCompileFailure} recognizes javac's
 *       {@code <file>:<line>:} error markers.</li>
 * </ol>
 */
@DisplayName("JavaLanguageProfile (ADR-002 §2.2)")
class JavaLanguageProfileTest {

    private final DockerSandboxConfig config = new DockerSandboxConfig(
            true,
            "ulticode-sandbox:latest",
            "256m",
            "1.0",
            30,
            128,
            "/tmp/seccomp/profile.json",
            new DockerSandboxConfig.DForm(true, "/opt/harness"),
            Map.of()
    );
    private final JavaLanguageProfile profile = new JavaLanguageProfile(config);

    private static SandboxJob job(String code) {
        return new SandboxJob("run-1", "user-1", "sub-1", 0L,
                "java", code, 2, 256);
    }

    @Test
    @DisplayName("languageId is 'java'")
    void languageId_java() {
        assertThat(profile.languageId()).isEqualTo("java");
    }

    @Test
    @DisplayName("materializeWorkspace writes Solution.java with read-only mode bits")
    void materializeWorkspace_createsSolutionFile() throws IOException {
        Path tempDir = Files.createTempDirectory("java-profile-test-");
        try {
            Path workspace = profile.materializeWorkspace(tempDir,
                    "class Solution { int answer() { return 42; } }");
            assertThat(workspace).isEqualTo(tempDir);
            Path solution = tempDir.resolve("Solution.java");
            assertThat(Files.exists(solution)).isTrue();
            assertThat(Files.readString(solution))
                    .contains("class Solution")
                    .contains("return 42");
            // Read-only (0444): only OWNER_READ / GROUP_READ / OTHERS_READ.
            assertThat(Files.getPosixFilePermissions(solution))
                    .containsExactlyInAnyOrder(
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                            java.nio.file.attribute.PosixFilePermission.OTHERS_READ);
        } finally {
            // best-effort cleanup
            try (var walk = Files.walk(tempDir)) {
                walk.sorted((a, b) -> b.toString().length() - a.toString().length())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
    }

    @Test
    @DisplayName("dockerCommand returns the configured image and the D-form dispatch shell")
    void dockerCommand_includesImageAndDispatch() {
        List<String> cmd = profile.dockerCommand(job("class Solution {}"), Path.of("/tmp/job"));
        // image + entry: sh -c <compile + run>
        assertThat(cmd).hasSize(4);
        assertThat(cmd.get(0)).isEqualTo("ulticode-sandbox:latest");
        assertThat(cmd.get(1)).isEqualTo("sh");
        assertThat(cmd.get(2)).isEqualTo("-c");
        // The dispatch shell uses the absolute /job/Solution.java path so javac
        // finds the source regardless of the image WORKDIR. Matches the M3 fix.
        assertThat(cmd.get(3))
                .contains("mkdir -p /tmp/classes")
                .contains("javac -cp /opt/harness/java -d /tmp/classes /job/Solution.java")
                .contains("java -Djava.security.manager=allow -cp /opt/harness/java:/tmp/classes Main /job/input.json");
    }

    @Test
    @DisplayName("isCompileFailure recognizes javac error markers for Solution.java and Main.java")
    void isCompileFailure_javacMarkersDetected() {
        assertThat(profile.isCompileFailure(
                "Solution.java:7: error: cannot find symbol\n  return newX;\n  ^")).isTrue();
        assertThat(profile.isCompileFailure(
                "Main.java:3: error: incompatible types")).isTrue();
        // Non-compile noise (runtime traceback) must NOT match.
        assertThat(profile.isCompileFailure(
                "Exception in thread \"main\" java.lang.RuntimeException: boom")).isFalse();
        assertThat(profile.isCompileFailure(null)).isFalse();
        assertThat(profile.isCompileFailure("")).isFalse();
    }

    @Test
    @DisplayName("effectiveLimits clamps to job's memoryMb (executor is the upper bound)")
    void effectiveLimits_clampsToJob() {
        SandboxLimits limits = profile.effectiveLimits(job(""));
        assertThat(limits.timeoutSeconds()).isEqualTo(2);
        assertThat(limits.memoryMb()).isEqualTo(256);
    }
}
