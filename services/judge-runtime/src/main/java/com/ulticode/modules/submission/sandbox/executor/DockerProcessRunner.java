package com.ulticode.modules.submission.sandbox.executor;

import com.ulticode.modules.submission.sandbox.SandboxOutcomeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final Logger LOG = LoggerFactory.getLogger(DockerProcessRunner.class);
    private static final String CIDFILE_OPTION = "--cidfile";

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
            Path cidFile = findCidFile(command);
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
            boolean finished;
            try {
                finished = process.waitFor(hardTimeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                cleanupAfterInterrupt(process, reader, command, cidFile);
                throw interrupted;
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (!finished) {
                process.destroyForcibly();
                // Join the stdout drainer on the timeout path too so the
                // dform-stdout thread and its buffer do not leak after a
                // hard timeout (07-java-design: a dedicated pipe thread must
                // be joined or shut down, including the failure/timeout path).
                try {
                    reader.join(TimeUnit.SECONDS.toMillis(Math.min(2, hardTimeoutSeconds)));
                } catch (InterruptedException interrupted) {
                    cleanupAfterInterrupt(process, reader, command, cidFile);
                    throw interrupted;
                }
                cleanupTimedOutContainer(command, cidFile);
                return DFormRunOutcome.timedOut(elapsedMs);
            }
            // Give the reader a brief grace window for last bytes.
            try {
                reader.join(TimeUnit.SECONDS.toMillis(Math.min(2, hardTimeoutSeconds)));
            } catch (InterruptedException interrupted) {
                cleanupAfterInterrupt(process, reader, command, cidFile);
                throw interrupted;
            }
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
    private void cleanupAfterInterrupt(
            Process process, Thread reader, List<String> command, Path cidFile) {
        process.destroyForcibly();
        try {
            reader.join(TimeUnit.SECONDS.toMillis(2));
        } catch (InterruptedException ignored) {
            reader.interrupt();
        }
        try {
            cleanupTimedOutContainer(command, cidFile);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        Thread.currentThread().interrupt();
    }

    private static Path findCidFile(List<String> command) {
        for (int i = 0; i + 1 < command.size(); i++) {
            if (CIDFILE_OPTION.equals(command.get(i))) {
                return Path.of(command.get(i + 1));
            }
        }
        return null;
    }

    private void cleanupTimedOutContainer(List<String> command, Path cidFile)
            throws InterruptedException {
        String containerRef = readContainerId(cidFile);
        if (containerRef.isBlank()) {
            containerRef = findContainerName(command);
        }
        if (containerRef.isBlank()) {
            LOG.warn("Docker sandbox timed out without a container reference");
            return;
        }

        Process cleanup;
        try {
            ProcessBuilder cleanupBuilder = new ProcessBuilder(
                    command.get(0), "rm", "-f", containerRef);
            cleanupBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            cleanupBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
            cleanup = cleanupBuilder.start();
        } catch (IOException error) {
            LOG.warn("Docker sandbox cleanup could not start for {}", containerRef, error);
            return;
        }
        try {
            if (!cleanup.waitFor(2, TimeUnit.SECONDS)) {
                cleanup.destroyForcibly();
                cleanup.waitFor(1, TimeUnit.SECONDS);
                LOG.warn("Docker sandbox cleanup timed out for {}", containerRef);
            } else if (cleanup.exitValue() != 0) {
                LOG.warn("Docker sandbox cleanup failed for {} with exit code {}",
                        containerRef, cleanup.exitValue());
            }
        } catch (InterruptedException interrupted) {
            cleanup.destroyForcibly();
            try {
                cleanup.waitFor(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            throw interrupted;
        }
    }

    private static String readContainerId(Path cidFile) throws InterruptedException {
        if (cidFile == null) {
            return "";
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                if (Files.isRegularFile(cidFile)) {
                    String containerId = Files.readString(cidFile, StandardCharsets.UTF_8).trim();
                    if (!containerId.isBlank()) {
                        return containerId;
                    }
                }
            } catch (IOException ignored) {
                // Retry briefly because the docker CLI writes the cidfile asynchronously.
            }
            if (attempt < 4) {
                Thread.sleep(100);
            }
        }
        return "";
    }

    private static String findContainerName(List<String> command) {
        for (int i = 0; i + 1 < command.size(); i++) {
            if ("--name".equals(command.get(i))) {
                return command.get(i + 1);
            }
        }
        return "";
    }
}
