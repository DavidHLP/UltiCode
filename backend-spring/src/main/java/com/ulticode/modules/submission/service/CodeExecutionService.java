package com.ulticode.modules.submission.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
            "javascript", "python", "java", "c", "cpp"
    );

    private final DockerSandboxConfig sandboxConfig;
    private final ObjectMapper objectMapper;

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

        if (!sandboxConfig.enabled()) {
            throw new BusinessException(ErrorCode.SANDBOX_ERROR,
                    "Code execution is disabled: sandbox mode is required");
        }

        if (testCases.size() == 1) {
            // Single test case: use existing per-case method (no overhead difference)
            RunResultDTO.RunCaseResult caseResult = executeInSandbox(language, request.getCode(), testCases.get(0), runId, userId);
            results.add(caseResult);
            if ("Accepted".equals(caseResult.getStatus())) {
                passedCases++;
            }
        } else {
            // Multiple test cases: batch execution in single container
            results = executeBatch(language, request.getCode(), testCases, runId, userId);
            passedCases = (int) results.stream()
                    .filter(r -> "Accepted".equals(r.getStatus()))
                    .count();
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
                .memory(results.stream()
                        .map(RunResultDTO.RunCaseResult::getMemory)
                        .max(String::compareTo)
                        .orElse("0.0MB"))
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
                        elapsedMs, null, "Execution timed out after " + sandboxConfig.timeout() + "s", 0.0);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                return buildCaseResult(testCase, runId, userId, "Runtime Error",
                        elapsedMs, null, sanitizeSandboxOutput(stdout), 0.0);
            }

            String expected = testCase.getOutput() != null ? testCase.getOutput().trim() : "";
            boolean passed = normalizeOutput(stdout).equals(normalizeOutput(expected));

            return buildCaseResult(testCase, runId, userId,
                    passed ? "Accepted" : "Wrong Answer",
                    elapsedMs, stdout, null, 0.0);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sandbox execution interrupted for language={}", language, e);
            throw new BusinessException(ErrorCode.SANDBOX_ERROR,
                    "Sandbox execution interrupted");
        } catch (IOException e) {
            log.error("Sandbox execution I/O failed for language={}", language, e);
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
                // Use base64 encoding to avoid shell injection via user code
                String b64 = Base64.getEncoder().encodeToString(
                        wrapped.getBytes(StandardCharsets.UTF_8));
                cmd.addAll(List.of("sh", "-c",
                        "echo '" + b64 + "' | base64 -d > /tmp/Main.java && "
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

    // ==================== Batch Docker Execution ====================

    /**
     * Execute all test cases in a single Docker container.
     * Generates a wrapper script that processes each test case sequentially.
     */
    private List<RunResultDTO.RunCaseResult> executeBatch(
            String language, String code,
            List<RunSubmissionDTO.RunTestCase> testCases,
            String runId, String userId) {

        try {
            String testCasesJson = buildBatchInputsJson(testCases);
            String wrapperScript = buildWrapperScript(language, code, testCases);
            List<String> command = buildBatchDockerCommand(language, wrapperScript);

            long startTime = System.nanoTime();
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Send all test case inputs via stdin
            process.getOutputStream().write(testCasesJson.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            boolean finished = process.waitFor(sandboxConfig.timeout(), TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                return testCases.stream()
                        .map(tc -> buildCaseResult(tc, runId, userId, "Time Limit Exceeded",
                                elapsedMs / testCases.size(), null,
                                "Batch execution timed out after " + sandboxConfig.timeout() + "s", 0.0))
                        .collect(Collectors.toList());
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                return testCases.stream()
                        .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                                elapsedMs / testCases.size(), null, sanitizeSandboxOutput(stdout), 0.0))
                        .collect(Collectors.toList());
            }

            return parseBatchResults(stdout, testCases, runId, userId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "Batch execution interrupted");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SANDBOX_ERROR, "Batch execution failed: " + e.getMessage());
        }
    }

    private String buildWrapperScript(String language, String code,
                                       List<RunSubmissionDTO.RunTestCase> testCases) {
        return switch (language) {
            case "javascript" -> buildJavaScriptBatchWrapper(code, testCases);
            case "python" -> buildPythonBatchWrapper(code, testCases);
            case "java" -> buildJavaBatchWrapper(code, testCases);
            case "c" -> buildCBatchWrapper(code, testCases);
            case "cpp" -> buildCppBatchWrapper(code, testCases);
            default -> throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED);
        };
    }

    private String buildJavaScriptBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        String funcName = extractFunctionName(code, "function ");
        return code + "\n" +
                "const input = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));\n" +
                "const results = input.map(args => {\n" +
                "  const start = Date.now();\n" +
                "  try {\n" +
                "    const result = " + funcName + "(...args);\n" +
                "    const mem = require('fs').readFileSync('/sys/fs/cgroup/memory.current', 'utf8').trim();\n" +
                "    return {output: JSON.stringify(result), runtime: Date.now() - start, status: 'ok', memory: parseInt(mem)};\n" +
                "  } catch(e) {\n" +
                "    return {output: e.message, runtime: Date.now() - start, status: 'error', memory: 0};\n" +
                "  }\n" +
                "});\n" +
                "process.stdout.write(JSON.stringify(results));\n";
    }

    private String buildPythonBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        String funcName = extractFunctionName(code, "def ");
        return "import json, sys, time\n" +
                code + "\n" +
                "input_data = json.loads(sys.stdin.read())\n" +
                "results = []\n" +
                "for args in input_data:\n" +
                "    start = time.time() * 1000\n" +
                "    try:\n" +
                "        result = " + funcName + "(*args)\n" +
                "        elapsed = time.time() * 1000 - start\n" +
                "        with open('/sys/fs/cgroup/memory.current') as f:\n" +
                "            mem = int(f.read().strip())\n" +
                "        results.append({'output': json.dumps(result), 'runtime': int(elapsed), 'status': 'ok', 'memory': mem})\n" +
                "    except Exception as e:\n" +
                "        elapsed = time.time() * 1000 - start\n" +
                "        results.append({'output': str(e), 'runtime': int(elapsed), 'status': 'error', 'memory': 0})\n" +
                "print(json.dumps(results))\n";
    }

    private String buildCBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        int perCaseTimeout = sandboxConfig.timeout() / Math.max(testCases.size(), 1);
        return "cat > /tmp/solution.c && gcc -o /tmp/solution /tmp/solution.c && " +
                "cat | python3 -c \"" +
                "import json,sys,subprocess,time\\n" +
                "inputs=json.loads(sys.stdin.read())\\n" +
                "results=[]\\n" +
                "for args in inputs:\\n" +
                "  start=time.time()*1000\\n" +
                "  try:\\n" +
                "    p=subprocess.run(['/tmp/solution'],input=json.dumps(args),capture_output=True,text=True,timeout=" + perCaseTimeout + ")\\n" +
                "    elapsed=time.time()*1000-start\\n" +
                "    try:\\n" +
                "      with open('/sys/fs/cgroup/memory.current') as f:\\n" +
                "        mem=int(f.read().strip())\\n" +
                "    except:\\n" +
                "      mem=0\\n" +
                "    results.append({'output':p.stdout.strip(),'runtime':int(elapsed),'status':'ok' if p.returncode==0 else 'error','memory':mem})\\n" +
                "  except subprocess.TimeoutExpired:\\n" +
                "    results.append({'output':'','runtime':" + perCaseTimeout * 1000 + ",'status':'timeout','memory':0})\\n" +
                "  except Exception as e:\\n" +
                "    results.append({'output':str(e),'runtime':0,'status':'error','memory':0})\\n" +
                "print(json.dumps(results))\"";
    }

    private String buildCppBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        int perCaseTimeout = sandboxConfig.timeout() / Math.max(testCases.size(), 1);
        return "cat > /tmp/solution.cpp && g++ -o /tmp/solution /tmp/solution.cpp && " +
                "cat | python3 -c \"" +
                "import json,sys,subprocess,time\\n" +
                "inputs=json.loads(sys.stdin.read())\\n" +
                "results=[]\\n" +
                "for args in inputs:\\n" +
                "  start=time.time()*1000\\n" +
                "  try:\\n" +
                "    p=subprocess.run(['/tmp/solution'],input=json.dumps(args),capture_output=True,text=True,timeout=" + perCaseTimeout + ")\\n" +
                "    elapsed=time.time()*1000-start\\n" +
                "    try:\\n" +
                "      with open('/sys/fs/cgroup/memory.current') as f:\\n" +
                "        mem=int(f.read().strip())\\n" +
                "    except:\\n" +
                "      mem=0\\n" +
                "    results.append({'output':p.stdout.strip(),'runtime':int(elapsed),'status':'ok' if p.returncode==0 else 'error','memory':mem})\\n" +
                "  except subprocess.TimeoutExpired:\\n" +
                "    results.append({'output':'','runtime':" + perCaseTimeout * 1000 + ",'status':'timeout','memory':0})\\n" +
                "  except Exception as e:\\n" +
                "    results.append({'output':str(e),'runtime':0,'status':'error','memory':0})\\n" +
                "print(json.dumps(results))\"";
    }

    private String buildJavaBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        String b64 = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
        int perCaseTimeout = sandboxConfig.timeout() / Math.max(testCases.size(), 1);
        return "echo '" + b64 + "' | base64 -d > /tmp/Main.java && javac /tmp/Main.java && " +
                "cat | python3 -c \"" +
                "import json,sys,subprocess,time\\n" +
                "inputs=json.loads(sys.stdin.read())\\n" +
                "results=[]\\n" +
                "for args in inputs:\\n" +
                "  start=time.time()*1000\\n" +
                "  try:\\n" +
                "    p=subprocess.run(['java','-cp','/tmp','Main'],input=json.dumps(args),capture_output=True,text=True,timeout=" + perCaseTimeout + ")\\n" +
                "    elapsed=time.time()*1000-start\\n" +
                "    try:\\n" +
                "      with open('/sys/fs/cgroup/memory.current') as f:\\n" +
                "        mem=int(f.read().strip())\\n" +
                "    except:\\n" +
                "      mem=0\\n" +
                "    results.append({'output':p.stdout.strip(),'runtime':int(elapsed),'status':'ok' if p.returncode==0 else 'error','memory':mem})\\n" +
                "  except subprocess.TimeoutExpired:\\n" +
                "    results.append({'output':'','runtime':" + perCaseTimeout * 1000 + ",'status':'timeout','memory':0})\\n" +
                "  except Exception as e:\\n" +
                "    results.append({'output':str(e),'runtime':0,'status':'error','memory':0})\\n" +
                "print(json.dumps(results))\"";
    }

    private List<String> buildBatchDockerCommand(String language, String wrapperScript) {
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

    private String buildBatchInputsJson(List<RunSubmissionDTO.RunTestCase> testCases) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < testCases.size(); i++) {
            if (i > 0) json.append(",");
            json.append(buildInputsJson(testCases.get(i)));
        }
        json.append("]");
        return json.toString();
    }

    private List<RunResultDTO.RunCaseResult> parseBatchResults(
            String stdout, List<RunSubmissionDTO.RunTestCase> testCases,
            String runId, String userId) {
        try {
            // Extract JSON array from stdout (may contain compilation output before the JSON)
            int jsonStart = stdout.lastIndexOf('[');
            int jsonEnd = stdout.lastIndexOf(']');
            if (jsonStart < 0 || jsonEnd < 0 || jsonEnd <= jsonStart) {
                return testCases.stream()
                        .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                                0, null, "Failed to parse batch results: " + sanitizeSandboxOutput(stdout), 0.0))
                        .collect(Collectors.toList());
            }

            String jsonArray = stdout.substring(jsonStart, jsonEnd + 1);
            List<Map<String, Object>> results = objectMapper.readValue(jsonArray,
                    new TypeReference<List<Map<String, Object>>>() {});

            List<RunResultDTO.RunCaseResult> caseResults = new ArrayList<>();
            for (int i = 0; i < testCases.size() && i < results.size(); i++) {
                Map<String, Object> result = results.get(i);
                RunSubmissionDTO.RunTestCase testCase = testCases.get(i);

                String output = result.get("output") != null ? result.get("output").toString() : "";
                long runtime = result.get("runtime") != null ? ((Number) result.get("runtime")).longValue() : 0;
                String status = result.get("status") != null ? result.get("status").toString() : "error";
                long memoryBytes = result.get("memory") != null
                        ? ((Number) result.get("memory")).longValue() : 0;
                double memoryMb = memoryBytes / (1024.0 * 1024.0);

                if ("timeout".equals(status)) {
                    caseResults.add(buildCaseResult(testCase, runId, userId,
                            "Time Limit Exceeded", runtime, null, "Per-case timeout exceeded", 0.0));
                } else if ("error".equals(status)) {
                    caseResults.add(buildCaseResult(testCase, runId, userId,
                            "Runtime Error", runtime, null, sanitizeSandboxOutput(output), 0.0));
                } else {
                    String expected = testCase.getOutput() != null ? testCase.getOutput().trim() : "";
                    boolean passed = normalizeOutput(output).equals(normalizeOutput(expected));
                    caseResults.add(buildCaseResult(testCase, runId, userId,
                            passed ? "Accepted" : "Wrong Answer", runtime, output, null, memoryMb));
                }
            }
            return caseResults;
        // broad catch: JSON parsing and result extraction may throw multiple exception types
        } catch (Exception e) {
            log.error("Failed to parse batch results", e);
            return testCases.stream()
                    .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                            0, null, "Result parsing failed: " + e.getMessage(), 0.0))
                    .collect(Collectors.toList());
        }
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
                .memory("0.0MB")
                .cases(List.of())
                .passedCases(0)
                .totalCases(0)
                .build();
    }

    private RunResultDTO.RunCaseResult buildCaseResult(RunSubmissionDTO.RunTestCase testCase,
                                                        String runId, String userId,
                                                        String status, long runtimeMs,
                                                        String output, String detail,
                                                        double memoryMb) {
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
                .memory(String.format("%.1fMB", memoryMb))
                .output(output)
                .expectedOutput(testCase.getOutput())
                .detail(detail)
                .inputs(inputs)
                .build();
    }
}
