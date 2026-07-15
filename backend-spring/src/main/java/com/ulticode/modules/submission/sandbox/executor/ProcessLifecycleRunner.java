package com.ulticode.modules.submission.sandbox.executor;

import java.util.List;

/**
 * Spawn an external process and harvest its bounded stdout.
 *
 * <p>Seam extracted from {@code SandboxExecutorImpl} so the domain
 * executor depends on a narrow process-lifecycle abstraction instead of
 * {@link ProcessBuilder} plumbing (output-budget draining, hard-timeout
 * enforcement, fork-failure recasting). The lone production implementer
 * is {@link DockerProcessRunner}; tests can substitute an in-memory
 * runner to exercise the executor without spawning docker.
 */
interface ProcessLifecycleRunner {

    /**
     * Run {@code command}, draining stdout in a background thread up to a
     * fixed output budget, and enforce a hard wall-clock timeout.
     *
     * @param command           the argv to spawn (e.g. {@code docker run ...})
     * @param hardTimeoutSeconds forcibly destroy the process if it runs longer
     * @param runId             stable id used to name the drainer thread
     * @return the captured outcome (finished / timed out / launch error)
     * @throws InterruptedException if the calling thread is interrupted
     *                              while waiting for the process
     */
    DFormRunOutcome run(List<String> command, int hardTimeoutSeconds, String runId)
            throws InterruptedException;
}
