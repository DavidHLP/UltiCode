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

    @Override
    public RunResultDTO.RunCaseResult executeInSandbox(String language, String code,
                                                      RunSubmissionDTO.RunTestCase testCase,
                                                      String runId, String userId) {
        try {
            String inputsJson = helper.buildInputsJson(testCase);
            List<String> command = buildDockerCommand(language, code);

            long startTime = System.nanoTime();
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            process.getOutputStream().write(inputsJson.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            boolean finished = process.waitFor(sandboxConfig.timeout(), java.util.concurrent.TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                return helper.buildCaseResult(testCase, runId, userId, "Time Limit Exceeded",
                        elapsedMs, null, "Execution timed out after " + sandboxConfig.timeout() + "s", 0.0);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();

            if (exitCode != 0) {
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
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "Sandbox execution failed: " + detail);
        }
    }

    @Override
    public List<RunResultDTO.RunCaseResult> executeBatch(String language, String code,
                                                        List<RunSubmissionDTO.RunTestCase> testCases,
                                                        String runId, String userId) {
        try {
            String testCasesJson = helper.buildBatchInputsJson(testCases);
            String wrapperScript = helper.buildWrapperScript(language, code, testCases);
            List<String> command = buildBatchDockerCommand(language, wrapperScript);

            long startTime = System.nanoTime();
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            process.getOutputStream().write(testCasesJson.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            boolean finished = process.waitFor(sandboxConfig.timeout(), java.util.concurrent.TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                return testCases.stream()
                        .map(tc -> helper.buildCaseResult(tc, runId, userId, "Time Limit Exceeded",
                                elapsedMs / testCases.size(), null,
                                "Batch execution timed out after " + sandboxConfig.timeout() + "s", 0.0))
                        .collect(Collectors.toList());
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();

            if (exitCode != 0) {
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
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "Batch execution failed: " + e.getMessage());
        }
    }

    @Override
    public List<String> buildDockerCommand(String language, String code) {
        List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "--rm", "-i",
                "--network", "none",
                "--cap-drop", "ALL",
                "--memory", sandboxConfig.memory(),
                "--cpus", sandboxConfig.cpus(),
                "--pids-limit", String.valueOf(sandboxConfig.pidsLimit()),
                "--ulimit", "nofile=128:128",
                "--read-only",
                "--tmpfs", "/tmp:rw,exec,size=64m",
                "--user", "1000:1000",
                "--security-opt", "no-new-privileges:true",
                "--security-opt", "seccomp=" + sandboxConfig.seccompProfilePath(),
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
        return new ArrayList<>(List.of(
                "docker", "run", "--rm", "-i",
                "--network", "none",
                "--cap-drop", "ALL",
                "--memory", sandboxConfig.memory(),
                "--cpus", sandboxConfig.cpus(),
                "--pids-limit", String.valueOf(sandboxConfig.pidsLimit()),
                "--ulimit", "nofile=128:128",
                "--read-only",
                "--tmpfs", "/tmp:rw,exec,size=64m",
                "--user", "1000:1000",
                "--security-opt", "no-new-privileges:true",
                "--security-opt", "seccomp=" + sandboxConfig.seccompProfilePath(),
                sandboxConfig.image(),
                "sh", "-c", wrapperScript
        ));
    }
}
