package com.ulticode.modules.submission.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxExecutor;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.TestCase;
import com.ulticode.modules.submission.util.OJSignatureParser;
import com.ulticode.modules.submission.port.JudgingLanguageSupport;
import com.ulticode.modules.submission.port.ProblemFactsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ProblemFactsPort problemFacts;
    private final UuidGenerator uuidGenerator;
    /**
     * Architecture-review candidate #1: the executable-language set
     * now crosses the {@link JudgingLanguageSupport} seam rather than
     * the submission-internal {@link CodeExecutionHelper} constants.
     */
    private final JudgingLanguageSupport languageSupport;
    /**
     * M2a-round-2 fix (codex review F2): defaults were hard-coded to
     * 2s / 256 MiB which silently regressed both /run and /submit
     * timeouts versus the pre-M2a code that read
     * {@code sandboxConfig.timeout()} / {@code sandboxConfig.memory()}.
     * The per-problem controller path remains the source of truth;
     * this is the fallback when a controller has not supplied a
     * per-run value.
     */
    private final DockerSandboxConfig sandboxConfig;

    /**
     * 进程级缓存:(problemId, language) -> 推断出的 OJ 参数类型列表,避免每次
     * /run 都查 DB + 解析 starter_code。题目 starter_code 由管理后台维护、变更
     * 频率极低,简单缓存即可;starter_code 被编辑后重启或接更新事件失效。
     */
    private final Map<String, List<String>> signatureCache = new ConcurrentHashMap<>();

    public RunResultDTO execute(RunSubmissionDTO request, Long problemId, String userId) {
        String language = request.getLanguage() == null
                ? ""
                : request.getLanguage().toLowerCase().trim();

        // CR fix (Phase 5.5 #1): validate against the actual
        // executable language set, not the API-advertised
        // advertisedLanguages(). After Form A was deleted, the
        // dispatcher can only run java + python (plus cpp).
        // Architecture-review candidate #1: cross the
        // JudgingLanguageSupport seam.
        if (!languageSupport.isExecutable(language)) {
            throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED,
                    "Unsupported language: " + language + ". Supported: "
                            + languageSupport.executableLanguages());
        }

        List<RunSubmissionDTO.RunTestCase> testCases = request.getTestCases();
        if (testCases == null || testCases.isEmpty()) {
            return helper.emptyResult(problemId, userId);
        }

        // 链表/树题:从题目 starter_code 推断参数 OJ 类型(ListNode/TreeNode),
        // 回填到每个 input 的 type。用户代码无类型注解时,这是 harness 把
        // [2,4,3] 反序列化成 ListNode 的唯一信号。详见 OJSignatureParser。
        List<String> paramTypes = resolveParamTypes(problemId, language);
        enrichInputTypes(testCases, paramTypes);

        // Map the wire DTO to the port-owned sandbox test-case shape
        // at the seam; the sandbox never sees the DTO type.
        List<TestCase> sandboxCases = testCases.stream()
                .map(CodeExecutionService::toSandboxTestCase)
                .toList();

        String runId = uuidGenerator.newId();
        // Per-run job descriptor. The submissionId is synthetic for
        // /run (preview) requests because no DB row exists yet — see
        // SandboxJob.submissionId() javadoc.
        // ADR-002 §8 (P2-1): per-problem time/memory limits take
        // precedence over the global default; NULL on the problem row
        // falls back to the global default.
        int timeoutSeconds = resolveTimeoutSeconds(problemId);
        int memoryMb = resolveMemoryMb(problemId);
        SandboxJob job = new SandboxJob(
                runId,
                userId == null ? "" : userId,
                /* submissionId */ runId,
                /* submissionGeneration */ 0L,
                language,
                request.getCode() == null ? "" : request.getCode(),
                /* timeoutSeconds */ timeoutSeconds,
                /* memoryMb */ memoryMb
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
                .mapToLong(r -> r.getRuntimeMs() != null ? r.getRuntimeMs()
                        : helper.parseRuntimeMs(r.getRuntime()))
                .sum();
        long totalRuntimeUs = dtoResults.stream()
                .mapToLong(r -> r.getRuntimeUs() != null ? r.getRuntimeUs() : 0L)
                .sum();
        long totalCpuMs = dtoResults.stream()
                .mapToLong(r -> r.getCpuMs() != null ? r.getCpuMs() : 0L)
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
                .runtime(totalRuntimeUs > 0
                        ? String.format("%.2fms", totalRuntimeUs / 1000.0)
                        : totalRuntimeMs + "ms")
                .runtimeMs(totalRuntimeMs)
                .runtimeUs(totalRuntimeUs > 0 ? totalRuntimeUs : null)
                .cpuMs(totalCpuMs > 0 ? totalCpuMs : null)
                .memory(String.format("%.1fMB", maxMemoryMb))
                .memoryMb(maxMemoryMb)
                .cases(dtoResults)
                .passedCases(passedCases)
                .totalCases(testCases.size())
                .build();
    }

    // ── 参数类型推断(链表/树题)──────────────────────────────────────────────

    /**
     * 按 (problemId, language) 解析题目 starter_code,得到 Solution 方法参数的
     * OJ 类型列表。结果进程级缓存,starter_code 变更频率极低。
     */
    private List<String> resolveParamTypes(Long problemId, String language) {
        if (problemId == null) {
            return List.of();
        }
        String cacheKey = problemId + ":" + language;
        List<String> cached = signatureCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<String> paramTypes = doResolveParamTypes(problemId, language);
        signatureCache.putIfAbsent(cacheKey, paramTypes);
        return paramTypes;
    }

    private List<String> doResolveParamTypes(Long problemId, String language) {
        try {
            String starter = problemFacts.findStarterCode(problemId, language);
            if (starter == null || starter.isBlank()) {
                return List.of();
            }
            return OJSignatureParser.parse(starter, language);
        } catch (RuntimeException e) {
            // 任何数据访问/解析异常都安全退化:不给 type 提示,等价修复前行为。
            log.debug("Failed to resolve OJ param types for problem {} lang {}: {}",
                    problemId, language, e.getMessage());
            return List.of();
        }
    }

    /**
     * 把推断出的参数类型回填到每个 test case 的 inputs:仅当 input 自身未带 type
     * 时按位置填入(paramTypes[i]),保留「input 显式 type > starter_code 推断」优先级。
     * 所有 test case 共享同一份 paramTypes(方法级参数类型),位置与 harness 的
     * 位置绑定语义一致。
     */
    private void enrichInputTypes(List<RunSubmissionDTO.RunTestCase> testCases, List<String> paramTypes) {
        if (paramTypes == null || paramTypes.isEmpty()) {
            return;
        }
        for (RunSubmissionDTO.RunTestCase tc : testCases) {
            List<RunSubmissionDTO.RunInput> inputs = tc.getInputs();
            if (inputs == null) {
                continue;
            }
            for (int i = 0; i < inputs.size(); i++) {
                RunSubmissionDTO.RunInput in = inputs.get(i);
                String hint = i < paramTypes.size() ? paramTypes.get(i) : null;
                if (hint == null) {
                    continue;
                }
                if (in.getType() == null || in.getType().isBlank()) {
                    in.setType(hint);
                }
            }
        }
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
        // Bug fix: forward the user-supplied inputs/output/expectedOutput
        // back to the wire so the UI's TestResultsView can render the
        // "lists = …" / "expected = …" pair; previously these three
        // fields were silently dropped, so /run responses showed
        // "此用例未返回可展示的输入输出详情" for every case.
        List<RunResultDTO.RunCaseResult.InputParam> dtoInputs = null;
        if (port.inputs() != null) {
            dtoInputs = port.inputs().stream()
                    .map(i -> RunResultDTO.RunCaseResult.InputParam.builder()
                            .id(i.id()).label(i.label()).name(i.name()).value(i.value())
                            .build())
                    .toList();
        }
        // Prefer precise microseconds for the formatted string so fast
        // cases stop showing "0ms" (ADR-002 §8). Fall back to the legacy
        // ms value when the harness didn't emit elapsed_us.
        String runtimeStr = port.elapsedUs() > 0
                ? String.format("%.2fms", port.elapsedUs() / 1000.0)
                : port.elapsedMs() + "ms";
        return RunResultDTO.RunCaseResult.builder()
                .id(original == null ? null : original.getId())
                .runId(runId)
                .submissionTestId(original == null ? null : original.getId())
                .testCaseId(original == null ? null : original.getId())
                .caseLabel(original == null ? null : original.getLabel())
                .status(wireStatus)
                .runtime(runtimeStr)
                .memory(String.format("%.1fMB", (double) memoryMb))
                .runtimeMs(port.elapsedMs())
                .memoryMb((double) memoryMb)
                .runtimeUs(port.elapsedUs() > 0 ? port.elapsedUs() : null)
                .cpuMs(port.cpuMs() > 0 ? port.cpuMs() : null)
                .detail(port.detail())
                .output(port.output())
                .expectedOutput(port.expectedOutput())
                .inputs(dtoInputs)
                .build();
    }

    // ── Per-problem resource limits (ADR-002 §8 / P2-1) ─────────────────────
    // A problem may carry its own time_limit (seconds) / memory_limit (MiB).
    // When present they override the global default; when NULL the global
    // default still applies, preserving backwards compatibility.

    private int resolveTimeoutSeconds(Long problemId) {
        Integer limit = readProblemLimit(problemId, true);
        return limit != null ? Math.max(1, limit) : deriveDefaultTimeoutSeconds();
    }

    private int resolveMemoryMb(Long problemId) {
        Integer limit = readProblemLimit(problemId, false);
        return (limit != null && limit > 0) ? limit : deriveDefaultMemoryMb();
    }

    private Integer readProblemLimit(Long problemId, boolean time) {
        if (problemId == null) {
            return null;
        }
        ProblemFactsPort.ProblemLimits limits = problemFacts.findLimits(problemId);
        if (limits == null) {
            // Missing problem row or data-access hiccup absorbed by the port:
            // fall back to the global default rather than failing the run.
            return null;
        }
        return time ? limits.timeLimitSeconds() : limits.memoryLimitMb();
    }

    // ── Per-run defaults ─────────────────────────────────────────────────────
    // M2a-round-2 fix (codex review F2): the pre-M2a code read
    // {@code sandboxConfig.timeout()} / {@code sandboxConfig.memory()}
    // (with the controller supplying a per-problem override for
    // /submit). M2a's hard-coded 2s / 256 MiB silently regressed both
    // /run and /submit; restored the config-derived defaults.
    //
    // The real per-run values come from the controller for /submit;
    // /run still uses these as a fallback when the problem record
    // has no resource configuration.
    private int deriveDefaultTimeoutSeconds() {
        // sandboxConfig.timeout() is the global default in seconds.
        // Floor at 1s so a misconfigured 0 doesn't immediately TLE
        // every preview.
        return Math.max(1, sandboxConfig.timeout());
    }

    private int deriveDefaultMemoryMb() {
        // sandboxConfig.memory() is a docker-style string such as
        // "256m" / "1g". Parse the numeric prefix into MiB. Anything
        // we can't parse falls back to 256 MiB.
        return parseMemoryMbOrDefault(sandboxConfig.memory(), 256);
    }

    /**
     * Parse a docker-style memory string into integer MiB. Supports
     * {@code "256m" / "1g" / "512M" / "1024"} (no suffix = MiB
     * for backwards compatibility with the legacy config that
     * emitted bare integers). Unknown units fall back to
     * {@code defaultMb}.
     */
    static int parseMemoryMbOrDefault(String memory, int defaultMb) {
        if (memory == null || memory.isBlank()) {
            return defaultMb;
        }
        String s = memory.trim();
        int suffixStart = s.length();
        char last = s.charAt(suffixStart - 1);
        if (Character.isLetter(last)) {
            suffixStart = suffixStart - 1;
        }
        long n;
        try {
            n = Long.parseLong(s.substring(0, suffixStart).trim());
        } catch (NumberFormatException e) {
            return defaultMb;
        }
        String unit = s.substring(suffixStart).toLowerCase();
        return switch (unit) {
            case "" -> (int) Math.max(1, n);     // bare integer → MiB
            case "b" -> (int) Math.max(1, n / (1024L * 1024L));
            case "k" -> (int) Math.max(1, n / 1024L);
            case "m" -> (int) Math.max(1, n);
            case "g" -> (int) Math.max(1, n * 1024L);
            default -> defaultMb;
        };
    }
}
