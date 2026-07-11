package com.ulticode.modules.submission.sandbox;

import com.ulticode.modules.submission.enums.SubmissionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Classifies a D-form sandbox run's outcome into a typed
 * {@link SandboxInfraFailure} and maps that to a single
 * {@link SubmissionStatus}.
 *
 * <p>This is the single source of truth for exit-code / output-based
 * failure detection (ADR-002 §2.5). It deliberately sees the
 * <b>raw</b> stdout / stderr (including docker / OCI runtime lines
 * that {@link com.ulticode.modules.submission.service.CodeExecutionHelper#sanitizeSandboxOutput(String)}
 * strips for display). Keeping the failure oracle independent of the
 * display formatter fixes a long-standing regression where real Docker
 * / OCI infra failures were scrubbed and flattened to a generic
 * "Runtime Error" with memory 0.
 *
 * <h2>Activation</h2>
 * Active by default. The only consumer today is
 * {@link SandboxExecutorImpl}; downstream services should call
 * {@link #toStatus(SandboxInfraFailure)} rather than constructing
 * {@link SubmissionStatus} literals for infra cases.
 *
 * <h2>Side effects</h2>
 * {@link #classify(int, String, Throwable, boolean)} logs infra
 * failures at WARN with the raw evidence so previously-hidden docker
 * / OCI errors stop silently disappearing into a generic
 * "Runtime Error". The compile-failure path stays silent (it is the
 * common case, not infra).
 */
@Slf4j
@Component
public class SandboxOutcomeClassifier {

    /**
     * Typed infra-failure categories the executor / classifier can
     * distinguish from a non-zero exit code or an {@link Throwable}.
     *
     * <p>Set is intentionally tight: it matches the categories the
     * executor distinguished before this module existed (exit 137
     * cgroup OOM, fork / pids-limit pressure, OCI runtime errors,
     * docker daemon launch failures, user compile / runtime errors).
     * Adding a new category is a wire contract change — update
     * {@link #toStatus(SandboxInfraFailure)} and the existing tests
     * in lockstep.
     */
    public enum SandboxInfraFailure {
        /** Process exited 0 — happy path, no infra issue. */
        NONE,
        /** Docker SIGKILL (exit 137) almost always means cgroup OOM or hard timeout. */
        OUT_OF_MEMORY,
        /** Sandbox reported fork failure / pids-limit / RLIMIT_NPROC exhaustion. */
        FORK_LIMIT,
        /** stderr/stdout contained an "OCI runtime" line (docker / containerd infra failure). */
        OCI_ERROR,
        /** Docker daemon could not even spawn the container (IOException launching docker). */
        LAUNCH_FAILURE,
        /** User code failed to compile (per-language signal). */
        COMPILE_ERROR,
        /** Anything else non-zero without a more specific infra marker. */
        RUNTIME_ERROR
    }

    /**
     * Classify the raw outcome of one D-form docker invocation.
     *
     * @param exitCode the docker process exit code, or {@code -1} when the
     *                 process never produced one (e.g. {@link
     *                 SandboxExecutorImpl.DFormRunOutcome#cause()} was set)
     * @param stdout   raw harness / docker stdout; <b>not</b> passed
     *                 through {@code sanitizeSandboxOutput}, so docker /
     *                 OCI runtime lines are still visible here
     * @param cause    the {@link Throwable} captured when docker could not
     *                 even be launched; {@code null} when the process
     *                 exited normally (or was killed by us)
     * @param compileFailure whether the active {@link LanguageProfile}
     *                        reported a compile error in stdout
     * @return the typed failure category for this run
     */
    public SandboxInfraFailure classify(int exitCode, String stdout,
                                        Throwable cause, boolean compileFailure) {
        // 1) Docker daemon could not launch the container at all.
        //    We treat ANY non-null cause from the executor as LAUNCH_FAILURE
        //    (matches the pre-classifier "outcome.cause() != null" branch in
        //    SandboxExecutorImpl).
        if (cause != null) {
            return SandboxInfraFailure.LAUNCH_FAILURE;
        }

        // 2) Happy path.
        if (exitCode == 0) {
            return SandboxInfraFailure.NONE;
        }

        String s = stdout == null ? "" : stdout;

        // 3) docker SIGKILL (exit 137) without a harness envelope is
        //    almost always cgroup OOM / hard timeout — surfaced as
        //    SANDBOX_ERROR today; preserve that.
        if (exitCode == 137) {
            return SandboxInfraFailure.OUT_OF_MEMORY;
        }

        // 4) Sandbox-side fork / pids-limit / RLIMIT_NPROC exhaustion.
        if (containsAny(s,
                "Cannot fork",
                "Resource temporarily unavailable",
                "fork: Cannot allocate memory",
                "pids-limit reached",
                "cgroup pids limit",
                "RLIMIT_NPROC")) {
            return SandboxInfraFailure.FORK_LIMIT;
        }

        // 5) docker / containerd OCI runtime errors. NOTE: this checks
        //    the RAW output; the previous path was substring-checking
        //    output that had already been scrubbed by sanitizeSandboxOutput,
        //    which stripped any line containing "OCI runtime" — i.e. the
        //    oracle could never fire. Seeing the raw string here lets us
        //    actually classify these cases.
        if (s.contains("OCI runtime")) {
            return SandboxInfraFailure.OCI_ERROR;
        }

        // 6) User-code compile failure (per-language signal from the
        //    LanguageProfile).
        if (compileFailure) {
            return SandboxInfraFailure.COMPILE_ERROR;
        }

        // 7) Catch-all.
        return SandboxInfraFailure.RUNTIME_ERROR;
    }

    /**
     * Convenience overload used by the fork-failure static helpers
     * that survived from the pre-classifier code. Keeps them as a
     * single-line delegation to the classifier logic so the
     * substring table lives in one place.
     */
    public boolean looksLikeSandboxForkFailure(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }
        return containsAny(output,
                "Cannot fork",
                "Resource temporarily unavailable",
                "fork: Cannot allocate memory",
                "pids-limit reached",
                "cgroup pids limit",
                "RLIMIT_NPROC");
    }

    /**
     * Convenience overload for the docker-daemon-side fork signal
     * (IOException msg from {@link ProcessBuilder#start()}).
     */
    public boolean looksLikeDockerDaemonForkFailure(String msg) {
        return looksLikeSandboxForkFailure(msg);
    }

    /**
     * Map a typed {@link SandboxInfraFailure} to the {@link SubmissionStatus}
     * the executor should put on the per-case {@link RunCaseResult}.
     * This is the SINGLE mapping table for the executor — the
     * ~14 inline {@code SubmissionStatus.XXX} literals previously
     * scattered across {@link SandboxExecutorImpl} collapse to this
     * one switch.
     */
    public SubmissionStatus toStatus(SandboxInfraFailure failure) {
        if (failure == null) {
            return SubmissionStatus.RUNTIME_ERROR;
        }
        switch (failure) {
            case NONE:
                // Should never be passed in — happy path never reaches the
                // "rejected" helpers. Be defensive: fall back to RUNTIME_ERROR
                // rather than ACCEPTED, which would mis-score a broken run.
                return SubmissionStatus.RUNTIME_ERROR;
            case OUT_OF_MEMORY:
            case FORK_LIMIT:
            case OCI_ERROR:
            case LAUNCH_FAILURE:
                return SubmissionStatus.SANDBOX_ERROR;
            case COMPILE_ERROR:
                return SubmissionStatus.COMPILE_ERROR;
            case RUNTIME_ERROR:
            default:
                return SubmissionStatus.RUNTIME_ERROR;
        }
    }

    /**
     * The {@link SubmissionStatus} the executor assigns when a
     * docker run hit the whole-batch (or single-case) hard timeout.
     * Caller passes only the "this case timed out" fact; the
     * mapping is owned here.
     *
     * <p>Deliberately <b>not</b> a {@link SandboxInfraFailure}: TLE
     * is a per-case user-code outcome (the harness exited because
     * the user's solution ran too long), not an infrastructure
     * failure of the docker layer. Putting it in
     * {@link SandboxInfraFailure} would conflate two orthogonal
     * categories.
     */
    public SubmissionStatus timeLimitExceeded() {
        return SubmissionStatus.TIME_LIMIT_EXCEEDED;
    }

    /**
     * The {@link SubmissionStatus} the executor assigns to a non-zero
     * docker exit that was <b>not</b> an infrastructure failure and
     * <b>not</b> a compile failure — i.e. the user's solution
     * crashed at runtime. Caller passes only the "the harness
     * envelope isn't valid / wasn't produced and this isn't infra"
     * fact; the mapping is owned here.
     */
    public SubmissionStatus genericRuntimeError() {
        return SubmissionStatus.RUNTIME_ERROR;
    }

    /**
     * The {@link SubmissionStatus} the executor assigns when a
     * {@link com.ulticode.modules.submission.sandbox.UnsupportedLanguageException}
     * is thrown — i.e. the active {@link LanguageProfile} set does
     * not cover this submission's language. There is no harness
     * to run, so there is no verdict possible; surfacing as
     * {@link SubmissionStatus#SANDBOX_ERROR} matches the pre-M2a
     * behavior and {@code InMemorySandboxAdapterTest}.
     */
    public SubmissionStatus unsupportedLanguage() {
        return SubmissionStatus.SANDBOX_ERROR;
    }

    /**
     * Apply the backend backstop memory-limit-ceiling check
     * (ADR-002 §8 Layer B). When the harness self-reported a
     * verdict of {@link SubmissionStatus#ACCEPTED} or
     * {@link SubmissionStatus#WRONG_ANSWER} but the run's peak
     * memory exceeded the active {@link com.ulticode.modules.submission.sandbox.SandboxLimits#memoryMb()}
     * ceiling, reclassify to
     * {@link SubmissionStatus#MEMORY_LIMIT_EXCEEDED} so the user
     * sees Memory Limit Exceeded instead of a misleading
     * Accepted / WA. The check is skipped when no ceiling was
     * configured ({@code memoryLimitBytes <= 0}) and when the
     * peak fits within the ceiling.
     *
     * <p>Caller passes raw facts (the harness status, the
     * observed peak, the configured ceiling); the decision is
     * owned here so the executor never picks a verdict.
     *
     * @param harnessStatus    the status the harness envelope
     *                         decoded for this case
     * @param peakMemoryBytes  the harness-reported peak memory
     *                         (bytes) for this case
     * @param memoryLimitBytes the active per-case memory ceiling
     *                         (bytes); {@code <= 0} disables the
     *                         check
     * @return {@code MEMORY_LIMIT_EXCEEDED} when the ceiling is
     *         configured and the peak exceeds it AND the harness
     *         verdict is one of the two pass-through statuses;
     *         otherwise the original {@code harnessStatus}
     */
    public SubmissionStatus applyMemoryCeiling(SubmissionStatus harnessStatus,
                                                long peakMemoryBytes,
                                                long memoryLimitBytes) {
        if (memoryLimitBytes <= 0 || peakMemoryBytes <= memoryLimitBytes) {
            return harnessStatus;
        }
        if (harnessStatus == SubmissionStatus.ACCEPTED
                || harnessStatus == SubmissionStatus.WRONG_ANSWER) {
            return SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
        }
        return harnessStatus;
    }

    /**
     * Log an infra failure at WARN with the raw evidence. Replaces the
     * silent generic-"Runtime Error" regression: docker / OCI errors
     * used to vanish into a string the helper had already scrubbed.
     *
     * <p>Call this <b>once</b> per batch when the failure was detected,
     * not once per case — the executor fans the same per-batch failure
     * across N {@link RunCaseResult}s.
     */
    public void logInfraFailure(String runId, SandboxInfraFailure failure,
                                int exitCode, String stdout, Throwable cause) {
        String raw = stdout == null ? "" : stdout;
        String truncated = raw.length() <= 4096 ? raw : raw.substring(0, 4096) + "... [truncated]";
        String causeMsg = cause == null
                ? ""
                : (" cause=" + cause.getClass().getSimpleName()
                        + ":" + (cause.getMessage() == null ? "(no message)" : cause.getMessage()));
        log.warn("Sandbox infra failure for runId={}: failure={} exitCode={}{} evidence={}",
                runId, failure.name(), exitCode, causeMsg, truncated);
        if (log.isDebugEnabled()) {
            log.debug("Sandbox infra failure (lowercased evidence marker search) for runId={}: {}",
                    runId, truncateForLog(lowercaseMarkerSearch(truncated)));
        }
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String truncateForLog(String s) {
        return s == null ? "<null>" : s;
    }

    private static String lowercaseMarkerSearch(String raw) {
        // Tiny operator-friendly helper for the debug log: list which of
        // our known markers matched the raw output.
        String lc = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        appendIfMatch(sb, lc, "cannot fork");
        appendIfMatch(sb, lc, "resource temporarily unavailable");
        appendIfMatch(sb, lc, "fork: cannot allocate memory");
        appendIfMatch(sb, lc, "pids-limit reached");
        appendIfMatch(sb, lc, "cgroup pids limit");
        appendIfMatch(sb, lc, "rlimit_nproc");
        appendIfMatch(sb, lc, "oci runtime");
        return sb.length() == 0 ? "no markers" : sb.toString();
    }

    private static void appendIfMatch(StringBuilder sb, String lc, String marker) {
        if (lc.contains(marker)) {
            if (sb.length() > 0) sb.append(',');
            sb.append(marker);
        }
    }
}