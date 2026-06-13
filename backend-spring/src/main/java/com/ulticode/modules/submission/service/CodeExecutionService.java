package com.ulticode.modules.submission.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxExecutor;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.TestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Thin facade for code execution (M2a, ADR-002).
 *
 * <p>Compared to the pre-M2a version:
 * <ul>
 *   <li>Directly depends on the Hexagonal
 *       {@link SandboxExecutor} port instead of the
 *       pre-M2a {@code SandboxService} interface — the latter
 *       has been deleted along with its impl (ADR-002 §1.1).</li>
 *   <li>Per-case verdict still flows through the shared
 *       {@link VerdictResolver} (M1a round-4 / F15-F16).</li>
 *   <li>Boundary translation (DTO ↔ port) lives here because this
 *       is the one place where the wire shape and the port shape
 *       meet; both the sandbox and the controller layers stay
 *       decoupled from each other.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionService {

    private final SandboxExecutor sandboxExecutor;
    private final CodeExecutionHelper helper;
    private final VerdictResolver verdictResolver;

    public RunResultDTO execute(RunSubmissionDTO request, Long problemId, String userId) {
        String language = request.getLanguage() == null
                ? ""
                : request.getLanguage().toLowerCase().trim();

        // CR fix (Phase 5.5 #1): validate against the actual
        // executable language set (DFORM_SUPPORTED_LANGUAGES), not
        // the API-advertised SUPPORTED_LANGUAGES. After Form A was
        // deleted, the dispatcher can only run java + python.
        if (!CodeExecutionHelper.DFORM_SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED,
                    "Unsupported language: " + language + ". Supported: "
                            + CodeExecutionHelper.DFORM_SUPPORTED_LANGUAGES);
        }

        List<RunSubmissionDTO.RunTestCase> testCases = request.getTestCases();
        if (testCases == null || testCases.isEmpty()) {
            return helper.emptyResult(problemId, userId);
        }

        // Map the wire DTO to the port-owned sandbox test-case shape
        // at the seam; the sandbox never sees the DTO type.
        List<TestCase> sandboxCases = testCases.stream()
                .map(CodeExecutionService::toSandboxTestCase)
                .toList();

        String runId = UUID.randomUUID().toString();
        // Per-run job descriptor. The submissionId is synthetic for
        // /run (preview) requests because no DB row exists yet — see
        // SandboxJob.submissionId() javadoc.
        SandboxJob job = new SandboxJob(
                runId,
                userId == null ? "" : userId,
                /* submissionId */ runId,
                /* submissionGeneration */ 0L,
                language,
                request.getCode() == null ? "" : request.getCode(),
                /* timeoutSeconds */ deriveDefaultTimeoutSeconds(),
                /* memoryMb */ deriveDefaultMemoryMb()
        );

        List<RunResultDTO.RunCaseResult> dtoResults = new ArrayList<>(sandboxCases.size());
        if (sandboxCases.size() == 1) {
            RunCaseResult one = sandboxExecutor.run(job, sandboxCases.get(0));
            dtoResults.add(toDtoCaseResult(one, runId, userId, testCases.get(0)));
        } else {
            List<RunCaseResult> parsed = sandboxExecutor.runBatch(job, sandboxCases).cases();
            for (int i = 0; i < parsed.size(); i++) {
                dtoResults.add(toDtoCaseResult(parsed.get(i), runId, userId, testCases.get(i)));
            }
        }

        int passedCases = (int) dtoResults.stream()
                .filter(r -> "Accepted".equals(r.getStatus()))
                .count();
        String verdict = verdictResolver.reduceWire(
                dtoResults.stream()
                        .map(RunResultDTO.RunCaseResult::getStatus)
                        .collect(Collectors.toList())
        ).wireValue();
        long totalRuntimeMs = dtoResults.stream()
                .mapToLong(r -> helper.parseRuntimeMs(r.getRuntime()))
                .sum();
        double maxMemoryMb = dtoResults.stream()
                .map(RunResultDTO.RunCaseResult::getMemoryMb)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        return RunResultDTO.builder()
                .id(runId)
                .problemId(problemId)
                .userId(userId)
                .verdict(verdict)
                .runtime(totalRuntimeMs + "ms")
                .runtimeMs(totalRuntimeMs)
                .memory(String.format("%.1fMB", maxMemoryMb))
                .memoryMb(maxMemoryMb)
                .cases(dtoResults)
                .passedCases(passedCases)
                .totalCases(testCases.size())
                .build();
    }

    // ── DTO ↔ port translation at the seam ──────────────────────────────────

    private static TestCase toSandboxTestCase(RunSubmissionDTO.RunTestCase rtc) {
        List<TestCase.Input> inputs = Optional.ofNullable(rtc.getInputs())
                .orElse(List.of())
                .stream()
                .map(ri -> new TestCase.Input(
                        ri.getId(), ri.getLabel(),
                        ri.getName(), ri.getValue(), ri.getType()))
                .toList();
        return new TestCase(rtc.getId(), rtc.getLabel(), inputs, rtc.getOutput());
    }

    private static RunResultDTO.RunCaseResult toDtoCaseResult(
            RunCaseResult port,
            String runId,
            String userId,
            RunSubmissionDTO.RunTestCase original) {
        String wireStatus = SubmissionStatusCodec.toWire(port.status());
        long memoryMb = port.memoryBytes() <= 0
                ? 0L
                : Math.max(1L, port.memoryBytes() / (1024L * 1024L));
        // The wire DTO carries both a pre-formatted runtime string
        // (e.g. "12ms") and a numeric v2 field. Build the string form
        // here so the response shape stays backwards compatible with
        // any caller that hasn't migrated to the v2 numeric fields.
        return RunResultDTO.RunCaseResult.builder()
                .id(original == null ? null : original.getId())
                .runId(runId)
                .submissionTestId(original == null ? null : original.getId())
                .testCaseId(original == null ? null : original.getId())
                .caseLabel(original == null ? null : original.getLabel())
                .status(wireStatus)
                .runtime(port.elapsedMs() + "ms")
                .memory(String.format("%.1fMB", (double) memoryMb))
                .runtimeMs(port.elapsedMs())
                .memoryMb((double) memoryMb)
                .detail(port.detail())
                .build();
    }

    // ── Per-run defaults ─────────────────────────────────────────────────────
    // Conservative fallbacks for /run (preview) requests where the
    // problem's resource configuration is not on the request. The
    // real per-run values come from the controller when this is
    // reached from the submission/judge path; M2a keeps the diff
    // minimal by leaving the controller wiring alone for now.
    private int deriveDefaultTimeoutSeconds() {
        return 2;
    }

    private int deriveDefaultMemoryMb() {
        return 256;
    }
}
