package com.ulticode.modules.submission.sandbox;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Strategy (ADR-002 §2.2) describing how to execute one programming
 * language in the sandbox.
 *
 * <p>One bean is registered per supported language. The executor
 * ({@code SandboxExecutorImpl}) looks up the profile by
 * {@link SandboxJob#languageId()} and delegates the per-language
 * concerns here:
 * <ul>
 *   <li>where the user code goes on disk inside the workspace;</li>
 *   <li>which docker image + harness entry point to use;</li>
 *   <li>how to tell a compile failure from a runtime error in stdout;</li>
 *   <li>whether the language wants tighter or looser limits than
 *       the per-run values in {@link SandboxJob}.</li>
 * </ul>
 *
 * <h2>What this interface deliberately does NOT cover</h2>
 * <ul>
 *   <li>Common security args (--network none / --cap-drop ALL /
 *       --read-only / --user 1000:1000 / seccomp / --ulimit nofile) —
 *       those are added by the executor in
 *       {@code SandboxExecutorImpl.commonSecurityArgs()}. A profile
 *       that adds its own divergent security args is a bug.</li>
 *   <li>Cross-language infrastructure failures (docker daemon fork
 *       pressure, pids-limit exhaustion) — those surface as
 *       {@code SubmissionStatus.SANDBOX_ERROR} inside the executor
 *       and never reach {@link #isCompileFailure(String)}.</li>
 * </ul>
 *
 * <h2>Adding a new language</h2>
 * <ol>
 *   <li>Add a new {@code @Component} implementing this interface.</li>
 *   <li>Pick a language id matching
 *       {@code CodeExecutionHelper.SUPPORTED_LANGUAGES}.</li>
 *   <li>Return a docker command that uses the pre-built harness inside
 *       {@code docker/sandbox/harness/{lang}/} of the sandbox image.</li>
 *   <li>If the D-form harness for the language is not yet ready,
 *       have {@link #dockerCommand} throw
 *       {@link UnsupportedLanguageException} after
 *       {@link #effectiveLimits} so the executor still reports the
 *       request as a clean unsupported-language verdict instead of a
 *       confusing runtime error.</li>
 * </ol>
 *
 * @see SandboxExecutor
 * @see UnsupportedLanguageException
 */
public interface LanguageProfile {

    /**
     * Stable, lower-case canonical id matching
     * {@code CodeExecutionHelper.SUPPORTED_LANGUAGES} (e.g.
     * {@code "java"}, {@code "python"}, {@code "javascript"},
     * {@code "c"}, {@code "cpp"}). Used as the key in the executor's
     * profile map. The executor throws
     * {@link UnsupportedLanguageException} when no profile is
     * registered for the job's language id.
     */
    String languageId();

    /**
     * Build the <b>language-specific</b> portion of the docker command
     * (image + entry point + workspace mount + dispatch shell).
     *
     * <p>The executor prepends {@code commonSecurityArgs()} and the
     * per-language resource limit args before this list, then calls
     * {@code docker run <commonSecurityArgs> <resourceLimits>
     * <returnedList...>}. The profile's returned list must therefore
     * start with the image name and end with the harness entry, not
     * include {@code docker} itself.
     *
     * <p>Allowed to throw {@link UnsupportedLanguageException} when
     * the D-form harness for this language is not yet implemented;
     * the executor translates that to a per-case
     * {@code SUBMISSION_LANGUAGE_UNSUPPORTED} verdict.
     *
     * @param job        the per-run job descriptor (carries the
     *                   resource limits, code, etc.)
     * @param workspace  the temp dir returned by
     *                   {@link #materializeWorkspace}; the profile
     *                   mounts it (typically as {@code /job:ro}).
     */
    List<String> dockerCommand(SandboxJob job, Path workspace);

    /**
     * Materialize the per-run workspace directory.
     *
     * <p>Typical responsibilities:
     * <ul>
     *   <li>Create a per-run subdirectory under {@code tempDir}.</li>
     *   <li>Write the user code to the file name the harness expects
     *       (e.g. {@code Solution.java} for Java,
     *       {@code solution.py} for Python).</li>
     *   <li>Set the file mode to read-only so a buggy harness cannot
     *       tamper with the user's source even if it tries.</li>
     * </ul>
     *
     * <p>{@code input.json} is written by the executor (it depends on
     * the test case, not on the language), so profiles do not need
     * to write it.
     *
     * @return the path the caller should mount into the container.
     *         The returned dir already exists; the caller is
     *         responsible for {@code rm -rf} on cleanup.
     */
    Path materializeWorkspace(Path tempDir, String code) throws IOException;

    /**
     * Tell a compile failure from a runtime failure by inspecting
     * the harness's stdout. Only consulted when the docker process
     * exited with a non-zero status (per the contract in
     * {@link SandboxExecutor}).
     *
     * <p>Implementations should be conservative: returning
     * {@code true} on a non-compile failure would mis-classify a
     * runtime bug as a compile error and confuse the user. When in
     * doubt, return {@code false} (let the executor fall back to
     * {@code RUNTIME_ERROR}).
     */
    boolean isCompileFailure(String stdout);

    /**
     * Return the per-language limits the executor should apply.
     * Defaults to the job's limits; profiles override only what the
     * language needs (e.g. JVM heap for Java, compile cache for C).
     *
     * <p>Must NOT exceed the per-run limits in {@code job}; the
     * executor is the last line of defense on resource bounds.
     */
    SandboxLimits effectiveLimits(SandboxJob job);
}
