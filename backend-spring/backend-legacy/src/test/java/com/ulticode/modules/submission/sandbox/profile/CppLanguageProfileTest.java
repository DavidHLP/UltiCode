package com.ulticode.modules.submission.sandbox.profile;

import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C++ language profile (ADR-002 §2.2). The harness ships in
 * docker/sandbox/harness/cpp/; the profile dispatches to the in-image
 * cpp-sandbox orchestrator. These tests pin the dispatch shell shape and
 * the workspace/compile-failure heuristics without touching Docker.
 */
@DisplayName("CppLanguageProfile (ADR-002 §2.2)")
class CppLanguageProfileTest {

    private final DockerSandboxConfig config = mock(DockerSandboxConfig.class);
    private final CppLanguageProfile profile = new CppLanguageProfile(config);

    CppLanguageProfileTest() {
        when(config.image()).thenReturn("ulticode-sandbox:latest");
        when(config.dFormHarnessRoot()).thenReturn("/opt/harness");
    }

    @Test
    @DisplayName("languageId is 'cpp'")
    void languageId_cpp() {
        assertThat(profile.languageId()).isEqualTo("cpp");
    }

    @Test
    @DisplayName("dockerCommand dispatches to the cpp-sandbox orchestrator")
    void dockerCommand_dispatchesToOrchestrator() {
        List<String> cmd = profile.dockerCommand(
                new SandboxJob("r", "u", "s", 0L, "cpp", "class Solution {};", 2, 256),
                Path.of("/tmp/job"));
        // The executor prepends security args + the /job volume; the profile
        // returns [image, "sh", "-c", <dispatch shell>].
        assertThat(cmd).hasSize(4);
        assertThat(cmd.get(0)).isEqualTo("ulticode-sandbox:latest");
        assertThat(cmd.get(1)).isEqualTo("sh");
        assertThat(cmd.get(2)).isEqualTo("-c");
        assertThat(cmd.get(3)).contains("/opt/harness/cpp/cpp-sandbox")
                .contains("/job/input.json");
    }

    @Test
    @DisplayName("materializeWorkspace writes solution.cpp as read-only")
    void materializeWorkspace_writesReadOnlySolution() throws IOException {
        Path tmp = Files.createTempDirectory("cpp-profile-test");
        try {
            profile.materializeWorkspace(tmp, "class Solution {};");
            Path sol = tmp.resolve("solution.cpp");
            assertThat(Files.exists(sol)).isTrue();
            assertThat(Files.readString(sol)).isEqualTo("class Solution {};");
            // READ_ONLY perms (OWNER/GROUP/OTHERS_READ, no write bit).
            assertThat(Files.isWritable(sol)).isFalse();
        } finally {
            Files.deleteIfExists(tmp.resolve("solution.cpp"));
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("isCompileFailure recognizes g++ error markers")
    void isCompileFailure_markers() {
        assertThat(profile.isCompileFailure("solution.cpp:8:5: error: 'foo' was not declared")).isTrue();
        assertThat(profile.isCompileFailure("error: expected '}' at end of input")).isTrue();
        assertThat(profile.isCompileFailure("terminate called after throwing an instance of 'std::bad_alloc'")).isFalse();
        assertThat(profile.isCompileFailure(null)).isFalse();
    }
}
