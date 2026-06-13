package com.ulticode.modules.submission.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.service.CodeExecutionHelper;
import com.ulticode.modules.submission.service.SandboxService;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of SandboxService.
 * Handles Docker sandbox lifecycle and security parameters.
 * Delegates per-language wrapper generation and result parsing to CodeExecutionHelper.
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
     * Detects sandbox-level fork failures that are NOT caused by user code.
     * Symptoms of host/cgroup/seccomp pressure rather than user program bugs:
     * busybox sh, kernel cgroup, docker daemon, or libc fwrite all surface here.
     *
     * <p>Match heuristics: covers the three dominant failure surfaces observed
     * in production (busybox sh "Cannot fork", glibc / dockerd
     * "Resource temporarily unavailable", and seccomp OOM/fork kill).
     *
     * @param output merged stdout/stderr (sandbox uses {@code redirectErrorStream(true)})
     * @return true if output strongly suggests an environmental fork failure
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
     * Stricter than {@link #isSandboxForkFailure(String)} because docker daemon messages
     * commonly include "pids" in unrelated contexts (e.g. configuration warnings).
     *
     * <p>Accepts only phrases that unambiguously identify a fork failure at the
     * daemon layer: kernel cgroup pressure, RLIMIT_NPROC exhaustion, or
     * dockerd PID-controller refusal.
     *
     * @param msg docker daemon IOException message; may be null
     * @return true if the message unambiguously indicates a fork failure
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

    /**
     * Truncates detail text for log emission to keep structured log aggregators healthy.
     * Appends a marker so the operator can see the original length if relevant.
     *
     * @param detail raw detail text (may be null)
     * @return detail unchanged if short; truncated with a length marker otherwise
     */
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

    @Override
    public RunResultDTO.RunCaseResult executeInSandbox(String language, String code,
                                                      RunSubmissionDTO.RunTestCase testCase,
                                                      String runId, String userId) {
        if (useDForm(language)) {
            return executeInSandboxD(language, code, testCase, runId, userId);
        }
        try {
            String inputsJson = helper.buildInputsJson(testCase);
            List<String> command = buildDockerCommand(language, code);

            DockerSandboxConfig.LanguageLimit langLimit = sandboxConfig.languages() != null
                    ? sandboxConfig.languages().get(language)
                    : null;
            int effectiveTimeout = langLimit != null ? langLimit.timeoutSeconds() : sandboxConfig.timeout();

            long startTime = System.nanoTime();
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            process.getOutputStream().write(inputsJson.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            boolean finished = process.waitFor(effectiveTimeout, java.util.concurrent.TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                return helper.buildCaseResult(testCase, runId, userId, "Time Limit Exceeded",
                        elapsedMs, null, "Execution timed out after " + effectiveTimeout + "s", 0.0);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                if (isSandboxForkFailure(stdout)) {
                    log.warn("Sandbox fork failure detected for language={} runId={} detail={}",
                            language, runId, truncateForLog(helper.sanitizeSandboxOutput(stdout)));
                    return helper.buildCaseResult(testCase, runId, userId, SANDBOX_FORK_FAILURE_VERDICT,
                            elapsedMs, null,
                            SANDBOX_FORK_FAILURE_DETAIL_PREFIX + helper.sanitizeSandboxOutput(stdout), 0.0);
                }
                return helper.buildCaseResult(testCase, runId, userId, "Runtime Error",
                        elapsedMs, null, helper.sanitizeSandboxOutput(stdout), 0.0);
            }

            String expected = testCase.getOutput() != null ? testCase.getOutput().trim() : "";
            boolean passed = helper.normalizeOutput(stdout).equals(helper.normalizeOutput(expected));

            return helper.buildCaseResult(testCase, runId, userId,
                    passed ? "Accepted" : "Wrong Answer",
                    elapsedMs, stdout, null, 0.0);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sandbox execution interrupted for language={}", language, e);
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "Sandbox execution interrupted");
        } catch (IOException e) {
            log.error("Sandbox execution I/O failed for language={}", language, e);
            String detail = e.getMessage();
            if (detail != null && detail.contains("Unable to find image")) {
                throw new BusinessException(ErrorCode.SANDBOX_IMAGE_NOT_FOUND,
                        "Sandbox image '" + sandboxConfig.image() + "' not found. Build it first: docker build -t "
                                + sandboxConfig.image() + " -f docker/sandbox/Dockerfile docker/sandbox/");
            }
            if (isDockerDaemonForkFailure(detail)) {
                throw new BusinessException(ErrorCode.SANDBOX_ERROR,
                        "Sandbox daemon-level fork failure (likely PID/cgroup pressure): " + detail);
            }
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "Sandbox execution failed: " + detail);
        }
    }

    @Override
    public List<RunResultDTO.RunCaseResult> executeBatch(String language, String code,
                                                        List<RunSubmissionDTO.RunTestCase> testCases,
                                                        String runId, String userId) {
        if (useDForm(language)) {
            return executeBatchD(language, code, testCases, runId, userId);
        }
        try {
            String testCasesJson = helper.buildBatchInputsJson(testCases);
            String wrapperScript = helper.buildWrapperScript(language, code, testCases);
            List<String> command = buildBatchDockerCommand(language, wrapperScript);

            DockerSandboxConfig.LanguageLimit langLimit = sandboxConfig.languages() != null
                    ? sandboxConfig.languages().get(language)
                    : null;
            int effectiveTimeout = langLimit != null ? langLimit.timeoutSeconds() : sandboxConfig.timeout();

            long startTime = System.nanoTime();
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            process.getOutputStream().write(testCasesJson.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            boolean finished = process.waitFor(effectiveTimeout, java.util.concurrent.TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                return testCases.stream()
                        .map(tc -> helper.buildCaseResult(tc, runId, userId, "Time Limit Exceeded",
                                elapsedMs / testCases.size(), null,
                                "Batch execution timed out after " + effectiveTimeout + "s", 0.0))
                        .collect(Collectors.toList());
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                if (isSandboxForkFailure(stdout)) {
                    log.warn("Sandbox fork failure detected in batch for language={} runId={} cases={} detail={}",
                            language, runId, testCases.size(),
                            truncateForLog(helper.sanitizeSandboxOutput(stdout)));
                    return testCases.stream()
                            .map(tc -> helper.buildCaseResult(tc, runId, userId, SANDBOX_FORK_FAILURE_VERDICT,
                                    elapsedMs / testCases.size(), null,
                                    SANDBOX_FORK_FAILURE_DETAIL_PREFIX + helper.sanitizeSandboxOutput(stdout), 0.0))
                            .collect(Collectors.toList());
                }
                return testCases.stream()
                        .map(tc -> helper.buildCaseResult(tc, runId, userId, "Runtime Error",
                                elapsedMs / testCases.size(), null, helper.sanitizeSandboxOutput(stdout), 0.0))
                        .collect(Collectors.toList());
            }

            return helper.parseBatchResults(stdout, testCases, runId, userId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "Batch execution interrupted");
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (isDockerDaemonForkFailure(msg)) {
                throw new BusinessException(ErrorCode.SANDBOX_ERROR,
                        "Sandbox daemon-level fork failure (likely PID/cgroup pressure): " + msg);
            }
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "Batch execution failed: " + msg);
        }
    }

    @Override
    public List<String> buildDockerCommand(String language, String code) {
        DockerSandboxConfig.LanguageLimit langLimit = sandboxConfig.languages() != null
                ? sandboxConfig.languages().get(language)
                : null;
        String effectiveMemory = langLimit != null ? langLimit.memory() : sandboxConfig.memory();
        int effectiveTimeout = langLimit != null ? langLimit.timeoutSeconds() : sandboxConfig.timeout();

        // SECURITY INVARIANT (H1 review): `--cap-drop ALL` and `--user 1000:1000` MUST stay in this
        // list. They are the ONLY line of defense against multi-flag clone() bypass
        // (e.g. clone(CLONE_NEWNS|CLONE_NEWPID)) because Docker 29.x + libseccomp cannot
        // express OR-semantics for clone() via SCMP_CMP_MASKED_EQ (see seccomp-profile.json
        // _comments). CAP_SYS_ADMIN absence (from --cap-drop ALL) is what blocks kernel-side
        // namespace creation; SandboxForkE2EIT.multiFlag_clone_isBlockedByCapabilityGate
        // guards this dependency. Removing these flags silently re-enables namespace creation.
        List<String> cmd = new ArrayList<>(List.of(
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
                "--volume", resolveSeccompProfileDirectoryPath() + ":/seccomp-profile:ro",
                sandboxConfig.image()
        ));

        switch (language) {
            case "javascript" -> cmd.addAll(List.of("node", "-e", helper.wrapJavaScript(code)));
            case "python" -> cmd.addAll(List.of("python3", "-c", helper.wrapPython(code)));
            case "java" -> {
                String wrapped = helper.wrapJava(code);
                String b64 = Base64.getEncoder().encodeToString(wrapped.getBytes(StandardCharsets.UTF_8));
                cmd.addAll(List.of("sh", "-c",
                        "echo '" + b64 + "' | base64 -d > /tmp/Main.java && javac /tmp/Main.java && java -cp /tmp Main"));
            }
            case "c" -> cmd.addAll(List.of("sh", "-c",
                    "cat > /tmp/solution.c && gcc -o /tmp/solution /tmp/solution.c && /tmp/solution"));
            case "cpp" -> cmd.addAll(List.of("sh", "-c",
                    "cat > /tmp/solution.cpp && g++ -o /tmp/solution /tmp/solution.cpp && /tmp/solution"));
            default -> throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED);
        }
        return cmd;
    }

    @Override
    public List<String> buildBatchDockerCommand(String language, String wrapperScript) {
        DockerSandboxConfig.LanguageLimit langLimit = sandboxConfig.languages() != null
                ? sandboxConfig.languages().get(language)
                : null;
        String effectiveMemory = langLimit != null ? langLimit.memory() : sandboxConfig.memory();

        List<String> cmd = new ArrayList<>(List.of(
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
                "--volume", resolveSeccompProfileDirectoryPath() + ":/seccomp-profile:ro",
                sandboxConfig.image()
        ));

        switch (language) {
            case "javascript" -> cmd.addAll(List.of("node", "-e", wrapperScript));
            case "python" -> cmd.addAll(List.of("python3", "-c", wrapperScript));
            case "java", "c", "cpp" -> cmd.addAll(List.of("sh", "-c", wrapperScript));
            default -> throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED);
        }
        return cmd;
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

    // ── D-form dispatch (LeetCode/HackerRank harness) ────────────────────────
    // The legacy executeInSandbox / executeBatch above builds a per-request
    // bash wrapper via helper.buildWrapperScript. D-form instead pre-compiles
    // the harness into the sandbox image (docker/sandbox/Dockerfile Phase 2
    // build) and ships a static input.json. These methods are the dispatcher.

    /** Gate the D-form path. Both the global flag and the language support set must agree. */
    private boolean useDForm(String language) {
        return sandboxConfig.dFormEnabled()
                && CodeExecutionHelper.DFORM_SUPPORTED_LANGUAGES.contains(language);
    }

    /** Maps a language to the file name the harness expects in /job/. */
    private static String dFormSolutionFileName(String language) {
        return switch (language) {
            case "java" -> "Solution.java";
            case "python" -> "solution.py";   // lowercase: harness does `import solution`
            case "c" -> "Solution.c";
            case "cpp" -> "Solution.cpp";
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

    /** Hard timeout the docker container's wall clock. Soft TLE is per-case inside the harness. */
    private int dFormHardTimeoutSeconds() {
        DockerSandboxConfig.LanguageLimit langLimit = sandboxConfig.languages() != null
                ? sandboxConfig.languages().get("dform")
                : null;
        if (langLimit != null) return langLimit.timeoutSeconds();
        // Fall back to Java's per-language budget (typically the longest). D-form's
        // hard kill is process-level; a per-case TLE will be reported by the
        // harness before this fires.
        DockerSandboxConfig.LanguageLimit javaLimit = sandboxConfig.languages() != null
                ? sandboxConfig.languages().get("java")
                : null;
        if (javaLimit != null) return javaLimit.timeoutSeconds();
        return sandboxConfig.timeout();
    }

    /** Per-case soft timeout forwarded to the harness (Thread.interrupt inside the worker). */
    private long dFormPerCaseTimeoutMs() {
        // Conservative default: 1s. Tunable via a future code-execution.sandbox.d-form
        // config field if/when needed.
        return 1_000L;
    }

    /** Per-language `sh -c` body that compiles + runs the user code via the pre-compiled harness. */
    private String dFormDispatchShell(String language, String harnessRoot) {
        return switch (language) {
            case "java" -> "cd /job && javac -cp " + harnessRoot + "/java -d . "
                    + dFormSolutionFileName(language)
                    + " && java -cp " + harnessRoot + "/java:. Main /job/input.json";
            case "python" -> "cd /job && SOLUTION_DIR=/job python3 "
                    + harnessRoot + "/python/main.py /job/input.json";
            case "c" -> "cd /job && gcc -O2 -o /tmp/sol " + dFormSolutionFileName(language)
                    + " && /tmp/sol /job/input.json";
            case "cpp" -> "cd /job && g++ -O2 -std=c++17 -o /tmp/sol " + dFormSolutionFileName(language)
                    + " && /tmp/sol /job/input.json";
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

    /** Single-case D-form dispatch. */
    private RunResultDTO.RunCaseResult executeInSandboxD(String language, String code,
                                                        RunSubmissionDTO.RunTestCase testCase,
                                                        String runId, String userId) {
        Path jobDir = null;
        try {
            String inputJson = helper.buildDInputsJson(testCase, dFormPerCaseTimeoutMs());
            jobDir = materializeDFormJob(language, code, inputJson, runId);
            List<String> command = buildDDockerCommand(language, jobDir);
            int hardTimeout = dFormHardTimeoutSeconds();
            long startTime = System.nanoTime();
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(hardTimeout, java.util.concurrent.TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            if (!finished) {
                process.destroyForcibly();
                return helper.buildCaseResult(testCase, runId, userId, "Time Limit Exceeded",
                        elapsedMs, null, "D-form dispatch timed out after " + hardTimeout + "s", 0.0);
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();
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
                    helper.parseDEnvelope(stdout, java.util.List.of(testCase), runId, userId);
            return results.isEmpty()
                    ? helper.buildCaseResult(testCase, runId, userId, "Runtime Error",
                            elapsedMs, null, "D-form envelope empty", 0.0)
                    : results.get(0);
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
        } finally {
            if (jobDir != null) {
                try {
                    Files.walk(jobDir)
                            .sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                } catch (IOException ignored) { /* best-effort cleanup */ }
            }
        }
    }

    /** Multi-case D-form dispatch. */
    private List<RunResultDTO.RunCaseResult> executeBatchD(String language, String code,
                                                           List<RunSubmissionDTO.RunTestCase> testCases,
                                                           String runId, String userId) {
        Path jobDir = null;
        try {
            String inputJson = helper.buildDBatchInputsJson(testCases, dFormPerCaseTimeoutMs());
            jobDir = materializeDFormJob(language, code, inputJson, runId);
            List<String> command = buildDDockerCommand(language, jobDir);
            int hardTimeout = dFormHardTimeoutSeconds();
            long startTime = System.nanoTime();
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(hardTimeout, java.util.concurrent.TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            if (!finished) {
                process.destroyForcibly();
                return testCases.stream()
                        .map(tc -> helper.buildCaseResult(tc, runId, userId, "Time Limit Exceeded",
                                elapsedMs / testCases.size(), null,
                                "D-form batch dispatch timed out after " + hardTimeout + "s", 0.0))
                        .collect(Collectors.toList());
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                if (isSandboxForkFailure(stdout)) {
                    return testCases.stream()
                            .map(tc -> helper.buildCaseResult(tc, runId, userId, SANDBOX_FORK_FAILURE_VERDICT,
                                    elapsedMs / testCases.size(), null,
                                    SANDBOX_FORK_FAILURE_DETAIL_PREFIX + helper.sanitizeSandboxOutput(stdout), 0.0))
                            .collect(Collectors.toList());
                }
                return testCases.stream()
                        .map(tc -> helper.buildCaseResult(tc, runId, userId, "Runtime Error",
                                elapsedMs / testCases.size(), null, helper.sanitizeSandboxOutput(stdout), 0.0))
                        .collect(Collectors.toList());
            }
            return helper.parseDEnvelope(stdout, testCases, runId, userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "D-form batch dispatch interrupted");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "D-form batch dispatch failed: " + e.getMessage());
        } finally {
            if (jobDir != null) {
                try {
                    Files.walk(jobDir)
                            .sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                } catch (IOException ignored) { /* best-effort cleanup */ }
            }
        }
    }
}
