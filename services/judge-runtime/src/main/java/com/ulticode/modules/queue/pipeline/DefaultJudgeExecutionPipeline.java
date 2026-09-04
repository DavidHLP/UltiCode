package com.ulticode.modules.queue.pipeline;

import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.JudgingCaseSource;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.modules.submission.runtime.JudgeRunResponse;
import com.ulticode.modules.submission.runtime.JudgeRunRequest;
import com.ulticode.domain.submission.enums.CaseScope;
import com.ulticode.modules.submission.port.VerdictResolvePort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link JudgeExecutionPipeline}.
 *
 * <p>Depends only on the queue-owned {@link JudgingCaseSource} seam: case
 * loading, source selection (canonical {@code test_cases} vs legacy
 * {@code problem_examples}), and inputs parsing all live behind it. This
 * pipeline owns what is the same across sources — sandbox dispatch, verdict
 * resolution, metric extraction, and per-case detail shaping — so the Problem
 * module's mappers, entities, and source-selection flag no longer leak inward.
 *
 * <p>The DTO-building, metric-extraction, and verdict-resolution logic that
 * was inline in {@code JudgeWorkerProcessor} lives here so it can be tested
 * without the worker's queue/lease/push collaborators.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!test")
@RequiredArgsConstructor
public class DefaultJudgeExecutionPipeline implements JudgeExecutionPipeline {

    private final JudgingCaseSource judgingCaseSource;
    private final com.ulticode.modules.submission.service.CodeExecutionService codeExecutionService;
    private final VerdictResolvePort verdictResolver;
    private final VerdictMetricsParser verdictMetricsParser;

    @Override
    public JudgeExecutionResult execute(
            String language, String code, long problemId, String userId, String submissionId) throws Exception {

        List<JudgingCase> cases = judgingCaseSource.loadCases(problemId);
        if (cases == null || cases.isEmpty()) {
            log.warn("No eligible judging cases for problem {} (fail closed: System Error)", problemId);
            return null;
        }

        Map<String, JudgingCase> byId = cases.stream().collect(
                Collectors.toMap(JudgingCase::getId, Function.identity(), (a, b) -> a));

        JudgeRunRequest runDto = buildJudgeRunRequest(language, code, cases);
        JudgeRunResponse result = codeExecutionService.execute(runDto, problemId, userId);

        SubmissionStatus status = determineVerdict(result.getCases());
        long maxRuntimeMs = 0;
        double maxMemoryMb = 0.0;
        for (JudgeRunResponse.RunCaseResult caseResult : result.getCases()) {
            maxRuntimeMs = Math.max(maxRuntimeMs, verdictMetricsParser.parseRuntimeMs(caseResult.getRuntime()));
            maxMemoryMb = Math.max(maxMemoryMb, verdictMetricsParser.parseMemoryMb(caseResult.getMemory()));
        }

        List<JudgeTestCaseDetail> testCaseDetails = buildTestCaseDetails(
                result.getCases(), byId, submissionId, String.valueOf(problemId));

        return new JudgeExecutionResult(status, (int) maxRuntimeMs, maxMemoryMb, testCaseDetails);
    }

    // -----------------------------------------------------------------------
    // Verdict resolution
    // -----------------------------------------------------------------------

    SubmissionStatus determineVerdict(List<JudgeRunResponse.RunCaseResult> cases) {
        if (cases == null || cases.isEmpty()) {
            return SubmissionStatus.SYSTEM_ERROR;
        }
        List<String> caseWireValues = new ArrayList<>(cases.size());
        for (JudgeRunResponse.RunCaseResult caseResult : cases) {
            caseWireValues.add(caseResult.getStatus());
        }
        return verdictResolver.reduceWire(caseWireValues);
    }

    // -----------------------------------------------------------------------
    // TestCaseDetail building (caseId + caseScope resolved via JudgingCase)
    // -----------------------------------------------------------------------

    private List<JudgeTestCaseDetail> buildTestCaseDetails(
            List<JudgeRunResponse.RunCaseResult> caseResults,
            Map<String, JudgingCase> byId,
            String submissionId,
            String problemId) {
        List<JudgeTestCaseDetail> details = new ArrayList<>(caseResults.size());
        for (JudgeRunResponse.RunCaseResult cr : caseResults) {
            String caseId = null;
            CaseScope caseScope = null;
            String tcId = cr.getTestCaseId();
            if (tcId != null && !tcId.isBlank()) {
                JudgingCase tc = byId.get(tcId);
                if (tc != null) {
                    // caseId is now stamped uniformly for BOTH sources. The
                    // legacy problem_examples path left it null pre-refactor;
                    // sealing case loading behind JudgingCaseSource makes every
                    // case carry a stable id, so downstream must not treat a
                    // null caseId as a "legacy submission" marker.
                    caseId = tc.getId();
                    if (Boolean.TRUE.equals(tc.getHidden())) {
                        caseScope = CaseScope.HIDDEN;
                    } else if (Boolean.TRUE.equals(tc.getSample())) {
                        caseScope = CaseScope.SAMPLE;
                    }
                } else {
                    log.error("sandbox returned testCaseId={} not in judging cases for submission={} problem={} (recording system error, no hidden I/O logged)",
                            tcId, submissionId, problemId);
                }
            }
            details.add(new JudgeTestCaseDetail(
                    cr.getStatus(),
                    (int) verdictMetricsParser.parseRuntimeMs(cr.getRuntime()),
                    verdictMetricsParser.parseMemoryMb(cr.getMemory()),
                    cr.getDetail(),
                    cr.getOutput(),
                    cr.getExpectedOutput(),
                    null,
                    caseId,
                    caseScope));
        }
        return details;
    }

    // -----------------------------------------------------------------------
    // JudgeRunRequest building
    // -----------------------------------------------------------------------

    private JudgeRunRequest buildJudgeRunRequest(
            String language, String code, List<JudgingCase> cases) {
        JudgeRunRequest runDto = new JudgeRunRequest();
        runDto.setLanguage(language);
        runDto.setCode(code);
        runDto.setTestCases(cases.stream().map(tc -> {
            JudgeRunRequest.TestCase rtc = new JudgeRunRequest.TestCase();
            rtc.setId(tc.getId());
            rtc.setLabel(tc.getLabel());
            rtc.setOutput(tc.getOutputText());
            rtc.setInputs(tc.getInputs());
            return rtc;
        }).toList());
        return runDto;
    }
}
