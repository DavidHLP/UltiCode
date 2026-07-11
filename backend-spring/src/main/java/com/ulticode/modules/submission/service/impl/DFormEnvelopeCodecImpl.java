package com.ulticode.modules.submission.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.service.DFormEnvelopeCodec;
import com.ulticode.modules.submission.service.SandboxOutputFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default {@link DFormEnvelopeCodec} implementation. Owns the input.json
 * serialiser and the stdout envelope parser for the D-form sandbox pipeline.
 *
 * <p>Cross-references {@link SandboxOutputFormatter} for the user-facing
 * detail-string scrub (so docker / OCI lines don't leak into the result)
 * and for the per-case DTO assembly — keeping the parse and the display
 * concerns in separate objects preserves the C4 depth gain.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DFormEnvelopeCodecImpl implements DFormEnvelopeCodec {

    /**
     * OJ data-type hints that the harness understands. Forwarded on the
     * {@code spec["type"]} field when set, so the harness can pick the
     * correct unmarshaller for unannotated Solution signatures (the
     * typical LeetCode / HackerRank style).
     */
    private static final Set<String> DFORM_TYPES = Set.of(
        "int", "long", "double", "boolean",
        "String", "int[]", "int[][]", "long[]", "String[]",
        "ListNode", "ListNode[]", "TreeNode", "TreeNode[]"
    );

    private final ObjectMapper objectMapper;
    private final SandboxOutputFormatter sandboxOutputFormatter;

    @Override
    public String buildDInputsJson(RunSubmissionDTO.RunTestCase testCase,
                                   long perCaseTimeoutMs, long memoryLimitBytes) {
        List<RunSubmissionDTO.RunTestCase> one = List.of(testCase);
        return buildDBatchInputsJson(one, perCaseTimeoutMs, memoryLimitBytes);
    }

    @Override
    public String buildDBatchInputsJson(List<RunSubmissionDTO.RunTestCase> testCases,
                                        long perCaseTimeoutMs, long memoryLimitBytes) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("per_case_timeout_ms", perCaseTimeoutMs);
        // ADR-002 §8: forward the per-case memory ceiling so the harness
        // can self-report Memory Limit Exceeded before the docker
        // --memory cap hard-kills the whole container. <=0 disables the
        // harness-level check (docker cap still enforces).
        if (memoryLimitBytes > 0) {
            root.put("memory_limit_bytes", memoryLimitBytes);
        }
        List<Map<String, Object>> cases = new ArrayList<>();
        for (RunSubmissionDTO.RunTestCase tc : testCases) {
            LinkedHashMap<String, Object> c = new LinkedHashMap<>();
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

    @Override
    @SuppressWarnings("unchecked")
    public List<RunResultDTO.RunCaseResult> parseDEnvelope(String stdout,
                                                          List<RunSubmissionDTO.RunTestCase> testCases,
                                                          String runId, String userId) {
        if (stdout == null || stdout.isBlank()) {
            return testCases.stream()
                .map(tc -> sandboxOutputFormatter.buildCaseResult(tc, runId, userId, "Runtime Error",
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
        Map<String, Object> env;
        try {
            env = objectMapper.readValue(envelopeSource, Map.class);
        } catch (Exception parseFail) {
            String detail = "D-form envelope unparseable: " + sandboxOutputFormatter.sanitizeSandboxOutput(stdout);
            return testCases.stream()
                .map(tc -> sandboxOutputFormatter.buildCaseResult(tc, runId, userId, "Runtime Error",
                    0L, null, detail, 0.0, 0L, 0L))
                .collect(Collectors.toList());
        }
        com.ulticode.modules.submission.dto.EnvelopeDTO envelope =
            com.ulticode.modules.submission.dto.EnvelopeDTO.fromMap(env);
        if (envelope.exitCode() != 0) {
            // Harness itself panicked (parse failure, javac failure, ambiguous
            // Solution, etc.). Surface a single Runtime Error for the whole batch.
            String detail = "D-form harness panic (exit_code=" + envelope.exitCode() + "): "
                + sandboxOutputFormatter.sanitizeSandboxOutput(stdout);
            return testCases.stream()
                .map(tc -> sandboxOutputFormatter.buildCaseResult(tc, runId, userId, "Runtime Error",
                    0L, null, detail, 0.0, 0L, 0L))
                .collect(Collectors.toList());
        }
        List<com.ulticode.modules.submission.dto.PerCaseResultDTO> parsed = envelope.results();
        List<RunResultDTO.RunCaseResult> out = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            RunSubmissionDTO.RunTestCase tc = testCases.get(i);
            com.ulticode.modules.submission.dto.PerCaseResultDTO pr = i < parsed.size() ? parsed.get(i) : null;
            if (pr == null) {
                out.add(sandboxOutputFormatter.buildCaseResult(tc, runId, userId, "Runtime Error",
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
            out.add(sandboxOutputFormatter.buildCaseResult(
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

    /**
     * Build the per-input spec array for a single test case. Honours the
     * OJ data-type hint when set and known.
     *
     * @param tc the test case whose inputs to spec
     * @return list of input spec maps; empty when the case has no inputs
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildDInputSpecs(RunSubmissionDTO.RunTestCase tc) {
        List<RunSubmissionDTO.RunInput> inputs = tc.getInputs();
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> specs = new ArrayList<>();
        for (RunSubmissionDTO.RunInput in : inputs) {
            LinkedHashMap<String, Object> spec = new LinkedHashMap<>();
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
}