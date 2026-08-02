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
 * Java language profile (ADR-002 §2.2).
 *
 * <p>Behavior migrated verbatim from the D-form dispatch path of the
 * pre-M2a {@code SandboxServiceImpl}:
 * <ul>
 *   <li>User code is written to {@code Solution.java} (uppercase
 *       {@code S}; the harness imports it by that exact name).</li>
 *   <li>Dispatch shell compiles to {@code /tmp/classes} then runs
 *       {@code Main} with the harness jar on the classpath.</li>
 *   <li>{@code javac} error detection recognizes lines starting with
 *       {@code Solution.java:} or {@code Main.java:}, the same
 *       heuristic the legacy code used.</li>
 * </ul>
 */
@Component
public class JavaLanguageProfile implements LanguageProfile {

    private static final String SOLUTION_FILE_NAME = "Solution.java";
    private static final Set<PosixFilePermission> READ_ONLY =
            EnumSet.of(PosixFilePermission.OWNER_READ,
                       PosixFilePermission.GROUP_READ,
                       PosixFilePermission.OTHERS_READ);

    private final DockerSandboxConfig config;

    public JavaLanguageProfile(DockerSandboxConfig config) {
        this.config = config;
    }

    @Override
    public String languageId() {
        return "java";
    }

    @Override
    public List<String> dockerCommand(SandboxJob job, Path workspace) {
        // image + entry: sh -c <compile + run>. The executor
        // prepends commonSecurityArgs() + --memory + --cpus +
        // --pids-limit + --volume.
        // harnessRoot is fixed for the D-form image (see
        // DockerSandboxConfig.dFormHarnessRoot).
        String harnessRoot = config.dFormHarnessRoot();
        // Sandbox image WORKDIR is /home/sandbox, but the user's Solution.java
        // lives under the mounted /job volume. Use the absolute /job/ path
        // so javac finds the source regardless of cwd. (M3 fix: relative
        // path broke once the image WORKDIR drifted from the volume mount.)
        String dispatchShell =
                "mkdir -p /tmp/classes && javac -cp " + harnessRoot + "/java -d /tmp/classes "
                        + "/job/" + SOLUTION_FILE_NAME
                        + " && java -Djava.security.manager=allow -cp "
                        + harnessRoot + "/java:/tmp/classes Main /job/input.json";
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
        // javac prefixes errors with "<file>:<line>:" — same heuristic
        // as the pre-M2a isJavaCompileFailure().
        return stdout.contains("Solution.java:") || stdout.contains("Main.java:");
    }

    @Override
    public SandboxLimits effectiveLimits(SandboxJob job) {
        // JVM bytecode compile needs ~1.5x memory headroom; clamp to
        // job's memoryMb so the executor's upper bound still holds.
        return new SandboxLimits(job.timeoutSeconds(), job.memoryMb());
    }
}
