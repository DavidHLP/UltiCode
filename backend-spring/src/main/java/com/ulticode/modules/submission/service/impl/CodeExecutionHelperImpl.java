package com.ulticode.modules.submission.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.service.CodeExecutionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of CodeExecutionHelper.
 * All per-language logic, result parsing, and utilities -- no Docker, no security.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionHelperImpl implements CodeExecutionHelper {

    private final ObjectMapper objectMapper;

    @Override
    public String buildWrapperScript(String language, String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        return switch (language) {
            case "javascript" -> buildJavaScriptBatchWrapper(code, testCases);
            case "python" -> buildPythonBatchWrapper(code, testCases);
            case "java" -> buildJavaBatchWrapper(code, testCases);
            case "c" -> buildCBatchWrapper(code, testCases);
            case "cpp" -> buildCppBatchWrapper(code, testCases);
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    public String buildJavaScriptBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
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

    public String buildPythonBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
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

    public String buildCBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        int perCaseTimeout = 30 / Math.max(testCases.size(), 1);
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

    public String buildCppBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        int perCaseTimeout = 30 / Math.max(testCases.size(), 1);
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

    public String buildJavaBatchWrapper(String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        String b64 = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
        int perCaseTimeout = 30 / Math.max(testCases.size(), 1);
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

    @Override
    public String buildBatchInputsJson(List<RunSubmissionDTO.RunTestCase> testCases) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < testCases.size(); i++) {
            if (i > 0) json.append(",");
            json.append(buildInputsJson(testCases.get(i)));
        }
        json.append("]");
        return json.toString();
    }

    @Override
    public List<RunResultDTO.RunCaseResult> parseBatchResults(
            String stdout, List<RunSubmissionDTO.RunTestCase> testCases,
            String runId, String userId) {
        try {
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
        } catch (Exception e) {
            log.error("Failed to parse batch results", e);
            return testCases.stream()
                    .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                            0, null, "Result parsing failed: " + e.getMessage(), 0.0))
                    .collect(Collectors.toList());
        }
    }

    @Override
    public String wrapJavaScript(String code) {
        String funcName = extractFunctionName(code, "function ");
        return """
                %s
                const input = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));
                const result = %s(...input);
                process.stdout.write(JSON.stringify(result));
                """.formatted(code, funcName);
    }

    @Override
    public String wrapPython(String code) {
        String funcName = extractFunctionName(code, "def ");
        return """
                import json, sys
                %s
                input_data = json.loads(sys.stdin.read())
                result = %s(*input_data)
                print(json.dumps(result))
                """.formatted(code, funcName);
    }

    @Override
    public String wrapJava(String code) {
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

    @Override
    public String extractFunctionName(String code, String keyword) {
        int idx = code.indexOf(keyword);
        if (idx < 0) return "solution";
        int start = idx + keyword.length();
        while (start < code.length() && Character.isWhitespace(code.charAt(start))) start++;
        int end = start;
        while (end < code.length() && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '_')) end++;
        return end == start ? "solution" : code.substring(start, end);
    }

    @Override
    public String buildInputsJson(RunSubmissionDTO.RunTestCase testCase) {
        if (testCase.getInputs() == null || testCase.getInputs().isEmpty()) return "[]";
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < testCase.getInputs().size(); i++) {
            if (i > 0) json.append(",");
            json.append(parseInputValue(testCase.getInputs().get(i).getValue()));
        }
        json.append("]");
        return json.toString();
    }

    @Override
    public String parseInputValue(String value) {
        if (value == null) return "null";
        value = value.trim();
        if (value.equals("true") || value.equals("false")) return value;
        if (value.startsWith("[") && value.endsWith("]")) return value;
        try { Double.parseDouble(value); return value; }
        catch (NumberFormatException e) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }

    @Override
    public String normalizeOutput(String output) {
        if (output == null) return "";
        return output.trim().replaceAll("\\s+", " ")
                .replaceAll(",\\s*}", "}").replaceAll(",\\s*]", "]");
    }

    @Override
    public long parseRuntimeMs(String runtime) {
        if (runtime == null || !runtime.endsWith("ms")) return 0;
        try { return Long.parseLong(runtime.replace("ms", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    @Override
    public String sanitizeSandboxOutput(String output) {
        if (output == null) return "Runtime error";
        String[] lines = output.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.contains("OCI runtime") || trimmed.contains("docker")) continue;
            sb.append(trimmed).append("\n");
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "Runtime error" : result;
    }

    @Override
    public RunResultDTO emptyResult(Long problemId, String userId) {
        return RunResultDTO.builder()
                .id(UUID.randomUUID().toString())
                .problemId(String.valueOf(problemId))
                .userId(userId)
                .verdict("System Error")
                .runtime("0ms")
                .memory("0.0MB")
                .cases(List.of())
                .passedCases(0)
                .totalCases(0)
                .build();
    }

    @Override
    public RunResultDTO.RunCaseResult buildCaseResult(RunSubmissionDTO.RunTestCase testCase,
                                                      String runId, String userId,
                                                      String status, long runtimeMs,
                                                      String output, String detail,
                                                      double memoryMb) {
        List<RunResultDTO.RunCaseResult.InputParam> inputs = null;
        if (testCase.getInputs() != null) {
            inputs = testCase.getInputs().stream()
                    .map(i -> RunResultDTO.RunCaseResult.InputParam.builder()
                            .id(i.getId()).label(i.getLabel()).name(i.getName()).value(i.getValue())
                            .build())
                    .toList();
        }
        return RunResultDTO.RunCaseResult.builder()
                .id(UUID.randomUUID().toString()).runId(runId)
                .submissionTestId(testCase.getId()).testCaseId(testCase.getId())
                .caseLabel(testCase.getLabel() != null ? testCase.getLabel() : testCase.getId())
                .status(status).runtime(runtimeMs + "ms").memory(String.format("%.1fMB", memoryMb))
                .output(output).expectedOutput(testCase.getOutput()).detail(detail).inputs(inputs)
                .build();
    }
}
