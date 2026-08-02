package com.ulticode.modules.submission.sandbox.executor;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;

/**
 * Outcome of one D-form docker invocation produced by a
 * {@link ProcessLifecycleRunner}.
 *
 * <p>Carries everything the sandbox executor needs to classify a run
 * without re-touching the process: whether the hard timeout fired, the
 * elapsed wall-clock, the captured (and budget-capped) stdout, the
 * process exit code, and the launch {@link Throwable} when docker could
 * not even be spawned. Extracted from {@code SandboxExecutorImpl} so the
 * runner seam can return a typed value instead of leaking process
 * internals back into the domain executor.
 *
 * <p>The {@code cause} field is the single signal the
 * {@link com.ulticode.modules.submission.sandbox.SandboxOutcomeClassifier}
 * uses to distinguish {@code LAUNCH_FAILURE} (docker never started) from
 * a normally-exited non-zero process.
 */
public record DFormRunOutcome(boolean timedOut, long elapsedMs,
                              String stdout, int exitCode, Throwable cause) {

    /** Process exited on its own (zero or non-zero). {@code cause} is null. */
    static DFormRunOutcome finished(long elapsedMs, String stdout, int exitCode) {
        return new DFormRunOutcome(false, elapsedMs, stdout, exitCode, null);
    }

    /** Hard timeout fired; the runner already force-destroyed the process. */
    static DFormRunOutcome timedOut(long elapsedMs) {
        return new DFormRunOutcome(true, elapsedMs, "", -1, null);
    }

    /** docker could not be launched at all (IOException surfaced as a cause). */
    static DFormRunOutcome error(Throwable e) {
        return new DFormRunOutcome(false, 0L,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                -1, e);
    }

    /** The waiting thread was interrupted before docker produced a result. */
    static DFormRunOutcome interrupted() {
        return new DFormRunOutcome(false, 0L, "", -1,
                new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "interrupted"));
    }
}
