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
 * JavaScript language profile (ADR-002 §2.2).
 *
 * <p><b>Status (M2a):</b> stub. The D-form harness for JavaScript is
 * not yet part of the migration (see
 * {@code CodeExecutionHelper.DFORM_SUPPORTED_LANGUAGES}); only the
 * structural interface implementation lives here.
 *
 * <h2>Default wiring</h2>
 * Disabled by default via {@code @ConditionalOnProperty}; opt in with
 * {@code sandbox.profile.javascript.enabled=true} once the harness
 * ships. When disabled the executor sees no profile for the
 * {@code "javascript"} id and surfaces
 * {@code SUBMISSION_LANGUAGE_UNSUPPORTED} to the caller (see
 * {@code SandboxExecutorImpl} profile lookup).
 *
 * <h2>When the harness ships</h2>
 * Replace {@link #dockerCommand} with the real Node.js invocation
 * that forwards to {@code /opt/harness/javascript/main.js /job/input.json};
 * everything else (workspace file name, compile-failure heuristic,
 * limits) is already correct.
 */
@Component
@ConditionalOnProperty(name = "sandbox.profile.javascript.enabled",
                       havingValue = "true",
                       matchIfMissing = false)
public class JavaScriptLanguageProfile implements LanguageProfile {

    private static final String SOLUTION_FILE_NAME = "solution.js";
    private static final Set<PosixFilePermission> READ_ONLY =
            EnumSet.of(PosixFilePermission.OWNER_READ,
                       PosixFilePermission.GROUP_READ,
                       PosixFilePermission.OTHERS_READ);

    @Override
    public String languageId() {
        return "javascript";
    }

    @Override
    public List<String> dockerCommand(SandboxJob job, Path workspace) {
        // M2a stub: the harness is not part of the D-form rollout
        // yet. Throw cleanly so the executor reports a structured
        // unsupported-language verdict instead of an opaque runtime
        // error from a non-existent entry script.
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
        // Node.js has no separate compile step in the harness path.
        return false;
    }

    @Override
    public SandboxLimits effectiveLimits(SandboxJob job) {
        return new SandboxLimits(job.timeoutSeconds(), job.memoryMb());
    }
}
