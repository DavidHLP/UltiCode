package com.ulticode.modules.submission.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
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
    private final UuidGenerator uuidGenerator;

    // Form A removed in Phase 5b. The D-form harness is now the
    // sole dispatch path. See CodeExecutionHelper interface javadoc.

    @Override
    public String extractFunctionName(String code, String keyword) {
        if (code == null || keyword == null) {
            return null;
        }
        int idx = code.indexOf(keyword);
        if (idx < 0) {
            return null;
        }
        int parenStart = code.indexOf('(', idx);
        if (parenStart < 0) {
            return null;
        }
        // Walk back to find the function name start
        int nameEnd = parenStart;
        int nameStart = nameEnd - 1;
        while (nameStart > idx && Character.isJavaIdentifierPart(code.charAt(nameStart))) {
            nameStart--;
        }
        nameStart++; // step past the non-identifier char
        if (nameStart >= nameEnd) {
            return null;
        }
        return code.substring(nameStart, nameEnd);
    }

    @Override
    public long parseRuntimeMs(String runtime) {
        if (runtime == null) return 0L;
        String trimmed = runtime.trim();
        if (trimmed.endsWith("ms")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2).trim();
        } else if (trimmed.endsWith("s")) {
            try {
                return (long) (Double.parseDouble(trimmed.substring(0, trimmed.length() - 1).trim()) * 1000.0);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        try {
            // Tolerate fractional ms (e.g. "12.34ms") from the precise
            // elapsed_us formatting introduced in ADR-002 §8.
            return (long) Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }


    @Override
    public String normalizeOutput(String output) {
        if (output == null) return "";
        return output.trim().replaceAll("\\s+", " ")
                .replaceAll("\\s*,\\s*", ",")
                .replaceAll(",\\s*}", "}").replaceAll(",\\s*]", "]");
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
                .id(uuidGenerator.newId())
                .problemId(problemId)
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
                                                      double memoryMb,
                                                      long elapsedUs, long cpuMs) {
        List<RunResultDTO.RunCaseResult.InputParam> inputs = null;
        if (testCase.getInputs() != null) {
            inputs = testCase.getInputs().stream()
                    .map(i -> RunResultDTO.RunCaseResult.InputParam.builder()
                            .id(i.getId()).label(i.getLabel()).name(i.getName()).value(i.getValue())
                            .build())
                    .toList();
        }
        // Prefer precise microseconds for the formatted string so fast
        // cases stop showing "0ms" (ADR-002 §8). Fall back to the legacy
        // ms value when the harness didn't emit elapsed_us.
        String runtimeStr = elapsedUs > 0
                ? String.format("%.2fms", elapsedUs / 1000.0)
                : runtimeMs + "ms";
        return RunResultDTO.RunCaseResult.builder()
                .id(uuidGenerator.newId()).runId(runId)
                .submissionTestId(testCase.getId()).testCaseId(testCase.getId())
                .caseLabel(testCase.getLabel() != null ? testCase.getLabel() : testCase.getId())
                .status(status)
                .runtime(runtimeStr)
                .runtimeMs(runtimeMs)
                .memory(String.format("%.1fMB", memoryMb))
                .memoryMb(memoryMb)
                .runtimeUs(elapsedUs > 0 ? elapsedUs : null)
                .cpuMs(cpuMs > 0 ? cpuMs : null)
                .output(output).expectedOutput(testCase.getOutput()).detail(detail).inputs(inputs)
                .build();
    }

    // ── D-form (LeetCode/HackerRank harness) ─────────────────────────────────
    // These three methods replace the per-request Form A bash wrapper with a
    // static input.json contract. The harness image (docker/sandbox/Dockerfile
    // Phase 2 build) has the pre-compiled harness at /opt/harness/{lang}/.
    //
    // Schema reference: docker/sandbox/harness/{java,python}/ — see
    // .claude/PRPs/plans/oj-sandbox-d-form-refactor.plan.md (D3 + D4 + D9).

    private static final java.util.Set<String> DFORM_TYPES = java.util.Set.of(
            "int", "long", "double", "boolean",
            "String", "int[]", "int[][]", "long[]", "String[]",
            "ListNode", "ListNode[]", "TreeNode", "TreeNode[]"
    );

    @Override
    public String buildDInputsJson(RunSubmissionDTO.RunTestCase testCase,
                                   long perCaseTimeoutMs, long memoryLimitBytes) {
        java.util.List<RunSubmissionDTO.RunTestCase> one = java.util.List.of(testCase);
        return buildDBatchInputsJson(one, perCaseTimeoutMs, memoryLimitBytes);
    }

    @Override
    public String buildDBatchInputsJson(List<RunSubmissionDTO.RunTestCase> testCases,
                                        long perCaseTimeoutMs, long memoryLimitBytes) {
        java.util.LinkedHashMap<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("per_case_timeout_ms", perCaseTimeoutMs);
        // ADR-002 §8: forward the per-case memory ceiling so the harness
        // can self-report Memory Limit Exceeded before the docker
        // --memory cap hard-kills the whole container. <=0 disables the
        // harness-level check (docker cap still enforces).
        if (memoryLimitBytes > 0) {
            root.put("memory_limit_bytes", memoryLimitBytes);
        }
        java.util.List<java.util.Map<String, Object>> cases = new java.util.ArrayList<>();
        for (RunSubmissionDTO.RunTestCase tc : testCases) {
            java.util.LinkedHashMap<String, Object> c = new java.util.LinkedHashMap<>();
            c.put("case_id", String.valueOf(tc.getId() != null ? tc.getId() : ""));
            c.put("label", tc.getLabel() != null ? tc.getLabel() : c.get("case_id"));
            c.put("expected_output", tc.getOutput() != null ? tc.getOutput() : "");
            c.put("inputs", buildDInputSpecs(tc));
            cases.add(c);
        }
        root.put("cases", cases);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize D-form input.json", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<java.util.Map<String, Object>> buildDInputSpecs(RunSubmissionDTO.RunTestCase tc) {
        List<RunSubmissionDTO.RunInput> inputs = tc.getInputs();
        if (inputs == null || inputs.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<java.util.Map<String, Object>> specs = new java.util.ArrayList<>();
        for (RunSubmissionDTO.RunInput in : inputs) {
            java.util.LinkedHashMap<String, Object> spec = new java.util.LinkedHashMap<>();
            spec.put("name", in.getName());
            // value is stored on the backend as a JSON-encoded literal; ship as-is
            spec.put("value", in.getValue() == null ? "null" : in.getValue());
            // CR fix: forward the OJ data-type hint when set. The harness
            // honors spec["type"] over a Java annotation or Python type hint
            // on the Solution method's argument, which is the only signal
            // for unannotated user code (the typical LeetCode/HackerRank
            // style). Empty / null / unknown types are omitted so the
            // harness falls back to whatever the annotation says.
            String type = in.getType();
            if (type != null && !type.isBlank() && DFORM_TYPES.contains(type)) {
                spec.put("type", type);
            }
            specs.add(spec);
        }
        return specs;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RunResultDTO.RunCaseResult> parseDEnvelope(String stdout,
                                                          List<RunSubmissionDTO.RunTestCase> testCases,
                                                          String runId, String userId) {
        if (stdout == null || stdout.isBlank()) {
            return testCases.stream()
                    .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                            0L, null, "D-form harness emitted no envelope (process killed mid-run?)",
                            0.0, 0L, 0L))
                    .collect(Collectors.toList());
        }
        // The Java harness Main emits JVM WARNING lines (e.g.
        // "SecurityManager has been called") before the JSON envelope,
        // which breaks a strict Jackson parse. Strip everything before
        // the first '{' so we land on the envelope object.
        int jsonStart = stdout.indexOf('{');
        String envelopeSource = jsonStart >= 0 ? stdout.substring(jsonStart) : stdout;
        java.util.Map<String, Object> env;
        try {
            env = objectMapper.readValue(envelopeSource, java.util.Map.class);
        } catch (Exception parseFail) {
            String detail = "D-form envelope unparseable: " + sanitizeSandboxOutput(stdout);
            return testCases.stream()
                    .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                            0L, null, detail, 0.0, 0L, 0L))
                    .collect(Collectors.toList());
        }
        com.ulticode.modules.submission.dto.EnvelopeDTO envelope =
                com.ulticode.modules.submission.dto.EnvelopeDTO.fromMap(env);
        if (envelope.exitCode() != 0) {
            // Harness itself panicked (parse failure, javac failure, ambiguous
            // Solution, etc.). Surface a single Runtime Error for the whole batch.
            String detail = "D-form harness panic (exit_code=" + envelope.exitCode() + "): "
                    + sanitizeSandboxOutput(stdout);
            return testCases.stream()
                    .map(tc -> buildCaseResult(tc, runId, userId, "Runtime Error",
                            0L, null, detail, 0.0, 0L, 0L))
                    .collect(Collectors.toList());
        }
        java.util.List<com.ulticode.modules.submission.dto.PerCaseResultDTO> parsed = envelope.results();
        java.util.List<RunResultDTO.RunCaseResult> out = new java.util.ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            RunSubmissionDTO.RunTestCase tc = testCases.get(i);
            com.ulticode.modules.submission.dto.PerCaseResultDTO pr = i < parsed.size() ? parsed.get(i) : null;
            if (pr == null) {
                out.add(buildCaseResult(tc, runId, userId, "Runtime Error",
                        0L, null, "D-form envelope missing per-case result for index " + i,
                        0.0, 0L, 0L));
                continue;
            }
            String status = pr.status() == null ? "Runtime Error" : pr.status();
            // TLE in D-form manifests as "Time Limit Exceeded" + interrupted=true.
            // Match the legacy verdict spellings to keep API consumers happy.
            String detail = null;
            if (pr.error() != null && pr.error().message() != null) {
                detail = "[" + (pr.error().type() == null ? "Error" : pr.error().type()) + "] "
                        + pr.error().message();
            }
            // Convert harness-reported peak bytes to MiB (floored at 1 if
            // non-zero, like CodeExecutionService.toDtoCaseResult does).
            // Older harnesses that don't emit peak_memory_bytes report 0
            // here — we keep that as a visible signal "unknown" rather
            // than silently injecting 256M.
            long memoryMb = pr.peakMemoryBytes() <= 0
                    ? 0L
                    : Math.max(1L, pr.peakMemoryBytes() / (1024L * 1024L));
            out.add(buildCaseResult(
                    tc, runId, userId,
                    status,
                    pr.elapsedMs(),
                    pr.result() == null ? null : String.valueOf(pr.result()),
                    detail,
                    (double) memoryMb,
                    pr.elapsedUs(),
                    pr.cpuMs()));
        }
        return out;
    }
}
