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
 *   <li>runtime parsing — {@code parseRuntimeMs} (kept here because the
 *       sandbox wire format it serves differs from the queue payload
 *       format {@link com.ulticode.modules.queue.port.VerdictMetricsParser}
 *       handles; see that method's javadoc).</li>
 * </ul>
 *
 * <p>This service exists as a thin delegation surface so existing callers
 * (the dispatcher, the queue pipeline, the controller) keep working without
 * churn. The two orphan text utilities (extractFunctionName, normalizeOutput)
 * that had no production callers were removed.
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

    // ── Legacy utility (sandbox wire format; not the queue payload format) ─

    /**
     * Parse a sandbox runtime wire string to milliseconds. Unlike
     * {@link com.ulticode.modules.queue.port.VerdictMetricsParser#parseRuntimeMs},
     * this version serves the raw sandbox-output formats the dispatcher and
     * sandbox executor see — the {@code "s"} suffix (seconds -> millis via
     * {@code Double} math) and fractional milliseconds (e.g. {@code "42.5ms"}).
     * The queue-side parser only accepts integer {@code "Nms"} payloads, so
     * the two are NOT interchangeable; routing this onto VerdictMetricsParser
     * would silently drop runtime data for those formats.
     */
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

}
