package com.ulticode.modules.submission.sandbox.executor;

import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.dto.RunSubmissionDTO;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.sandbox.BatchRunResult;
import com.ulticode.modules.submission.sandbox.LanguageProfile;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxExecutor;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.SandboxLimits;
import com.ulticode.modules.submission.sandbox.SandboxOutcomeClassifier;
import com.ulticode.modules.submission.sandbox.SandboxOutcomeClassifier.SandboxInfraFailure;
import com.ulticode.modules.submission.sandbox.TestCase;
import com.ulticode.modules.submission.sandbox.UnsupportedLanguageException;
import com.ulticode.modules.submission.service.DFormEnvelopeCodec;
import com.ulticode.modules.submission.service.SandboxOutputFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
 *   <li>Infra-failure detection delegates to
 *       {@link SandboxOutcomeClassifier} (the single source of truth
 *       for exit-code / output → {@link SandboxInfraFailure} →
 *       {@link SubmissionStatus} mapping). The legacy static
 *       {@link #isSandboxForkFailure(String)} /
 *       {@link #isDockerDaemonForkFailure(String)} helpers are
 *       retained as thin delegations to the classifier so the
 *       regression coverage in
 *       {@code SandboxExecutorImplForkDetectionTest} continues to
 *       pass without behavior change.</li>
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

    // Per-case soft timeout floor forwarded to the harness (its
    // per_case_timeout_ms). ADR-002 §8: the soft timeout now equals the
    // problem's per-case limit directly (no longer derived from a single
    // hard timeout), floored at 500ms so a misconfigured 0 doesn't TLE
    // every case instantly.
    private static final int DFORM_SOFT_TIMEOUT_FLOOR_MS = 500;

    // ── Docker hard-timeout math (ADR-002 §8) ───────────────────────────────
    // The whole-batch hard timeout scales with case count so N cases each
    // allowed `timeoutSeconds` don't get SIGKILLed after the first one.
    private static final int MAX_BATCH_HARD_TIMEOUT_SECONDS = 180;
    // C/C++ g++ compile budget folded into the docker hard timeout so the
    // runner's compile phase never gets killed by the outer cap (ADR-002 §8
    // / P1-4). Interpreted languages get 0.
    private static final int COMPILE_BUDGET_SECONDS = 35;
    // Grace for docker startup + envelope flush after the last case.
    private static final int DOCKER_GRACE_SECONDS = 2;

    // ── Common security args (ADR-002 §3.3) ──────────────────────────────────
    // These are appended to every language's docker command so a
    // profile can never silently weaken sandbox isolation.
    private static final String DOCKER_BIN = "docker";
    private static final String SECCOMP_NO_NEW_PRIVS = "no-new-privileges:true";

    // ── State ────────────────────────────────────────────────────────────────
    private final Map<String, LanguageProfile> profiles;
    private final DockerSandboxConfig config;
    private final DFormEnvelopeCodec dFormEnvelopeCodec;
    private final SandboxOutputFormatter sandboxOutputFormatter;
    private final SandboxOutcomeClassifier outcomeClassifier;
    private final ProcessLifecycleRunner processLifecycleRunner;
    private final SandboxResultTranslator resultTranslator;

    public SandboxExecutorImpl(List<LanguageProfile> all,
                               DockerSandboxConfig config,
                               DFormEnvelopeCodec dFormEnvelopeCodec,
                               SandboxOutputFormatter sandboxOutputFormatter,
                               SandboxOutcomeClassifier outcomeClassifier,
                               ProcessLifecycleRunner processLifecycleRunner) {
        this.config = config;
        this.dFormEnvelopeCodec = dFormEnvelopeCodec;
        this.sandboxOutputFormatter = sandboxOutputFormatter;
        this.outcomeClassifier = outcomeClassifier;
        this.processLifecycleRunner = processLifecycleRunner;
        this.resultTranslator = new SandboxResultTranslator(sandboxOutputFormatter, outcomeClassifier);
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
                SubmissionStatus status = outcomeClassifier.timeLimitExceeded();
                return new BatchRunResult(cases.stream()
                        .map(c -> rejected(status,
                                "D-form batch dispatch timed out after "
                                        + hardTimeoutSeconds(job, cases.size()) + "s",
                                perCase, 0L))
                        .toList());
            }

            if (outcome.exitCode() != 0) {
                // Classify the raw outcome through the single-source
                // SandboxOutcomeClassifier (replaces the inline ~14
                // SubmissionStatus literals and the substring-on-sanitized-
                // output fork oracle). compileFailure comes from the
                // active LanguageProfile (only consulted when we couldn't
                // classify this as an infra failure first).
                boolean compile = profileOrThrow(job).isCompileFailure(outcome.stdout());
                SandboxInfraFailure failure = outcomeClassifier.classify(
                        outcome.exitCode(), outcome.stdout(), outcome.cause(), compile);
                if (failure != SandboxInfraFailure.NONE) {
                    return infraFailureBatch(cases, outcome, start, failure);
                }
                long perCase = (System.nanoTime() - start) / 1_000_000
                        / Math.max(cases.size(), 1);
                String detail = sandboxOutputFormatter.sanitizeSandboxOutput(outcome.stdout());
                SubmissionStatus status = outcomeClassifier.genericRuntimeError();
                return new BatchRunResult(cases.stream()
                        .map(c -> rejected(status,
                                detail, perCase, 0L))
                        .toList());
            }

            // Happy path: parse the harness envelope into one
            // RunCaseResult per case. Translate at the DTO boundary.
            List<RunSubmissionDTO.RunTestCase> runCases = cases.stream()
                    .map(resultTranslator::toRunTestCase)
                    .toList();
            List<RunResultDTO.RunCaseResult> parsedDto = dFormEnvelopeCodec.parseDEnvelope(
                    outcome.stdout(), runCases, job.runId(), job.userId());
            // F3: zip with the original port-owned cases so each
            // toPortResult call can preserve the original input
            // metadata (the DTO already has the harness's actual
            // output; the port only needs the inputs and expected
            // output from the request).
            long memoryLimitBytes = effectiveMemoryLimitBytes(job);
            List<RunCaseResult> parsed = new ArrayList<>(parsedDto.size());
            for (int i = 0; i < parsedDto.size(); i++) {
                parsed.add(resultTranslator.toPortResult(parsedDto.get(i), cases.get(i), memoryLimitBytes));
            }
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
                return rejected(outcomeClassifier.timeLimitExceeded(),
                        "D-form dispatch timed out after " + hardTimeoutSeconds(job, 1) + "s",
                        elapsedMs, 0L);
            }
            if (outcome.exitCode() != 0) {
                // Same classification path as runBatch — see comments there.
                boolean compile = profileOrThrow(job).isCompileFailure(outcome.stdout());
                SandboxInfraFailure failure = outcomeClassifier.classify(
                        outcome.exitCode(), outcome.stdout(), outcome.cause(), compile);
                if (failure != SandboxInfraFailure.NONE) {
                    return rejectedInfra(outcome, failure, elapsedMs);
                }
                return rejected(outcomeClassifier.genericRuntimeError(),
                        sandboxOutputFormatter.sanitizeSandboxOutput(outcome.stdout()),
                        elapsedMs, 0L);
            }
            long memoryLimitBytes = effectiveMemoryLimitBytes(job);
            List<RunCaseResult> parsed = dFormEnvelopeCodec.parseDEnvelope(
                    outcome.stdout(), List.of(resultTranslator.toRunTestCase(tc)),
                    job.runId(), job.userId())
                    .stream()
                    .map(dto -> resultTranslator.toPortResult(dto, tc, memoryLimitBytes))
                    .toList();
            if (parsed.isEmpty()) {
                return rejected(outcomeClassifier.genericRuntimeError(),
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
                    .map(resultTranslator::toRunTestCase)
                    .toList();
            String inputJson = runCases.size() == 1
                    ? dFormEnvelopeCodec.buildDInputsJson(runCases.get(0), perCaseMs, effectiveMemoryLimitBytes(job))
                    : dFormEnvelopeCodec.buildDBatchInputsJson(runCases, perCaseMs, effectiveMemoryLimitBytes(job));

            jobDir = Files.createTempDirectory("ulticode-sandbox-" + job.runId() + "-");
            // Rootless remote daemons map child-container uid 1000 through a
            // subordinate host range. Shared traversal permissions are required
            // because the daemon cannot use the worker's private 0700 default.
            Files.setPosixFilePermissions(jobDir, SHARED_WORKSPACE_POSIX);
            Path workspace = profile.materializeWorkspace(jobDir, job.code());
            writeInputJson(jobDir, inputJson);
            // Make input.json read-only too (matches the pre-M2a
            // chmod 0444 in materializeDFormJob).
            Files.setPosixFilePermissions(jobDir.resolve("input.json"),
                    READ_ONLY_POSIX);

            List<String> command = buildDockerCommand(job, profile, workspace, limits);
            return processLifecycleRunner.run(command, hardTimeoutSeconds(job, cases.size()), job.runId());
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
     *     &lt;profile.dockerCommand&gt;...
     * </pre>
     */
    private List<String> buildDockerCommand(SandboxJob job,
                                            LanguageProfile profile,
                                            Path workspace,
                                            SandboxLimits limits) {
        // M2a-round-2 fix (codex review F4): honor the profile's
        // effective limits (ADR-002 §2.2) — the per-run
        // SandboxLimits returned by profile.effectiveLimits(job) is
        // the single source of truth for what the executor actually
        // applies. Profiles are free to tighten/relax per-language
        // memory; the executor never re-derives from config.
        String effectiveMemory = limits.memoryMb() + "m";

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
        cmd.add("--name");
        cmd.add("ulticode-sandbox-" + job.runId());
        cmd.add("--cidfile");
        cmd.add(workspace.resolve(".container.cid").toAbsolutePath().toString());
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

    // ── Fork-failure detection (ADR-002 §2.5: classifier is the oracle) ───────

    /**
     * @deprecated Retained as a thin delegation to
     * {@link SandboxOutcomeClassifier#looksLikeSandboxForkFailure(String)}
     * so the regression coverage in
     * {@code SandboxExecutorImplForkDetectionTest} keeps passing. New
     * call sites should inject the classifier directly.
     */
    @Deprecated
    static boolean isSandboxForkFailure(String output) {
        return new SandboxOutcomeClassifier().looksLikeSandboxForkFailure(output);
    }

    /**
     * @deprecated Retained as a thin delegation to
     * {@link SandboxOutcomeClassifier#looksLikeDockerDaemonForkFailure(String)}.
     */
    @Deprecated
    static boolean isDockerDaemonForkFailure(String msg) {
        return new SandboxOutcomeClassifier().looksLikeDockerDaemonForkFailure(msg);
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
        return rejected(outcomeClassifier.unsupportedLanguage(),
                "D-form harness not implemented for language: " + e.languageId(),
                0L, 0L);
    }

    private BatchRunResult unsupportedLanguageBatch(List<TestCase> cases,
                                                    UnsupportedLanguageException e) {
        SubmissionStatus status = outcomeClassifier.unsupportedLanguage();
        String detail = "D-form harness not implemented for language: " + e.languageId();
        return new BatchRunResult(cases.stream()
                .map(c -> rejected(status, detail, 0L, 0L))
                .toList());
    }

    private BatchRunResult forkFailureBatch(List<TestCase> cases,
                                            DFormRunOutcome outcome,
                                            long start) {
        // Kept for source-compat with any external caller; forwards to the
        // generic infra-failure builder with FORK_LIMIT.
        return infraFailureBatch(cases, outcome, start, SandboxInfraFailure.FORK_LIMIT);
    }

    /**
     * Build a {@link BatchRunResult} filled with one rejected case per input
     * test case. The single-source
     * {@link SandboxOutcomeClassifier#toStatus(SandboxInfraFailure)} picks
     * the right {@link SubmissionStatus} (typically
     * {@link SubmissionStatus#SANDBOX_ERROR}, or
     * {@link SubmissionStatus#COMPILE_ERROR} for COMPILE_ERROR); the
     * detail string is the user-facing sanitized output so users no
     * longer see raw docker / OCI lines that used to be scrubbed and
     * flattened to a generic "Runtime Error".
     */
    private BatchRunResult infraFailureBatch(List<TestCase> cases,
                                             DFormRunOutcome outcome,
                                             long start,
                                             SandboxInfraFailure failure) {
        // Log the raw evidence once per batch — previously the raw
        // output was scrubbed before any oracle saw it, so real docker /
        // OCI errors silently disappeared into "Runtime error" + memory=0.
        outcomeClassifier.logInfraFailure(/* runId */ null, failure,
                outcome.exitCode(), outcome.stdout(), outcome.cause());
        long perCase = (System.nanoTime() - start) / 1_000_000
                / Math.max(cases.size(), 1);
        SubmissionStatus status = outcomeClassifier.toStatus(failure);
        String detail = buildInfraDetail(failure, outcome);
        return new BatchRunResult(cases.stream()
                .map(c -> rejected(status, detail, perCase, 0L))
                .toList());
    }

    private RunCaseResult rejectedInfra(DFormRunOutcome outcome,
                                        SandboxInfraFailure failure,
                                        long elapsedMs) {
        outcomeClassifier.logInfraFailure(null, failure,
                outcome.exitCode(), outcome.stdout(), outcome.cause());
        SubmissionStatus status = outcomeClassifier.toStatus(failure);
        String detail = buildInfraDetail(failure, outcome);
        return rejected(status, detail, elapsedMs, 0L);
    }

    /**
     * Build a stable, user-facing detail string for each infra-failure
     * category. The previous code reused
     * {@code sandboxOutputFormatter.sanitizeSandboxOutput(outcome.stdout())} which
     * stripped docker / OCI lines — leaving the user with a generic
     * "Runtime error". Each branch now spells out which infra signal
     * fired and (for fork / OOM / OCI / launch) keeps a sanitized
     * fragment so the user can debug.
     */
    private String buildInfraDetail(SandboxInfraFailure failure, DFormRunOutcome outcome) {
        String sanitized = sandboxOutputFormatter.sanitizeSandboxOutput(outcome.stdout());
        switch (failure) {
            case OUT_OF_MEMORY:
                return "sandbox process killed (exit 137; likely cgroup OOM or hard timeout)";
            case FORK_LIMIT:
                return "sandbox fork failure: " + sanitized;
            case OCI_ERROR:
                return "sandbox OCI runtime failure: " + sanitized;
            case LAUNCH_FAILURE:
                String c = outcome.cause() == null ? "" : outcome.cause().toString();
                return "sandbox launch failure: " + c;
            case COMPILE_ERROR:
                return sanitized;
            default:
                return sanitized;
        }
    }

    // ── File I/O helpers ────────────────────────────────────────────────────

    private static final Set<PosixFilePermission> READ_ONLY_POSIX =
            EnumSet.of(PosixFilePermission.OWNER_READ,
                       PosixFilePermission.GROUP_READ,
                       PosixFilePermission.OTHERS_READ);
    private static final Set<PosixFilePermission> SHARED_WORKSPACE_POSIX =
            EnumSet.of(PosixFilePermission.OWNER_READ,
                       PosixFilePermission.OWNER_WRITE,
                       PosixFilePermission.OWNER_EXECUTE,
                       PosixFilePermission.GROUP_READ,
                       PosixFilePermission.GROUP_EXECUTE,
                       PosixFilePermission.OTHERS_READ,
                       PosixFilePermission.OTHERS_EXECUTE);

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

    // ── Timeout math (ADR-002 §8) ────────────────────────────────────────────

    private static boolean isCompiledLanguage(SandboxJob job) {
        String lang = job.languageId();
        return "c".equals(lang) || "cpp".equals(lang);
    }

    /**
     * Docker hard timeout for the whole batch. ADR-002 §8 (P0-1): scales
     * with case count so N cases each allowed {@code timeoutSeconds} don't
     * get SIGKILLed after the first one (the old {@code timeoutSeconds + 1}
     * formula made multi-case previews TLE the whole batch). Compiled
     * languages get a compile budget on top (P1-4). Capped to bound a
     * pathological caseCount.
     */
    private int hardTimeoutSeconds(SandboxJob job, int caseCount) {
        int n = Math.max(1, caseCount);
        int compile = isCompiledLanguage(job) ? COMPILE_BUDGET_SECONDS : 0;
        long total = (long) job.timeoutSeconds() * n + compile + DOCKER_GRACE_SECONDS;
        return (int) Math.min(total, MAX_BATCH_HARD_TIMEOUT_SECONDS);
    }

    /**
     * Per-case soft timeout forwarded to the harness (its
     * {@code per_case_timeout_ms}). Equals the problem's per-case limit;
     * the harness self-reports Time Limit Exceeded when a single case
     * exceeds it. ADR-002 §8.
     */
    private long perCaseTimeoutMs(SandboxJob job) {
        return Math.max(DFORM_SOFT_TIMEOUT_FLOOR_MS, job.timeoutSeconds() * 1000L);
    }

    /**
     * Effective per-run memory ceiling in bytes (from the active
     * LanguageProfile's limits). Forwarded to the harness as
     * {@code memory_limit_bytes} so it can self-report MLE, and used by
     * {@link SandboxResultTranslator#toPortResult} as the Layer-B backstop threshold. ADR-002 §8.
     */
    private long effectiveMemoryLimitBytes(SandboxJob job) {
        SandboxLimits limits = profileOrThrow(job).effectiveLimits(job);
        return (long) limits.memoryMb() * 1024L * 1024L;
    }

    // ── Seccomp resolution ─────────────────────────────────────────────────

    // Cap on how far resolveSeccompProfileFilePath() walks up from user.dir
    // while re-rooting a repository-root-relative profile path. Bounded so a
    // pathological user.dir near the filesystem root still terminates quickly.
    private static final int SECCOMP_REROOT_MAX_DEPTH = 10;

    /**
     * Resolve the host-side seccomp profile path that gets passed to
     * {@code docker run --security-opt seccomp=<path>} (read by the daemon).
     * <p>The configured path is intentionally <b>relative</b> (no absolute
     * paths in config — portability/design-norm). It is treated as
     * <b>repository-root-relative</b> and re-rooted by walking up from the
     * JVM working directory until the target exists. This is necessary
     * because {@code user.dir} drifts per launch mode: {@code mvn
     * spring-boot:run} forks a JVM whose {@code user.dir} is the module
     * directory (e.g. {@code services/app/app-web}), a packaged jar uses
     * wherever it was launched from, and PM2 sets {@code cwd = services/}.
     * A single relative path resolved via {@code Path.toAbsolutePath()}
     * therefore pointed at different files per launch mode and silently
     * broke every judge call (docker exit 125 → masked "Runtime Error").
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Absolute path → used verbatim.</li>
     *   <li>{@code user.dir/<path>} if it exists → returned (fast path when
     *       the JVM already runs from the repo root).</li>
     *   <li>Walk up from {@code user.dir.getParent()}; the first
     *       {@code ancestor/<path>} that exists is returned (re-root). This
     *       is the normal path under {@code mvn spring-boot:run}, so it is
     *       silent; a WARN fires only in the next branch.</li>
     *   <li>Nothing matched → return the {@code user.dir}-relative path so
     *       docker surfaces a clear "no such file" error rather than
     *       silently weakening isolation by dropping the seccomp filter.
     *       This is the one case that logs a WARN.</li>
     * </ol>
     *
     * <p>No filename or directory constant is embedded here — the walk-up
     * re-roots whatever relative path configuration supplied.
     */
    private String resolveSeccompProfileFilePath() {
        String path = config.seccompProfilePath();
        if (path == null || path.isBlank()) {
            return "";
        }
        String resolved = resolveSeccompProfile(path,
                Path.of(System.getProperty("user.dir", ".")));
        // Re-rooting under spring-boot:run is the normal path (no log). Warn
        // only when the resolved file does not exist — that is the one case
        // where docker will reject the run (exit 125). The classifier now
        // surfaces that as SANDBOX_ERROR instead of masking it as a user
        // "Runtime Error", and this log pinpoints the misconfiguration.
        if (!Files.exists(Path.of(resolved))) {
            log.warn("Seccomp profile '{}' could not be resolved from user.dir ({}); "
                            + "returning {} — docker will reject the run with a clear error.",
                    path, System.getProperty("user.dir"), resolved);
        }
        return resolved;
    }

    /**
     * Pure resolution logic, extracted for unit testing. Resolves a
     * repository-root-relative {@code configuredPath} by walking up from
     * {@code userDir} until the target exists. See
     * {@link #resolveSeccompProfileFilePath()} for the full rationale.
     */
    static String resolveSeccompProfile(String configuredPath, Path userDir) {
        Path configured = Path.of(configuredPath);
        if (configured.isAbsolute()) {
            return configured.toString();
        }
        Path direct = userDir.resolve(configured).normalize();
        if (Files.exists(direct)) {
            return direct.toString();
        }
        Path ancestor = userDir.getParent();
        for (int depth = 0; ancestor != null && depth < SECCOMP_REROOT_MAX_DEPTH; depth++) {
            Path candidate = ancestor.resolve(configured).normalize();
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
            ancestor = ancestor.getParent();
        }
        // Nothing matched: return the user.dir-relative path so docker
        // surfaces a clear "no such file" error instead of silently
        // weakening isolation by dropping the seccomp filter.
        return direct.toString();
    }


}
