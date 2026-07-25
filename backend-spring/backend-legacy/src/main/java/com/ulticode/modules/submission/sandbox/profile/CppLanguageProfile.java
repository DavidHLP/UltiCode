package com.ulticode.modules.submission.sandbox.profile;

import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.sandbox.LanguageProfile;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.SandboxLimits;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * C++ language profile (ADR-002 §2.2).
 *
 * <p>Dispatches to the pre-built {@code cpp-sandbox} orchestrator inside the
 * sandbox image ({@code /opt/harness/cpp/cpp-sandbox}). The orchestrator reads
 * {@code /job/input.json} + {@code /job/solution.cpp}, statically extracts the
 * Solution method name, generates a typed runner, g++-compiles it, then runs
 * each case in an isolated child process — see
 * {@code docker/sandbox/harness/cpp/}. C++ has no runtime reflection, so the
 * harness parses the method name from the user source and bakes it into the
 * generated runner (method-name + {@code inputs[].type} driven codegen).
 *
 * <h2>Wiring</h2>
 * Disabled by default via {@code @ConditionalOnProperty}; opt in with
 * {@code sandbox.profile.cpp.enabled=true} (also requires the harness image to
 * be (re)built so {@code /opt/harness/cpp/cpp-sandbox} + the harness sources
 * are present).
 */
@Component
@ConditionalOnProperty(name = "sandbox.profile.cpp.enabled",
                       havingValue = "true",
                       matchIfMissing = false)
public class CppLanguageProfile implements LanguageProfile {

    private static final String SOLUTION_FILE_NAME = "solution.cpp";
    private static final Set<PosixFilePermission> READ_ONLY =
            EnumSet.of(PosixFilePermission.OWNER_READ,
                       PosixFilePermission.GROUP_READ,
                       PosixFilePermission.OTHERS_READ);

    private final DockerSandboxConfig config;

    public CppLanguageProfile(DockerSandboxConfig config) {
        this.config = config;
    }

    @Override
    public String languageId() {
        return "cpp";
    }

    @Override
    public List<String> dockerCommand(SandboxJob job, Path workspace) {
        // cpp-sandbox is the in-image orchestrator: it reads /job/input.json +
        // /job/solution.cpp, generates + compiles a typed runner, runs each case
        // in an isolated child process, and emits the D-form envelope on stdout.
        // The executor prepends commonSecurityArgs() + --memory + --cpus +
        // --pids-limit + the /job volume mount before this list.
        String harnessRoot = config.dFormHarnessRoot();
        String cppRoot = harnessRoot + "/cpp";
        // ULTICODE_HARNESS_ROOT points the orchestrator at its own harness
        // sources (json.cpp/serializer.cpp/*.hpp) for the runtime runner
        // compile, so a custom code-execution.sandbox.d-form.harness-root
        // install works — not just the default /opt/harness.
        String dispatchShell = "ULTICODE_HARNESS_ROOT=" + cppRoot + " " + cppRoot
                + "/cpp-sandbox /job/input.json";
        return List.of(config.image(), "sh", "-c", dispatchShell);
    }

    @Override
    public Path materializeWorkspace(Path tempDir, String code) throws IOException {
        Path solution = tempDir.resolve(SOLUTION_FILE_NAME);
        Files.writeString(solution, code == null ? "" : code, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(solution, READ_ONLY);
        return tempDir;
    }

    @Override
    public boolean isCompileFailure(String stdout) {
        if (stdout == null || stdout.isEmpty()) {
            return false;
        }
        // cpp-sandbox emits its own Compile Error envelope per case, so this is a
        // defensive fallback for any future variant that surfaces raw g++ output.
        // g++ uses the "<file>:<line>:<col>: error:" format; match either the
        // solution-file marker or the generic "error:" token.
        return stdout.contains("solution.cpp:") || stdout.contains("error:");
    }

    @Override
    public SandboxLimits effectiveLimits(SandboxJob job) {
        return new SandboxLimits(job.timeoutSeconds(), job.memoryMb());
    }
}
