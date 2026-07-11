package com.ulticode.modules.submission.service.impl;

import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.service.CodeExecutionHelper;
import com.ulticode.modules.submission.service.DFormEnvelopeCodec;
import com.ulticode.modules.submission.service.SandboxOutputFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementation of {@link CodeExecutionHelper} after the C4 split.
 *
 * <p>The four orthogonal concerns that used to share one 9-method
 * interface now live in three named types:
 * <ul>
 *   <li>envelope codec — {@link DFormEnvelopeCodec},</li>
 *   <li>display formatter — {@link SandboxOutputFormatter},</li>
 *   <li>legacy text utilities (kept here until callers migrate) —
 *       {@code extractFunctionName}, {@code normalizeOutput},
 *       {@code parseRuntimeMs}.</li>
 * </ul>
 *
 * <p>This service now exists as a thin delegation surface so existing
 * callers (the dispatcher, the queue pipeline, the controller) keep
 * working without churn. The three legacy methods stay until each caller
 * is independently migrated to its authoritative replacement (parseRuntimeMs
 * → VerdictMetricsParser; the other two have no direct callers and will
 * be removed in a follow-up).
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionHelperImpl implements CodeExecutionHelper {

    private final DFormEnvelopeCodec dFormEnvelopeCodec;
    private final SandboxOutputFormatter sandboxOutputFormatter;

    // ── Delegated: DFormEnvelopeCodec ────────────────────────────────────────

    @Override
    public String buildDInputsJson(RunSubmissionDTO.RunTestCase testCase,
                                   long perCaseTimeoutMs, long memoryLimitBytes) {
        return dFormEnvelopeCodec.buildDInputsJson(testCase, perCaseTimeoutMs, memoryLimitBytes);
    }

    @Override
    public String buildDBatchInputsJson(List<RunSubmissionDTO.RunTestCase> testCases,
                                        long perCaseTimeoutMs, long memoryLimitBytes) {
        return dFormEnvelopeCodec.buildDBatchInputsJson(testCases, perCaseTimeoutMs, memoryLimitBytes);
    }

    @Override
    public List<RunResultDTO.RunCaseResult> parseDEnvelope(String stdout,
                                                          List<RunSubmissionDTO.RunTestCase> testCases,
                                                          String runId, String userId) {
        return dFormEnvelopeCodec.parseDEnvelope(stdout, testCases, runId, userId);
    }

    // ── Delegated: SandboxOutputFormatter ───────────────────────────────────

    @Override
    public String sanitizeSandboxOutput(String output) {
        return sandboxOutputFormatter.sanitizeSandboxOutput(output);
    }

    @Override
    public RunResultDTO emptyResult(Long problemId, String userId) {
        return sandboxOutputFormatter.emptyResult(problemId, userId);
    }

    @Override
    public RunResultDTO.RunCaseResult buildCaseResult(RunSubmissionDTO.RunTestCase testCase,
                                                      String runId, String userId,
                                                      String status, long runtimeMs,
                                                      String output, String detail,
                                                      double memoryMb,
                                                      long elapsedUs, long cpuMs) {
        return sandboxOutputFormatter.buildCaseResult(testCase, runId, userId, status,
            runtimeMs, output, detail, memoryMb, elapsedUs, cpuMs);
    }

    // ── Legacy utilities (kept here; no direct callers as of C4 audit) ──────

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
        int nameEnd = parenStart;
        int nameStart = nameEnd - 1;
        while (nameStart > idx && Character.isJavaIdentifierPart(code.charAt(nameStart))) {
            nameStart--;
        }
        nameStart++;
        if (nameStart >= nameEnd) {
            return null;
        }
        return code.substring(nameStart, nameEnd);
    }

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
    public String normalizeOutput(String output) {
        if (output == null) {
            return "";
        }
        return output.trim().replaceAll("\\s+", " ")
            .replaceAll("\\s*,\\s*", ",")
            .replaceAll(",\\s*}", "}").replaceAll(",\\s*]", "]");
    }
}
