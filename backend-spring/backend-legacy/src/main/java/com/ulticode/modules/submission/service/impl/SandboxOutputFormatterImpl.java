package com.ulticode.modules.submission.service.impl;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.service.SandboxOutputFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default {@link SandboxOutputFormatter} implementation. Owns the display
 * side of the D-form sandbox pipeline: stdout scrubbing, empty-result
 * shells, and per-case DTO assembly.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxOutputFormatterImpl implements SandboxOutputFormatter {

    private final UuidGenerator uuidGenerator;

    @Override
    public long parseRuntimeMs(String runtime) {
        if (runtime == null) {
            return 0L;
        }
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
            return (long) Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public String sanitizeSandboxOutput(String output) {
        if (output == null) {
            return "Runtime error";
        }
        String[] lines = output.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("OCI runtime") || trimmed.contains("docker")) {
                continue;
            }
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
}