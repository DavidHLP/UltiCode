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
 * C++ language profile (ADR-002 §2.2).
 *
 * <p><b>Status (M2a):</b> stub. Mirrors {@link CLanguageProfile}
 * for now; once the C++ D-form harness ships, replace
 * {@link #dockerCommand} with a {@code g++ -O2 -std=c++17 ...}
 * invocation.
 *
 * <h2>Default wiring</h2>
 * Disabled by default via {@code @ConditionalOnProperty}; opt in
 * with {@code sandbox.profile.cpp.enabled=true} once the harness
 * ships.
 *
 * @see CLanguageProfile
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

    @Override
    public String languageId() {
        return "cpp";
    }

    @Override
    public List<String> dockerCommand(SandboxJob job, Path workspace) {
        // M2a stub: Phase 1 C++ harness doesn't read input.json.
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
        // g++ uses the same "<file>:<line>:<col>: error:" format as
        // gcc. Be conservative and match either the file marker or
        // the generic "error:" token.
        return stdout.contains("solution.cpp:") || stdout.contains("error:");
    }

    @Override
    public SandboxLimits effectiveLimits(SandboxJob job) {
        return new SandboxLimits(job.timeoutSeconds(), job.memoryMb());
    }
}
