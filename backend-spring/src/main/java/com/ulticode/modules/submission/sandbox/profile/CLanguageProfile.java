package com.ulticode.modules.submission.sandbox.profile;

import com.ulticode.modules.submission.sandbox.LanguageProfile;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.SandboxLimits;
import com.ulticode.modules.submission.sandbox.UnsupportedLanguageException;
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
 * C language profile (ADR-002 §2.2).
 *
 * <p><b>Status (M2a):</b> stub. The D-form harness for C is in
 * Phase 1 smoke skeleton and does not yet read {@code input.json}
 * (see {@code CodeExecutionHelper.DFORM_SUPPORTED_LANGUAGES}).
 * Only the structural interface implementation lives here.
 *
 * <h2>Default wiring</h2>
 * Disabled by default via {@code @ConditionalOnProperty}; opt in
 * with {@code sandbox.profile.c.enabled=true} once the harness
 * ships.
 *
 * <h2>When the harness ships</h2>
 * Replace {@link #dockerCommand} with
 * {@code "gcc -O2 -std=c11 solution.c -o /tmp/a.out && /tmp/a.out /job/input.json"};
 * the workspace file name and compile-failure heuristic
 * ({@code "solution.c:"} / {@code "error:"} substrings) are
 * already correct.
 */
@Component
@ConditionalOnProperty(name = "sandbox.profile.c.enabled",
                       havingValue = "true",
                       matchIfMissing = false)
public class CLanguageProfile implements LanguageProfile {

    private static final String SOLUTION_FILE_NAME = "solution.c";
    private static final Set<PosixFilePermission> READ_ONLY =
            EnumSet.of(PosixFilePermission.OWNER_READ,
                       PosixFilePermission.GROUP_READ,
                       PosixFilePermission.OTHERS_READ);

    @Override
    public String languageId() {
        return "c";
    }

    @Override
    public List<String> dockerCommand(SandboxJob job, Path workspace) {
        // M2a stub: Phase 1 C harness doesn't read input.json.
        throw new UnsupportedLanguageException(languageId());
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
        // gcc prefixes errors with "<file>:<line>:<col>: error:" or
        // "error:" on continuation lines. Be conservative — match
        // both.
        return stdout.contains("solution.c:") || stdout.contains("error:");
    }

    @Override
    public SandboxLimits effectiveLimits(SandboxJob job) {
        return new SandboxLimits(job.timeoutSeconds(), job.memoryMb());
    }
}
