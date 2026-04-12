package com.ulticode.modules.submission.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
            "javascript", "python", "java", "c", "cpp"
    );

    private static final Map<String, String> LANGUAGE_RUNNERS = Map.of(
            "javascript", "node",
            "python", "python3",
            "java", "java",
            "c", "gcc",
            "cpp", "g++"
    );

    private final DockerSandboxConfig sandboxConfig;

    public RunResultDTO execute(RunSubmissionDTO request, Long problemId, String userId) {
        String language = request.getLanguage().toLowerCase().trim();

        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED,
                    "Unsupported language: " + language + ". Supported: " + SUPPORTED_LANGUAGES);
        }

        List<RunSubmissionDTO.RunTestCase> testCases = request.getTestCases();
        if (testCases == null || testCases.isEmpty()) {
            return emptyResult(problemId, userId);
        }

        String runId = UUID.randomUUID().toString();
        List<RunResultDTO.RunCaseResult> results = new ArrayList<>();
        int passedCases = 0;

        for (RunSubmissionDTO.RunTestCase testCase : testCases) {
            RunResultDTO.RunCaseResult caseResult = sandboxConfig.enabled()
                    ? executeInSandbox(language, request.getCode(), testCase, runId, userId)
                    : executeDirect(language, request.getCode(), testCase, runId, userId);
            results.add(caseResult);
            if ("Accepted".equals(caseResult.getStatus())) {
                passedCases++;
            }
        }

        String verdict = passedCases == testCases.size() ? "Accepted" : "Wrong Answer";
        long totalRuntimeMs = results.stream()
                .mapToLong(r -> parseRuntimeMs(r.getRuntime()))
                .sum();

        return RunResultDTO.builder()
                .id(runId)
                .problemId(String.valueOf(problemId))
                .userId(userId)
                .verdict(verdict)
                .runtime(totalRuntimeMs + "ms")
                .memory("0KB")
                .cases(results)
                .passedCases(passedCases)
                .totalCases(testCases.size())
                .build();
    }

    // ==================== Docker Sandbox Execution ====================

    private RunResultDTO.RunCaseResult executeInSandbox(String language, String code,
                                                         RunSubmissionDTO.RunTestCase testCase,
                                                         String runId, String userId) {
        try {
            String inputsJson = buildInputsJson(testCase);
            List<String> command = buildDockerCommand(language, code);

            long startTime = System.nanoTime();
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            process.getOutputStream().write(inputsJson.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            boolean finished = process.waitFor(sandboxConfig.timeout(), TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                return buildCaseResult(testCase, runId, userId, "Time Limit Exceeded",
                        elapsedMs, null, "Execution timed out after " + sandboxConfig.timeout() + "s");
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                return buildCaseResult(testCase, runId, userId, "Runtime Error",
                        elapsedMs, null, sanitizeSandboxOutput(stdout));
            }

            String expected = testCase.getOutput() != null ? testCase.getOutput().trim() : "";
            boolean passed = normalizeOutput(stdout).equals(normalizeOutput(expected));

            return buildCaseResult(testCase, runId, userId,
                    passed ? "Accepted" : "Wrong Answer",
                    elapsedMs, stdout, null);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sandbox execution failed for language={}", language, e);
            String detail = e.getMessage();
            if (detail != null && detail.contains("Unable to find image")) {
                throw new BusinessException(ErrorCode.SANDBOX_IMAGE_NOT_FOUND,
                        "Sandbox image '" + sandboxConfig.image() + "' not found. Build it first: docker build -t "
                                + sandboxConfig.image() + " -f docker/sandbox/Dockerfile docker/sandbox/");
            }
            throw new BusinessException(ErrorCode.SANDBOX_ERROR,
                    "Sandbox execution failed: " + detail);
        }
    }

    private List<String> buildDockerCommand(String language, String code) {
        List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "--rm", "-i",
                "--network", "none",
                "--memory", sandboxConfig.memory(),
                "--cpus", sandboxConfig.cpus(),
                "--pids-limit", String.valueOf(sandboxConfig.pidsLimit()),
                "--ulimit", "nofile=128:128",
                "--read-only",
                "--tmpfs", "/tmp:rw,size=64m",
                "--user", "1000:1000",
                "--security-opt", "no-new-privileges:true",
                sandboxConfig.image()
        ));

        switch (language) {
            case "javascript" -> {
                String wrapped = wrapJavaScript(code);
                cmd.addAll(List.of("node", "-e", wrapped));
            }
            case "python" -> {
                String wrapped = wrapPython(code);
                cmd.addAll(List.of("python3", "-c", wrapped));
            }
            case "java" -> {
                String wrapped = wrapJava(code);
                cmd.addAll(List.of("sh", "-c",
                        "echo '" + escapeSingleQuote(wrapped) + "' > /tmp/Main.java && "
                                + "javac /tmp/Main.java && java -cp /tmp Main"));
            }
            case "c" -> {
                cmd.addAll(List.of("sh", "-c",
                        "cat > /tmp/solution.c && gcc -o /tmp/solution /tmp/solution.c && /tmp/solution"));
            }
            case "cpp" -> {
                cmd.addAll(List.of("sh", "-c",
                        "cat > /tmp/solution.cpp && g++ -o /tmp/solution /tmp/solution.cpp && /tmp/solution"));
            }
            default -> throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED);
        }

        return cmd;
    }

    private String escapeSingleQuote(String s) {
        return s.replace("'", "'\\''");
    }

    // ==================== Direct ProcessBuilder Execution (Fallback) ====================

    private RunResultDTO.RunCaseResult executeDirect(String language, String code,
                                                     RunSubmissionDTO.RunTestCase testCase,
                                                     String runId, String userId) {
        try {
            String runnerCmd = LANGUAGE_RUNNERS.get(language);
            List<String> command = buildDirectCommand(runnerCmd, language, code);
            String inputsJson = buildInputsJson(testCase);

            long startTime = System.nanoTime();
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            process.getOutputStream().write(inputsJson.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            boolean finished = process.waitFor(sandboxConfig.timeout(), TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                return buildCaseResult(testCase, runId, userId, "Time Limit Exceeded",
                        elapsedMs, null, "Execution timed out after " + sandboxConfig.timeout() + "s");
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                return buildCaseResult(testCase, runId, userId, "Runtime Error",
                        elapsedMs, null, stdout);
            }

            String expected = testCase.getOutput() != null ? testCase.getOutput().trim() : "";
            boolean passed = normalizeOutput(stdout).equals(normalizeOutput(expected));

            return buildCaseResult(testCase, runId, userId,
                    passed ? "Accepted" : "Wrong Answer",
                    elapsedMs, stdout, null);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Direct execution failed for language={}", language, e);
            return buildCaseResult(testCase, runId, userId, "Runtime Error",
                    0, null, e.getMessage());
        }
    }

    private List<String> buildDirectCommand(String runner, String language, String code) {
        return switch (language) {
            case "javascript" -> List.of(runner, "-e", wrapJavaScript(code));
            case "python" -> List.of(runner, "-c", wrapPython(code));
            case "java" -> List.of(runner, wrapJava(code));
            default -> throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED);
        };
    }

    // ==================== Code Wrappers ====================

    private String wrapJavaScript(String code) {
        String funcName = extractFunctionName(code, "function ");
        return """
                %s
                const input = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));
                const result = %s(...input);
                process.stdout.write(JSON.stringify(result));
                """.formatted(code, funcName);
    }

    private String wrapPython(String code) {
        String funcName = extractFunctionName(code, "def ");
        return """
                import json, sys
                %s
                input_data = json.loads(sys.stdin.read())
                result = %s(*input_data)
                print(json.dumps(result))
                """.formatted(code, funcName);
    }

    private String wrapJava(String code) {
        String funcName = extractFunctionName(code, " ");
        return """
                import java.util.*;
                public class Main {
                    public static void main(String[] args) throws Exception {
                        Scanner sc = new Scanner(System.in);
                        StringBuilder sb = new StringBuilder();
                        while (sc.hasNextLine()) sb.append(sc.nextLine());
                        String input = sb.toString();
                        input = input.substring(1, input.length() - 1);
                        String[] parts = input.split(",");
                        %s
                        System.out.print(result);
                    }
                }
                """.formatted(code);
    }

    private String extractFunctionName(String code, String keyword) {
        int idx = code.indexOf(keyword);
        if (idx < 0) {
            return "solution";
        }
        int start = idx + keyword.length();
        while (start < code.length() && Character.isWhitespace(code.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < code.length() && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '_')) {
            end++;
        }
        if (end == start) {
            return "solution";
        }
        return code.substring(start, end);
    }

    // ==================== Utilities ====================

    private String buildInputsJson(RunSubmissionDTO.RunTestCase testCase) {
        if (testCase.getInputs() == null || testCase.getInputs().isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < testCase.getInputs().size(); i++) {
            RunSubmissionDTO.RunInput input = testCase.getInputs().get(i);
            if (i > 0) json.append(",");
            json.append(parseInputValue(input.getValue()));
        }
        json.append("]");
        return json.toString();
    }

    private String parseInputValue(String value) {
        if (value == null) return "null";
        value = value.trim();
        if (value.equals("true") || value.equals("false")) {
            return value;
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            return value;
        }
        try {
            Double.parseDouble(value);
            return value;
        } catch (NumberFormatException e) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }

    private String normalizeOutput(String output) {
        if (output == null) return "";
        return output.trim()
                .replaceAll("\\s+", " ")
                .replaceAll(",\\s*}", "}")
                .replaceAll(",\\s*]", "]");
    }

    private long parseRuntimeMs(String runtime) {
        if (runtime == null || !runtime.endsWith("ms")) return 0;
        try {
            return Long.parseLong(runtime.replace("ms", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String sanitizeSandboxOutput(String output) {
        if (output == null) return "Runtime error";
        // Trim Docker noise but keep useful error info
        String[] lines = output.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // Skip Docker-specific noise
            if (trimmed.contains("OCI runtime") || trimmed.contains("docker")) continue;
            sb.append(trimmed).append("\n");
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "Runtime error" : result;
    }

    private RunResultDTO emptyResult(Long problemId, String userId) {
        return RunResultDTO.builder()
                .id(UUID.randomUUID().toString())
                .problemId(String.valueOf(problemId))
                .userId(userId)
                .verdict("Accepted")
                .runtime("0ms")
                .memory("0KB")
                .cases(List.of())
                .passedCases(0)
                .totalCases(0)
                .build();
    }

    private RunResultDTO.RunCaseResult buildCaseResult(RunSubmissionDTO.RunTestCase testCase,
                                                        String runId, String userId,
                                                        String status, long runtimeMs,
                                                        String output, String detail) {
        List<RunResultDTO.RunCaseResult.InputParam> inputs = null;
        if (testCase.getInputs() != null) {
            inputs = testCase.getInputs().stream()
                    .map(i -> RunResultDTO.RunCaseResult.InputParam.builder()
                            .id(i.getId())
                            .label(i.getLabel())
                            .name(i.getName())
                            .value(i.getValue())
                            .build())
                    .toList();
        }

        return RunResultDTO.RunCaseResult.builder()
                .id(UUID.randomUUID().toString())
                .runId(runId)
                .submissionTestId(testCase.getId())
                .testCaseId(testCase.getId())
                .caseLabel(testCase.getLabel() != null ? testCase.getLabel() : testCase.getId())
                .status(status)
                .runtime(runtimeMs + "ms")
                .memory("0KB")
                .output(output)
                .expectedOutput(testCase.getOutput())
                .detail(detail)
                .inputs(inputs)
                .build();
    }
}
