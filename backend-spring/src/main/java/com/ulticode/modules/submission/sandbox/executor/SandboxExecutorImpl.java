package com.ulticode.modules.submission.sandbox.executor;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.sandbox.BatchRunResult;
import com.ulticode.modules.submission.sandbox.LanguageProfile;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxExecutor;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.SandboxLimits;
import com.ulticode.modules.submission.sandbox.TestCase;
import com.ulticode.modules.submission.sandbox.UnsupportedLanguageException;
import com.ulticode.modules.submission.service.CodeExecutionHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default production {@link SandboxExecutor} implementation.
 *
 * <p>This class replaces the pre-M2a {@code SandboxServiceImpl}
 * (ADR-002 §1.1, §2.2). It does three things:
 * <ol>
 *   <li>Resolves the {@link LanguageProfile} for the job's
 *       {@link SandboxJob#languageId()}, fail-fast on duplicate
 *       registrations (caught in the constructor).</li>
 *   <li>Materializes the per-run workspace and writes
 *       {@code input.json} for the D-form harness.</li>
 *   <li>Spawns the docker container with the common security args
 *       and the per-language command from the profile, then parses
 *       the harness envelope back into {@link RunCaseResult}s.</li>
 * </ol>
 *
 * <h2>Cross-language concerns live here</h2>
 * <ul>
 *   <li>{@link #isSandboxForkFailure(String)} and
 *       {@link #isDockerDaemonForkFailure(String)} stay in the
 *       executor (ADR-002 §2.5) — they are not language-specific.</li>
 *   <li>The per-case list from {@link #runBatch} is always the same
 *       length and order as the input test-case list, even on
 *       infrastructure failure (the failing branch fills the list
 *       with one {@link SubmissionStatus#SANDBOX_ERROR} per input
 *       case).</li>
 * </ul>
 *
 * <h2>Activation</h2>
 * Active by default ({@code matchIfMissing = true}). The
 * test-only {@code InMemorySandboxAdapter} activates on
 * {@code sandbox.executor=inmemory}; both branches are mutually
 * exclusive.
 *
 * @see LanguageProfile
 * @see InMemorySandboxAdapter
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sandbox.executor",
                       havingValue = "docker",
                       matchIfMissing = true)
public class SandboxExecutorImpl implements SandboxExecutor {

    // ── Output budget ────────────────────────────────────────────────────────
    // 8 MiB envelope + 128 KiB per-case headroom, matches the pre-M2a
    // DFORM_OUTPUT_BUDGET_BYTES (Phase 3.5 #3 fix).
    private static final int DFORM_OUTPUT_BUDGET_BYTES = 8 * 1024 * 1024 + 128 * 1024;

    // Per-case soft timeout forwarded to the harness worker thread.
    // Matches the pre-M2a dFormPerCaseTimeoutMs derivation: hard
    // timeout minus 1s, floored at 500ms.
    private static final int DFORM_SOFT_TIMEOUT_BUFFER_MS = 1000;
    private static final int DFORM_SOFT_TIMEOUT_FLOOR_MS = 500;

    // ── Common security args (ADR-002 §3.3) ──────────────────────────────────
    // These are appended to every language's docker command so a
    // profile can never silently weaken sandbox isolation.
    private static final String DOCKER_BIN = "docker";
    private static final String SECCOMP_NO_NEW_PRIVS = "no-new-privileges:true";

    // ── State ────────────────────────────────────────────────────────────────
    private final Map<String, LanguageProfile> profiles;
    private final DockerSandboxConfig config;
    private final CodeExecutionHelper helper;

    public SandboxExecutorImpl(List<LanguageProfile> all,
                               DockerSandboxConfig config,
                               CodeExecutionHelper helper) {
        this.config = config;
        this.helper = helper;
        // Fail-fast: two profiles claiming the same language id is a
        // wiring bug, not a runtime fallback. Per ADR-002 §2.2.
        this.profiles = all.stream().collect(Collectors.toUnmodifiableMap(
                LanguageProfile::languageId,
                p -> p,
                (a, b) -> {
                    throw new IllegalStateException(
                            "Duplicate LanguageProfile for languageId="
                                    + a.languageId() + ": " + a.getClass().getName()
                                    + " vs " + b.getClass().getName());
                }));
        log.info("SandboxExecutorImpl wired with {} LanguageProfile(s): {}",
                profiles.size(), profiles.keySet());
    }

    // ── Public port contract ─────────────────────────────────────────────────

    @Override
    public RunCaseResult run(SandboxJob job, TestCase testCase) {
        return runOne(job, testCase, /* perCaseMs */ perCaseTimeoutMs(job));
    }

    @Override
    public BatchRunResult runBatch(SandboxJob job, List<TestCase> cases) {
        // The D-form harness runs all cases in a single container
        // invocation; the executor spawns docker once and parses one
        // envelope. Per ADR-002, the per-case list must preserve
        // input order and length even on failure.
        long start = System.nanoTime();
        try {
            DFormRunOutcome outcome = executeDForm(job, cases,
                    /* perCaseMs */ perCaseTimeoutMs(job));

            if (outcome.timedOut()) {
                long perCase = (System.nanoTime() - start) / 1_000_000
                        / Math.max(cases.size(), 1);
                return new BatchRunResult(cases.stream()
                        .map(c -> rejected(SubmissionStatus.TIME_LIMIT_EXCEEDED,
                                "D-form batch dispatch timed out after "
                                        + hardTimeoutSeconds(job) + "s",
                                perCase, 0L))
                        .toList());
            }

            if (outcome.exitCode() != 0) {
                if (isSandboxForkFailure(outcome.stdout())) {
                    return forkFailureBatch(cases, outcome, start);
                }
                boolean compile = profileOrThrow(job).isCompileFailure(outcome.stdout());
                SubmissionStatus status = compile
                        ? SubmissionStatus.COMPILE_ERROR
                        : SubmissionStatus.RUNTIME_ERROR;
                long perCase = (System.nanoTime() - start) / 1_000_000
                        / Math.max(cases.size(), 1);
                String detail = helper.sanitizeSandboxOutput(outcome.stdout());
                return new BatchRunResult(cases.stream()
                        .map(c -> rejected(status, detail, perCase, 0L))
                        .toList());
            }

            // Happy path: parse the harness envelope into one
            // RunCaseResult per case. Translate at the DTO boundary.
            List<RunSubmissionDTO.RunTestCase> runCases = cases.stream()
                    .map(this::toRunTestCase)
                    .toList();
            List<RunCaseResult> parsed = helper.parseDEnvelope(
                    outcome.stdout(), runCases, job.runId(), job.userId())
                    .stream()
                    .map(this::toPortResult)
                    .toList();
            return new BatchRunResult(parsed);

        } catch (UnsupportedLanguageException e) {
            return unsupportedLanguageBatch(cases, e);
        }
    }

    // ── Per-case entry (single test) ─────────────────────────────────────────

    private RunCaseResult runOne(SandboxJob job, TestCase tc, long perCaseMs) {
        long start = System.nanoTime();
        try {
            DFormRunOutcome outcome = executeDForm(job, List.of(tc), perCaseMs);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            if (outcome.timedOut()) {
                return rejected(SubmissionStatus.TIME_LIMIT_EXCEEDED,
                        "D-form dispatch timed out after " + hardTimeoutSeconds(job) + "s",
                        elapsedMs, 0L);
            }
            if (outcome.exitCode() != 0) {
                if (isSandboxForkFailure(outcome.stdout())) {
                    log.warn("D-form sandbox fork failure for runId={}: {}",
                            job.runId(),
                            truncateForLog(helper.sanitizeSandboxOutput(outcome.stdout())));
                    return rejected(SubmissionStatus.SANDBOX_ERROR,
                            "sandbox fork failure: "
                                    + helper.sanitizeSandboxOutput(outcome.stdout()),
                            elapsedMs, 0L);
                }
                boolean compile = profileOrThrow(job).isCompileFailure(outcome.stdout());
                SubmissionStatus status = compile
                        ? SubmissionStatus.COMPILE_ERROR
                        : SubmissionStatus.RUNTIME_ERROR;
                return rejected(status,
                        helper.sanitizeSandboxOutput(outcome.stdout()),
                        elapsedMs, 0L);
            }
            List<RunCaseResult> parsed = helper.parseDEnvelope(
                    outcome.stdout(), List.of(toRunTestCase(tc)),
                    job.runId(), job.userId())
                    .stream()
                    .map(this::toPortResult)
                    .toList();
            if (parsed.isEmpty()) {
                return rejected(SubmissionStatus.RUNTIME_ERROR,
                        "D-form envelope empty", elapsedMs, 0L);
            }
            return parsed.get(0);
        } catch (UnsupportedLanguageException e) {
            return unsupportedLanguageSingle(e);
        }
    }

    // ── Docker invocation (Phase 5b D-form, refactored) ──────────────────────

    private DFormRunOutcome executeDForm(SandboxJob job, List<TestCase> cases, long perCaseMs) {
        Path jobDir = null;
        try {
            LanguageProfile profile = profileOrThrow(job);
            SandboxLimits limits = profile.effectiveLimits(job);
            // The pre-existing CodeExecutionHelper still speaks
            // RunSubmissionDTO.RunTestCase; we map at the boundary so
            // the sandbox port stays decoupled from the DTO package.
            List<RunSubmissionDTO.RunTestCase> runCases = cases.stream()
                    .map(this::toRunTestCase)
                    .toList();
            String inputJson = runCases.size() == 1
                    ? helper.buildDInputsJson(runCases.get(0), perCaseMs)
                    : helper.buildDBatchInputsJson(runCases, perCaseMs);

            jobDir = Files.createTempDirectory("ulticode-sandbox-" + job.runId() + "-");
            Path workspace = profile.materializeWorkspace(jobDir, job.code());
            writeInputJson(jobDir, inputJson);
            // Make input.json read-only too (matches the pre-M2a
            // chmod 0444 in materializeDFormJob).
            Files.setPosixFilePermissions(jobDir.resolve("input.json"),
                    READ_ONLY_POSIX);

            List<String> command = buildDockerCommand(job, profile, workspace, limits);
            return runDProcess(command, hardTimeoutSeconds(job), job.runId());
        } catch (IOException e) {
            log.warn("D-form workspace setup failed for runId={}: {}",
                    job.runId(), e.getMessage());
            return DFormRunOutcome.error(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DFormRunOutcome.interrupted();
        } finally {
            cleanupJobDir(jobDir);
        }
    }

    /**
     * Compose the full {@code docker run} command:
     * <pre>
     *   docker run --rm -i
     *     &lt;commonSecurityArgs&gt;
     *     --memory &lt;effective&gt; --cpus &lt;cpus&gt; --pids-limit &lt;N&gt;
     *     --tmpfs /tmp:rw,exec,size=64m --ulimit nofile=128:128
     *     --volume &lt;jobDir&gt;:/job:ro
     *     --volume &lt;seccompDir&gt;:/seccomp-profile:ro
     *     &lt;profile.dockerCommand&gt;...
     * </pre>
     */
    private List<String> buildDockerCommand(SandboxJob job,
                                            LanguageProfile profile,
                                            Path workspace,
                                            SandboxLimits limits) {
        // Resolve per-language memory override (matches the pre-M2a
        // buildDDockerCommand logic).
        String effectiveMemory = Optional.ofNullable(config.languages())
                .map(m -> m.get(job.languageId()))
                .map(DockerSandboxConfig.LanguageLimit::memory)
                .orElse(config.memory());

        List<String> cmd = new ArrayList<>();
        cmd.add(DOCKER_BIN);
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-i");
        cmd.addAll(commonSecurityArgs());
        cmd.add("--memory");
        cmd.add(effectiveMemory);
        cmd.add("--cpus");
        cmd.add(config.cpus());
        cmd.add("--pids-limit");
        cmd.add(String.valueOf(config.pidsLimit()));
        cmd.add("--ulimit");
        cmd.add("nofile=128:128");
        cmd.add("--tmpfs");
        cmd.add("/tmp:rw,exec,size=64m");
        cmd.add("--volume");
        cmd.add(workspace.toAbsolutePath() + ":/job:ro");
        cmd.add("--volume");
        cmd.add(resolveSeccompProfileDirectoryPath() + ":/seccomp-profile:ro");
        cmd.addAll(profile.dockerCommand(job, workspace));
        return cmd;
    }

    /**
     * Common security args (ADR-002 §3.3). Profiles are forbidden
     * from overriding these — if a future language needs something
     * not covered here, add it here, never in the profile.
     */
    private List<String> commonSecurityArgs() {
        return List.of(
                "--network", "none",
                "--cap-drop", "ALL",
                "--read-only",
                "--user", "1000:1000",
                "--security-opt", SECCOMP_NO_NEW_PRIVS,
                "--security-opt", "seccomp=" + resolveSeccompProfileFilePath()
        );
    }

    /**
     * Spawn the docker process with a concurrent stdout drainer.
     * Mirrors the pre-M2a runDProcess (Phase 3.5 #3 fix: drain the
     * pipe in a background thread to avoid the 64 KiB Linux pipe
     * buffer deadlock).
     */
    private DFormRunOutcome runDProcess(List<String> command,
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
            // SANDBOX_ERROR via isDockerDaemonForkFailure.
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (isDockerDaemonForkFailure(msg)) {
                return DFormRunOutcome.finished(0L,
                        "Cannot fork / pids-limit reached", 137);
            }
            return DFormRunOutcome.error(e);
        }
    }

    // ── Fork-failure detection (ADR-002 §2.5: stays in executor) ──────────────

    static boolean isSandboxForkFailure(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }
        return output.contains("Cannot fork")
                || output.contains("Resource temporarily unavailable")
                || output.contains("fork: Cannot allocate memory");
    }

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

    // ── Profile resolution ───────────────────────────────────────────────────

    private LanguageProfile profileOrThrow(SandboxJob job) {
        LanguageProfile p = profiles.get(job.languageId());
        if (p == null) {
            throw new UnsupportedLanguageException(job.languageId());
        }
        return p;
    }

    // ── Translation helpers ──────────────────────────────────────────────────

    private RunCaseResult rejected(SubmissionStatus status, String detail,
                                   long elapsedMs, long memoryBytes) {
        return RunCaseResult.rejected(status, detail, elapsedMs, memoryBytes);
    }

    private RunCaseResult unsupportedLanguageSingle(UnsupportedLanguageException e) {
        return rejected(SubmissionStatus.SANDBOX_ERROR,
                "D-form harness not implemented for language: " + e.languageId(),
                0L, 0L);
    }

    private BatchRunResult unsupportedLanguageBatch(List<TestCase> cases,
                                                    UnsupportedLanguageException e) {
        String detail = "D-form harness not implemented for language: " + e.languageId();
        return new BatchRunResult(cases.stream()
                .map(c -> rejected(SubmissionStatus.SANDBOX_ERROR, detail, 0L, 0L))
                .toList());
    }

    private BatchRunResult forkFailureBatch(List<TestCase> cases,
                                            DFormRunOutcome outcome,
                                            long start) {
        long perCase = (System.nanoTime() - start) / 1_000_000
                / Math.max(cases.size(), 1);
        String detail = "sandbox fork failure: "
                + helper.sanitizeSandboxOutput(outcome.stdout());
        return new BatchRunResult(cases.stream()
                .map(c -> rejected(SubmissionStatus.SANDBOX_ERROR, detail, perCase, 0L))
                .toList());
    }

    // ── File I/O helpers ────────────────────────────────────────────────────

    /**
     * Translate the port-owned {@link TestCase} into the DTO the
     * pre-existing {@link CodeExecutionHelper} still speaks. Lives
     * here (not in the port) so {@code sandbox} stays decoupled from
     * the {@code submission.dto} package in the public type
     * signatures; only this executor — which is the seam — touches
     * the DTO type.
     */
    private RunSubmissionDTO.RunTestCase toRunTestCase(TestCase tc) {
        RunSubmissionDTO.RunTestCase rtc = new RunSubmissionDTO.RunTestCase();
        rtc.setId(tc.id());
        rtc.setLabel(tc.label());
        rtc.setOutput(tc.expectedOutput());
        List<RunSubmissionDTO.RunInput> inputs = new ArrayList<>(tc.inputs().size());
        for (TestCase.Input in : tc.inputs()) {
            RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
            ri.setId(in.id());
            ri.setLabel(in.label());
            ri.setName(in.name());
            ri.setValue(in.value());
            ri.setType(in.type());
            inputs.add(ri);
        }
        rtc.setInputs(inputs);
        return rtc;
    }

    /**
     * Translate the DTO-level {@link RunResultDTO.RunCaseResult}
     * (which carries a wire-string status) into the port-owned
     * {@link RunCaseResult} (which carries a
     * {@link SubmissionStatus} enum, per ADR-001).
     *
     * <p>The helper writes the pre-formatted runtime / memory
     * strings (e.g. {@code "12ms"} / {@code "22.0MB"}) AND the
     * numeric v2 fields (e.g. {@code runtimeMs} /
     * {@code memoryMb}). We prefer the numeric fields when
     * present and fall back to the formatted strings for legacy
     * callers, matching the pre-M2a behavior.
     */
    private RunCaseResult toPortResult(RunResultDTO.RunCaseResult dto) {
        SubmissionStatus status = SubmissionStatusCodec.fromWire(dto.getStatus());
        long elapsedMs = dto.getRuntimeMs() != null
                ? dto.getRuntimeMs()
                : helper.parseRuntimeMs(dto.getRuntime());
        long memoryBytes = dto.getMemoryMb() != null
                ? (long) (dto.getMemoryMb() * 1024L * 1024L)
                : 0L;
        double score = status == SubmissionStatus.ACCEPTED ? 1.0 : 0.0;
        return new RunCaseResult(status, elapsedMs, memoryBytes, dto.getDetail(), score);
    }

    private static final Set<PosixFilePermission> READ_ONLY_POSIX =
            EnumSet.of(PosixFilePermission.OWNER_READ,
                       PosixFilePermission.GROUP_READ,
                       PosixFilePermission.OTHERS_READ);

    private void writeInputJson(Path jobDir, String inputJson) throws IOException {
        Files.writeString(jobDir.resolve("input.json"),
                inputJson == null ? "{}" : inputJson, StandardCharsets.UTF_8);
    }

    private void cleanupJobDir(Path jobDir) {
        if (jobDir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(jobDir)) {
            walk.sorted((a, b) -> b.toString().length() - a.toString().length())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException ignored) { /* best-effort */ }
                    });
        } catch (IOException e) {
            log.debug("Failed to clean sandbox job dir {}: {}", jobDir, e.getMessage());
        }
    }

    private static String truncateForLog(String s) {
        if (s == null) return "<null>";
        return s.length() <= 4096 ? s : s.substring(0, 4096) + "... [truncated]";
    }

    // ── Timeout math ─────────────────────────────────────────────────────────

    private int hardTimeoutSeconds(SandboxJob job) {
        // job.timeoutSeconds is the per-run soft cap. The docker
        // --stop-timeout (which is what bounds the process itself
        // from the kernel side) gets a +1s grace to let the harness
        // write its partial envelope before SIGKILL.
        return job.timeoutSeconds() + 1;
    }

    private long perCaseTimeoutMs(SandboxJob job) {
        int hardMs = hardTimeoutSeconds(job) * 1000;
        long candidate = hardMs - DFORM_SOFT_TIMEOUT_BUFFER_MS;
        return Math.max(candidate, DFORM_SOFT_TIMEOUT_FLOOR_MS);
    }

    // ── Seccomp resolution (matches the pre-M2a helpers) ─────────────────────

    private String resolveSeccompProfileFilePath() {
        String path = config.seccompProfilePath();
        // The pre-M2a code resolves both the file and the
        // directory containing it; the directory is bind-mounted so
        // docker can read the JSON file. Keeping the same convention
        // avoids drift.
        return path;
    }

    private String resolveSeccompProfileDirectoryPath() {
        String path = config.seccompProfilePath();
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return idx >= 0 ? path.substring(0, idx) : ".";
    }

    // ── Local DTO for runDProcess results ────────────────────────────────────

    private record DFormRunOutcome(boolean timedOut, long elapsedMs,
                                   String stdout, int exitCode, Throwable error) {
        static DFormRunOutcome finished(long elapsedMs, String stdout, int exitCode) {
            return new DFormRunOutcome(false, elapsedMs, stdout, exitCode, null);
        }
        static DFormRunOutcome timedOut(long elapsedMs) {
            return new DFormRunOutcome(true, elapsedMs, "", -1, null);
        }
        static DFormRunOutcome error(Throwable e) {
            return new DFormRunOutcome(false, 0L,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    -1, e);
        }
        static DFormRunOutcome interrupted() {
            return new DFormRunOutcome(false, 0L, "", -1,
                    new BusinessException(ErrorCode.SANDBOX_ERROR, "interrupted"));
        }
    }
}
