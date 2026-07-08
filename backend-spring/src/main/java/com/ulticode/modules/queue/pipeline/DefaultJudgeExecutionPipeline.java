package com.ulticode.modules.queue.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.config.JudgeSourceProperties;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.CaseScope;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.VerdictResolver;
import com.ulticode.modules.submission.enums.SubmissionStatus;
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
 * Owns the two execution paths (canonical {@code test_cases} table vs
 * legacy {@code problem_examples}) and the branching on
 * {@link JudgeSourceProperties#isUseTestCases()}.
 *
 * The DTO-building, metric-extraction, and verdict-resolution logic that
 * was inline in {@code JudgeWorkerProcessor} lives here so it can be tested
 * without the worker's queue/lease/push collaborators.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultJudgeExecutionPipeline implements JudgeExecutionPipeline {

    private final TestCaseMapper testCaseMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final CodeExecutionService codeExecutionService;
    private final JudgeSourceProperties judgeSourceProperties;
    private final ObjectMapper objectMapper;
    private final VerdictResolver verdictResolver;
    private final VerdictMetricsParser verdictMetricsParser;

    @Override
    public JudgeExecutionResult execute(
            String language,
            String code,
            long problemId,
            String userId,
            String submissionId
    ) throws Exception {

        if (judgeSourceProperties.isUseTestCases()) {
            return executeWithTestCases(language, code, problemId, userId, submissionId);
        }
        return executeWithProblemExamples(language, code, problemId, userId, submissionId);
    }

    // -----------------------------------------------------------------------
    // Path 1: canonical test_cases table (P0-1 primary)
    // -----------------------------------------------------------------------

    private JudgeExecutionResult executeWithTestCases(
            String language, String code, long problemId, String userId, String submissionId) {

        List<TestCase> cases = testCaseMapper.findActiveCasesForJudging(problemId);
        if (cases == null || cases.isEmpty()) {
            log.warn("No eligible test_cases for problem {} (fail closed: System Error, no problem_examples fallback)",
                    problemId);
            return null;
        }

        Map<String, TestCase> byId = cases.stream().collect(
                Collectors.toMap(TestCase::getId, Function.identity(), (a, b) -> a));

        RunSubmissionDTO runDto = buildRunSubmissionDTOFromTestCases(language, code, cases);
        RunResultDTO result = codeExecutionService.execute(runDto, problemId, userId);

        String verdict = determineVerdict(result.getCases());
        long maxRuntimeMs = 0;
        double maxMemoryMb = 0.0;
        for (RunResultDTO.RunCaseResult caseResult : result.getCases()) {
            maxRuntimeMs = Math.max(maxRuntimeMs, verdictMetricsParser.parseRuntimeMs(caseResult.getRuntime()));
            maxMemoryMb = Math.max(maxMemoryMb, verdictMetricsParser.parseMemoryMb(caseResult.getMemory()));
        }

        List<Submission.TestCaseDetail> testCaseDetails = buildTestCaseDetailsWithScope(
                result.getCases(), byId, submissionId, String.valueOf(problemId));

        return new JudgeExecutionResult(verdict, (int) maxRuntimeMs, maxMemoryMb, testCaseDetails);
    }

    // -----------------------------------------------------------------------
    // Path 2: legacy problem_examples table
    // -----------------------------------------------------------------------

    private JudgeExecutionResult executeWithProblemExamples(
            String language, String code, long problemId, String userId, String submissionId) {

        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(problemId);
        if (examples == null || examples.isEmpty()) {
            log.warn("No problem examples found for problem {}", problemId);
            return null;
        }

        RunSubmissionDTO runDto = buildRunSubmissionDTO(language, code, examples);
        RunResultDTO result = codeExecutionService.execute(runDto, problemId, userId);

        String verdict = determineVerdict(result.getCases());
        long maxRuntimeMs = 0;
        double maxMemoryMb = 0.0;
        for (RunResultDTO.RunCaseResult caseResult : result.getCases()) {
            maxRuntimeMs = Math.max(maxRuntimeMs, verdictMetricsParser.parseRuntimeMs(caseResult.getRuntime()));
            maxMemoryMb = Math.max(maxMemoryMb, verdictMetricsParser.parseMemoryMb(caseResult.getMemory()));
        }

        List<Submission.TestCaseDetail> testCaseDetails = result.getCases().stream()
                .map(cr -> {
                    Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
                    detail.setStatus(cr.getStatus());
                    detail.setTime((int) verdictMetricsParser.parseRuntimeMs(cr.getRuntime()));
                    detail.setMemory(verdictMetricsParser.parseMemoryMb(cr.getMemory()));
                    detail.setOutput(cr.getOutput());
                    detail.setExpectedOutput(cr.getExpectedOutput());
                    detail.setDetail(cr.getDetail());
                    return detail;
                })
                .toList();

        return new JudgeExecutionResult(verdict, (int) maxRuntimeMs, maxMemoryMb, testCaseDetails);
    }

    // -----------------------------------------------------------------------
    // Verdict resolution
    // -----------------------------------------------------------------------

    String determineVerdict(List<RunResultDTO.RunCaseResult> cases) {
        if (cases == null || cases.isEmpty()) {
            return SubmissionStatus.SYSTEM_ERROR.wireValue();
        }
        List<String> caseWireValues = new ArrayList<>(cases.size());
        for (RunResultDTO.RunCaseResult caseResult : cases) {
            caseWireValues.add(caseResult.getStatus());
        }
        return verdictResolver.reduceWire(caseWireValues).wireValue();
    }

    // -----------------------------------------------------------------------
    // TestCaseDetail building (test_cases path — with caseId + caseScope)
    // -----------------------------------------------------------------------

    private List<Submission.TestCaseDetail> buildTestCaseDetailsWithScope(
            List<RunResultDTO.RunCaseResult> caseResults,
            Map<String, TestCase> byId,
            String submissionId,
            String problemId) {
        List<Submission.TestCaseDetail> details = new ArrayList<>(caseResults.size());
        for (RunResultDTO.RunCaseResult cr : caseResults) {
            Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
            detail.setStatus(cr.getStatus());
            detail.setTime((int) verdictMetricsParser.parseRuntimeMs(cr.getRuntime()));
            detail.setMemory(verdictMetricsParser.parseMemoryMb(cr.getMemory()));
            detail.setOutput(cr.getOutput());
            detail.setExpectedOutput(cr.getExpectedOutput());
            detail.setDetail(cr.getDetail());

            String tcId = cr.getTestCaseId();
            if (tcId != null && !tcId.isBlank()) {
                TestCase tc = byId.get(tcId);
                if (tc != null) {
                    detail.setCaseId(tc.getId());
                    if (Boolean.TRUE.equals(tc.getIsHidden())) {
                        detail.setCaseScope(CaseScope.HIDDEN);
                    } else if (Boolean.TRUE.equals(tc.getIsSample())) {
                        detail.setCaseScope(CaseScope.SAMPLE);
                    }
                } else {
                    log.error("sandbox returned testCaseId={} not in test_cases for submission={} problem={} (recording system error, no hidden I/O logged)",
                            tcId, submissionId, problemId);
                }
            }
            details.add(detail);
        }
        return details;
    }

    // -----------------------------------------------------------------------
    // RunSubmissionDTO building
    // -----------------------------------------------------------------------

    private RunSubmissionDTO buildRunSubmissionDTO(String language, String code, List<ProblemExample> examples) {
        RunSubmissionDTO runDto = new RunSubmissionDTO();
        runDto.setLanguage(language);
        runDto.setCode(code);
        runDto.setTestCases(examples.stream().map(tc -> {
            RunSubmissionDTO.RunTestCase rtc = new RunSubmissionDTO.RunTestCase();
            rtc.setId(String.valueOf(tc.getId()));
            rtc.setLabel("Case " + tc.getExampleOrder());
            rtc.setOutput(tc.getOutputText());
            rtc.setInputs(parseInputs(tc.getInputs(), tc.getInputText(), tc.getId()));
            return rtc;
        }).toList());
        return runDto;
    }

    private RunSubmissionDTO buildRunSubmissionDTOFromTestCases(String language, String code, List<TestCase> cases) {
        RunSubmissionDTO runDto = new RunSubmissionDTO();
        runDto.setLanguage(language);
        runDto.setCode(code);
        runDto.setTestCases(cases.stream().map(tc -> {
            RunSubmissionDTO.RunTestCase rtc = new RunSubmissionDTO.RunTestCase();
            rtc.setId(tc.getId());
            rtc.setLabel("Case " + tc.getTestOrder());
            rtc.setOutput(tc.getOutputText());
            rtc.setInputs(parseInputs(tc.getInputs(), tc.getInputText(), tc.getId()));
            return rtc;
        }).toList());
        return runDto;
    }

    /**
     * Parse the JSON inputs array from a test case / problem example.
     * Falls back to wrapping inputText as a single input if JSON is absent or malformed.
     */
    private List<RunSubmissionDTO.RunInput> parseInputs(String inputsJson, String inputText, Object entityId) {
        List<RunSubmissionDTO.RunInput> runInputs = new ArrayList<>();
        if (inputsJson != null && !inputsJson.isBlank()) {
            try {
                List<Map<String, Object>> inputs = objectMapper.readValue(
                        inputsJson, new TypeReference<List<Map<String, Object>>>() {});
                for (int i = 0; i < inputs.size(); i++) {
                    Map<String, Object> item = inputs.get(i);
                    RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
                    ri.setId(String.valueOf(i));
                    Object nameObj = item.get("name");
                    Object labelObj = item.get("label");
                    String name = (nameObj != null ? nameObj : (labelObj != null ? labelObj : "input")).toString();
                    ri.setLabel(name);
                    ri.setName(name);
                    Object valueObj = item.get("value");
                    ri.setValue(valueObj != null ? valueObj.toString() : "");
                    Object typeObj = item.get("type");
                    if (typeObj != null && !typeObj.toString().isBlank()) {
                        ri.setType(typeObj.toString());
                    }
                    runInputs.add(ri);
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse inputs JSON for entity {}, falling back to inputText", entityId);
            }
        }
        if (runInputs.isEmpty() && inputText != null) {
            RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
            ri.setId("0");
            ri.setLabel("input");
            ri.setName("input");
            ri.setValue(inputText);
            runInputs.add(ri);
        }
        return runInputs;
    }
}
