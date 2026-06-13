package com.ulticode.modules.submission.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.service.CodeExecutionHelper;
import com.ulticode.modules.submission.service.SandboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of SandboxService.
 *
 * <p>Phase 5b: the legacy Form A (per-request bash wrapper) path is
 * gone. D-form (LeetCode/HackerRank harness) is the sole dispatch.
 * The harness inside the sandbox image is responsible for compiling
 * and running the user code; the backend's job is to:
 * <ol>
 *   <li>Materialize a per-run temp dir with the user code and the
 *       per-call {@code input.json}.
 *   <li>Spawn the docker container with the temp dir mounted
 *       read-only at {@code /job}.
 *   <li>Concurrently drain the container's stdout to avoid the
 *       pipe-buffer deadlock that Phase 3 surfaced.
 *   <li>Hand the captured envelope to {@link CodeExecutionHelper}
 *       for verdict mapping.
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxServiceImpl implements SandboxService {

    private final DockerSandboxConfig sandboxConfig;
    private final CodeExecutionHelper helper;

    /** Verdict returned to the client when the sandbox itself fails to fork (not user code). */
    static final String SANDBOX_FORK_FAILURE_VERDICT = "Sandbox Error";

    /** Detail prefix prepended to the original sandbox output for ops triage. */
    static final String SANDBOX_FORK_FAILURE_DETAIL_PREFIX =
            "Sandbox fork failed (likely PID/cgroup/seccomp pressure): ";

    /** Truncate log detail to this many bytes to keep structured log aggregators healthy. */
    private static final int MAX_LOG_DETAIL_BYTES = 1024;

    /**
     * Cap on bytes the backend will buffer from a single sandbox invocation.
     * 8 MiB envelope + 128 KiB safety headroom for a 100-case batch. If
     * the harness exceeds this, the reader drops the rest and
     * {@code runDProcess} appends a truncation marker so envelope
     * parsing doesn't blow up downstream.
     */
    private static final int DFORM_OUTPUT_BUDGET_BYTES = 8 * 1024 * 1024 + 128 * 1024;

    /**
     * Detects sandbox-level fork failures that are NOT caused by user code.
     * Symptoms of host/cgroup/seccomp pressure rather than user program bugs:
     * busybox sh, kernel cgroup, docker daemon, or libc fwrite all surface here.
     */
    static boolean isSandboxForkFailure(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }
        return output.contains("Cannot fork")
                || output.contains("Resource temporarily unavailable")
                || output.contains("fork: Cannot allocate memory");
    }

    /**
     * Detects docker daemon-side fork failures surfaced as {@link IOException#getMessage()}.
     */
    static boolean isDockerDaemonForkFailure(String msg) {
        if (msg == null || msg.isEmpty()) {
            return false;
        }
        return msg.contains("Cannot fork")
                || msg.contains("fork: Cannot allocate memory")
                || msg.contains("pids-limit reached")
                || msg.contains("cgroup pids limit")
                || msg.contains("RLIMIT_NPROC");
    }

    private static String truncateForLog(String detail) {
        if (detail == null) {
            return "<null>";
        }
        if (detail.length() <= MAX_LOG_DETAIL_BYTES) {
            return detail;
        }
        return detail.substring(0, MAX_LOG_DETAIL_BYTES)
                + "... [truncated, original=" + detail.length() + " bytes]";
    }

    // ── Public entry points ──────────────────────────────────────────────────

    @Override
    public RunResultDTO.RunCaseResult executeInSandbox(String language, String code,
                                                      RunSubmissionDTO.RunTestCase testCase,
                                                      String runId, String userId) {
        Path jobDir = null;
        try {
            String inputJson = helper.buildDInputsJson(testCase, dFormPerCaseTimeoutMs());
            jobDir = materializeDFormJob(language, code, inputJson, runId);
            List<String> command = buildDDockerCommand(language, jobDir);
            DFormRunResult run = runDProcess(command, dFormHardTimeoutSeconds(), runId);
            long elapsedMs = run.elapsedMs();
            String stdout = run.stdout();
            int exitCode = run.exitCode();
            if (run.timedOut()) {
                return helper.buildCaseResult(testCase, runId, userId, "Time Limit Exceeded",
                        elapsedMs, null, "D-form dispatch timed out after " + dFormHardTimeoutSeconds() + "s", 0.0);
            }
            if (exitCode != 0) {
                if (isSandboxForkFailure(stdout)) {
                    log.warn("D-form sandbox fork failure for runId={}: {}", runId,
                            truncateForLog(helper.sanitizeSandboxOutput(stdout)));
                    return helper.buildCaseResult(testCase, runId, userId, SANDBOX_FORK_FAILURE_VERDICT,
                            elapsedMs, null,
                            SANDBOX_FORK_FAILURE_DETAIL_PREFIX + helper.sanitizeSandboxOutput(stdout), 0.0);
                }
                return helper.buildCaseResult(testCase, runId, userId, "Runtime Error",
                        elapsedMs, null, helper.sanitizeSandboxOutput(stdout), 0.0);
            }
            List<RunResultDTO.RunCaseResult> results =
                    helper.parseDEnvelope(stdout, List.of(testCase), runId, userId);
            return results.isEmpty()
                    ? helper.buildCaseResult(testCase, runId, userId, "Runtime Error",
                            elapsedMs, null, "D-form envelope empty", 0.0)
                    : results.get(0);
        } finally {
            cleanupJobDir(jobDir);
        }
    }

    @Override
    public List<RunResultDTO.RunCaseResult> executeBatch(String language, String code,
                                                        List<RunSubmissionDTO.RunTestCase> testCases,
                                                        String runId, String userId) {
        Path jobDir = null;
        try {
            String inputJson = helper.buildDBatchInputsJson(testCases, dFormPerCaseTimeoutMs());
            jobDir = materializeDFormJob(language, code, inputJson, runId);
            List<String> command = buildDDockerCommand(language, jobDir);
            DFormRunResult run = runDProcess(command, dFormHardTimeoutSeconds(), runId);
            long elapsedMs = run.elapsedMs();
            String stdout = run.stdout();
            int exitCode = run.exitCode();
            if (run.timedOut()) {
                long perCase = elapsedMs / Math.max(testCases.size(), 1);
                return testCases.stream()
                        .map(tc -> helper.buildCaseResult(tc, runId, userId, "Time Limit Exceeded",
                                perCase, null,
                                "D-form batch dispatch timed out after " + dFormHardTimeoutSeconds() + "s", 0.0))
                        .toList();
            }
            if (exitCode != 0) {
                if (isSandboxForkFailure(stdout)) {
                    long perCase = elapsedMs / Math.max(testCases.size(), 1);
                    return testCases.stream()
                            .map(tc -> helper.buildCaseResult(tc, runId, userId, SANDBOX_FORK_FAILURE_VERDICT,
                                    perCase, null,
                                    SANDBOX_FORK_FAILURE_DETAIL_PREFIX + helper.sanitizeSandboxOutput(stdout), 0.0))
                            .toList();
                }
                long perCase = elapsedMs / Math.max(testCases.size(), 1);
                return testCases.stream()
                        .map(tc -> helper.buildCaseResult(tc, runId, userId, "Runtime Error",
                                perCase, null, helper.sanitizeSandboxOutput(stdout), 0.0))
                        .toList();
            }
            return helper.parseDEnvelope(stdout, testCases, runId, userId);
        } finally {
            cleanupJobDir(jobDir);
        }
    }

    // ── D-form dispatch internals ────────────────────────────────────────────

    /** Maps a language to the file name the harness expects in /job/. */
    private static String dFormSolutionFileName(String language) {
        return switch (language) {
            case "java" -> "Solution.java";
            case "python" -> "solution.py";   // lowercase: harness does `import solution`
            default -> throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED);
        };
    }

    /** Writes user code + input.json to a per-run temp dir, returns the dir path. */
    private Path materializeDFormJob(String language, String code, String inputJson, String runId) {
        Path jobDir;
        try {
            jobDir = Files.createTempDirectory("ulticode-sandbox-" + runId + "-");
            Files.writeString(jobDir.resolve(dFormSolutionFileName(language)),
                    code == null ? "" : code, StandardCharsets.UTF_8);
            Files.writeString(jobDir.resolve("input.json"),
                    inputJson == null ? "{}" : inputJson, StandardCharsets.UTF_8);
            // :ro mount requires the *contents* to be read-only, not the dir.
            // chmod 0444 on each file makes the container-side reads safe even if
            // a buggy harness attempted to write back.
            Files.setPosixFilePermissions(jobDir.resolve(dFormSolutionFileName(language)),
                    java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                       java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                                       java.nio.file.attribute.PosixFilePermission.OTHERS_READ));
            Files.setPosixFilePermissions(jobDir.resolve("input.json"),
                    java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                       java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                                       java.nio.file.attribute.PosixFilePermission.OTHERS_READ));
            return jobDir;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SANDBOX_ERROR,
                    "Failed to materialize D-form job for runId=" + runId + ": " + e.getMessage());
        }
    }

    /** Per-case soft timeout forwarded to the harness (Thread.interrupt inside the worker). */
    private long dFormPerCaseTimeoutMs() {
        // Conservative default: 1s. Tunable via a future
        // code-execution.sandbox.d-form config field if/when needed.
        return 1_000L;
    }

    /**
     * Hard timeout the docker container's wall clock. Per-language
     * budgets win when set; otherwise fall back to the Java budget
     * (typically the longest) or the global default.
     */
    private int dFormHardTimeoutSeconds() {
        DockerSandboxConfig.LanguageLimit langLimit = sandboxConfig.languages() != null
                ? sandboxConfig.languages().get("dform")
                : null;
        if (langLimit != null) return langLimit.timeoutSeconds();
        DockerSandboxConfig.LanguageLimit javaLimit = sandboxConfig.languages() != null
                ? sandboxConfig.languages().get("java")
                : null;
        if (javaLimit != null) return javaLimit.timeoutSeconds();
        return sandboxConfig.timeout();
    }

    /**
     * Per-language {@code sh -c} body that compiles + runs the user code
     * via the pre-compiled harness.
     *
     * <p>CR fix (Phase 3.5 #1): Java previously used {@code javac -d .}
     * from inside the {@code :ro} mount of {@code /job}, which
     * deterministically failed at write time. Compilation now targets
     * a per-run tmpfs path that the docker command pre-creates as
     * writable, and the runtime classpath reads from that same path.
     */
    private String dFormDispatchShell(String language, String harnessRoot) {
        return switch (language) {
            case "java" -> "mkdir -p /tmp/classes && javac -cp " + harnessRoot + "/java -d /tmp/classes "
                    + dFormSolutionFileName(language)
                    + " && java -Djava.security.manager=allow -cp "
                    + harnessRoot + "/java:/tmp/classes Main /job/input.json";
            case "python" -> "SOLUTION_DIR=/job python3 "
                    + harnessRoot + "/python/main.py /job/input.json";
            default -> throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED);
        };
    }

    private List<String> buildDDockerCommand(String language, Path jobDir) {
        DockerSandboxConfig.LanguageLimit langLimit = sandboxConfig.languages() != null
                ? sandboxConfig.languages().get(language)
                : null;
        String effectiveMemory = langLimit != null ? langLimit.memory() : sandboxConfig.memory();
        String harnessRoot = sandboxConfig.dFormHarnessRoot();
        String hostJobDir = jobDir.toAbsolutePath().toString();
        return new ArrayList<>(List.of(
                "docker", "run", "--rm", "-i",
                "--network", "none",
                "--cap-drop", "ALL",
                "--memory", effectiveMemory,
                "--cpus", sandboxConfig.cpus(),
                "--pids-limit", String.valueOf(sandboxConfig.pidsLimit()),
                "--ulimit", "nofile=128:128",
                "--tmpfs", "/tmp:rw,exec,size=64m",
                "--read-only",
                "--user", "1000:1000",
                "--security-opt", "no-new-privileges:true",
                "--security-opt", "seccomp=" + resolveSeccompProfileFilePath(),
                "--volume", hostJobDir + ":/job:ro",
                "--volume", resolveSeccompProfileDirectoryPath() + ":/seccomp-profile:ro",
                sandboxConfig.image(),
                "sh", "-c", dFormDispatchShell(language, harnessRoot)
        ));
    }

    /**
     * Run a docker command with a concurrent stdout drainer.
     *
     * <p>CR fix (Phase 3.5 #3): {@link ProcessBuilder#start()} hands the
     * process a pipe that is only 64 KiB on Linux by default. If the
     * harness envelope (per-case verdict + 64 KiB user_stdout each) is
     * larger than that, the container blocks on {@code write()} and the
     * backend's {@code waitFor} blocks too — false TLE for otherwise
     * valid runs. Spawning a daemon thread that drains the pipe as the
     * process runs eliminates the deadlock.
     */
    private DFormRunResult runDProcess(List<String> command, int hardTimeoutSeconds, String runId) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            AtomicBoolean overBudget = new AtomicBoolean(false);
            Thread reader = new Thread(() -> {
                try (java.io.InputStream in = process.getInputStream()) {
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) != -1) {
                        synchronized (buf) {
                            if (buf.size() + n > DFORM_OUTPUT_BUDGET_BYTES) {
                                overBudget.set(true);
                                // drop further reads; close InputStream to unblock harness
                                return;
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

            long startTime = System.nanoTime();
            boolean finished = process.waitFor(hardTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                // Don't bother joining the reader; we're returning TLE.
                return new DFormRunResult(true, elapsedMs, "", -1);
            }
            // waitFor returned, but the harness may still be flushing the last bytes.
            // Give the reader a brief grace window to finish draining.
            reader.join(java.util.concurrent.TimeUnit.SECONDS.toMillis(Math.min(2, hardTimeoutSeconds)));
            String stdout;
            synchronized (buf) {
                stdout = buf.toString(StandardCharsets.UTF_8);
            }
            int exitCode;
            try {
                exitCode = process.exitValue();
            } catch (IllegalThreadStateException stillRunning) {
                // process didn't actually exit despite waitFor returning true — extremely rare
                exitCode = -1;
            }
            if (overBudget.get()) {
                stdout = stdout + "\n[truncated: harness output exceeded "
                        + DFORM_OUTPUT_BUDGET_BYTES + " bytes]";
            }
            return new DFormRunResult(false, elapsedMs, stdout, exitCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "D-form dispatch interrupted");
        } catch (IOException e) {
            log.error("D-form I/O failed for runId={}", runId, e);
            String detail = e.getMessage();
            if (detail != null && detail.contains("Unable to find image")) {
                throw new BusinessException(ErrorCode.SANDBOX_IMAGE_NOT_FOUND,
                        "Sandbox image '" + sandboxConfig.image() + "' not found. Build it first: "
                                + "docker build -t " + sandboxConfig.image()
                                + " -f docker/sandbox/Dockerfile docker/sandbox/");
            }
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "D-form dispatch failed: " + detail);
        }
    }

    private void cleanupJobDir(Path jobDir) {
        if (jobDir == null) return;
        try {
            Files.walk(jobDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) { /* best-effort cleanup */ }
    }

    private String resolveSeccompProfileFilePath() {
        String path = sandboxConfig.seccompProfilePath();
        if (path != null && !path.isBlank()) {
            Path configuredPath = resolvePathFromWorkingTree(Path.of(path));
            if (Files.isDirectory(configuredPath)) {
                return configuredPath.resolve("seccomp-profile.json").toString();
            }
            return configuredPath.toString();
        }
        return resolvePathFromWorkingTree(Path.of("docker", "sandbox", "seccomp-profile.json")).toString();
    }

    private String resolveSeccompProfileDirectoryPath() {
        Path filePath = Path.of(resolveSeccompProfileFilePath());
        Path parent = filePath.getParent();
        return parent != null ? parent.toString() : filePath.toString();
    }

    private Path resolvePathFromWorkingTree(Path candidate) {
        if (candidate.isAbsolute()) {
            return candidate;
        }
        String cwd = System.getProperty("user.dir");
        Path cwdCandidate = Path.of(cwd).resolve(candidate).normalize();
        if (Files.exists(cwdCandidate)) {
            return cwdCandidate;
        }
        Path parent = Path.of(cwd).getParent();
        if (parent != null) {
            Path parentCandidate = parent.resolve(candidate).normalize();
            if (Files.exists(parentCandidate)) {
                return parentCandidate;
            }
        }
        return cwdCandidate;
    }

    /** Minimal record bundling the result of a {@link #runDProcess} call. */
    private record DFormRunResult(boolean timedOut, long elapsedMs, String stdout, int exitCode) {}
}
