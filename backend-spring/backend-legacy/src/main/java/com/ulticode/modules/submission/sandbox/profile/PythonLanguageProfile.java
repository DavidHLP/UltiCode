package com.ulticode.modules.submission.sandbox.profile;

import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.sandbox.LanguageProfile;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.SandboxLimits;
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
 * Python language profile (ADR-002 §2.2).
 *
 * <p>Behavior migrated verbatim from the D-form dispatch path of the
 * pre-M2a {@code SandboxServiceImpl}:
 * <ul>
 *   <li>User code is written to {@code solution.py} (lowercase
 *       {@code s}; the harness does {@code import solution}, which
 *       is case-sensitive on Linux).</li>
 *   <li>Dispatch shell sets {@code SOLUTION_DIR=/job} then runs
 *       the pre-compiled harness {@code main.py}.</li>
 *   <li>Python has no separate compile step, so
 *       {@link #isCompileFailure} is a no-op — non-zero exit from
 *       the harness is always a runtime failure.</li>
 * </ul>
 */
@Component
public class PythonLanguageProfile implements LanguageProfile {

    private static final String SOLUTION_FILE_NAME = "solution.py";
    private static final Set<PosixFilePermission> READ_ONLY =
            EnumSet.of(PosixFilePermission.OWNER_READ,
                       PosixFilePermission.GROUP_READ,
                       PosixFilePermission.OTHERS_READ);

    private final DockerSandboxConfig config;

    public PythonLanguageProfile(DockerSandboxConfig config) {
        this.config = config;
    }

    @Override
    public String languageId() {
        return "python";
    }

    @Override
    public List<String> dockerCommand(SandboxJob job, Path workspace) {
        String harnessRoot = config.dFormHarnessRoot();
        String dispatchShell =
                "SOLUTION_DIR=/job python3 " + harnessRoot + "/python/main.py /job/input.json";
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
        // CPython doesn't have a separate compile step in the harness
        // path. Any non-zero exit is a runtime error.
        return false;
    }

    @Override
    public SandboxLimits effectiveLimits(SandboxJob job) {
        return new SandboxLimits(job.timeoutSeconds(), job.memoryMb());
    }
}
