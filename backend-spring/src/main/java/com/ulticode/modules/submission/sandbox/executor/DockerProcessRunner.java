package com.ulticode.modules.submission.sandbox.executor;

import com.ulticode.modules.submission.sandbox.SandboxOutcomeClassifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production {@link ProcessLifecycleRunner} that spawns {@code docker}
 * via {@link ProcessBuilder} and drains its stdout in a background thread.
 *
 * <p>Verbatim relocation of the pre-seam
 * {@code SandboxExecutorImpl.runDProcess} (Phase 3.5 #3 fix: drain the
 * pipe concurrently to avoid the 64 KiB Linux pipe-buffer deadlock) plus
 * the output-budget cap. The only collaborator is the
 * {@link SandboxOutcomeClassifier}, consulted to recast docker-daemon
 * fork-failure {@link IOException}s as a synthetic exit-137 so the
 * caller's classification path treats them as infra failures rather than
 * launch errors.
 */
@Component
class DockerProcessRunner implements ProcessLifecycleRunner {

    // 8 MiB envelope + 128 KiB per-case headroom, matches the pre-M2a
    // DFORM_OUTPUT_BUDGET_BYTES (Phase 3.5 #3 fix).
    private static final int DFORM_OUTPUT_BUDGET_BYTES = 8 * 1024 * 1024 + 128 * 1024;

    private final SandboxOutcomeClassifier outcomeClassifier;

    DockerProcessRunner(SandboxOutcomeClassifier outcomeClassifier) {
        this.outcomeClassifier = outcomeClassifier;
    }

    /**
     * Spawn the docker process with a concurrent stdout drainer.
     * Mirrors the pre-M2a runDProcess (Phase 3.5 #3 fix: drain the
     * pipe in a background thread to avoid the 64 KiB Linux pipe
     * buffer deadlock).
     */
    @Override
    public DFormRunOutcome run(List<String> command,
                                int hardTimeoutSeconds,
                                String runId)
            throws InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            AtomicBoolean overBudget = new AtomicBoolean(false);
            Thread reader = new Thread(() -> {
                try (InputStream in = process.getInputStream()) {
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) != -1) {
                        synchronized (buf) {
                            if (buf.size() + n > DFORM_OUTPUT_BUDGET_BYTES) {
                                overBudget.set(true);
                                return; // close InputStream to unblock harness
                            }
                            buf.write(chunk, 0, n);
                        }
                    }
                } catch (IOException ignored) {
                    /* process closed */
                }
            }, "dform-stdout-" + runId);
            reader.setDaemon(true);
            reader.start();

            long start = System.nanoTime();
            boolean finished = process.waitFor(hardTimeoutSeconds, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (!finished) {
                process.destroyForcibly();
                // Join the stdout drainer on the timeout path too so the
                // dform-stdout thread and its buffer do not leak after a
                // hard timeout (07-java-design: a dedicated pipe thread must
                // be joined or shut down, including the failure/timeout path).
                reader.join(TimeUnit.SECONDS.toMillis(Math.min(2, hardTimeoutSeconds)));
                return DFormRunOutcome.timedOut(elapsedMs);
            }
            // Give the reader a brief grace window for last bytes.
            reader.join(TimeUnit.SECONDS.toMillis(Math.min(2, hardTimeoutSeconds)));
            String stdout;
            synchronized (buf) {
                stdout = buf.toString(StandardCharsets.UTF_8);
                if (overBudget.get()) {
                    stdout = stdout + "\n[truncated: D-form output exceeded "
                            + DFORM_OUTPUT_BUDGET_BYTES + " bytes]";
                }
            }
            return DFormRunOutcome.finished(elapsedMs, stdout, process.exitValue());
        } catch (IOException e) {
            // Surface docker daemon-side fork failures as
            // SANDBOX_ERROR via the classifier's fork-detection helper.
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (outcomeClassifier.looksLikeDockerDaemonForkFailure(msg)) {
                return DFormRunOutcome.finished(0L,
                        "Cannot fork / pids-limit reached", 137);
            }
            return DFormRunOutcome.error(e);
        }
    }
}
