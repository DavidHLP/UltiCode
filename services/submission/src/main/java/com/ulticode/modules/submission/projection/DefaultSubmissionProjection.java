package com.ulticode.modules.submission.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.dto.LanguageStatsDTO;
import com.ulticode.app.api.dto.LearningProgressDTO;
import com.ulticode.app.api.dto.MonthlySubmissionStatsDTO;
import com.ulticode.app.api.dto.PerformanceStats;
import com.ulticode.app.api.dto.SubmissionDetailVO;
import com.ulticode.app.api.dto.SubmissionHistoryDTO;
import com.ulticode.app.api.dto.SubmissionStatusMeta;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.dto.WeeklyProgressDTO;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.domain.submission.enums.CaseScope;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.SubmissionStatusCatalog;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Write-path projection adapter for {@code backend-submission}.
 *
 * <p>SPLIT-003 slice-2 copy of the P0-1 security projection from the App
 * adapter: user-visible (SAMPLE/null=legacy) test details are exposed,
 * HIDDEN cases are filtered, and the first failing detail is extracted
 * without leaking hidden-case inputs/outputs. Read-side enrichments
 * (user/problem summaries) are omitted here — the App-side adapter supplies
 * them on the read path until SPLIT-004.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSubmissionProjection implements SubmissionProjection {

    /** Keys inspected in order when extracting a numeric value from a bin map. */
    private static final String[] BIN_KEYS = {"value", "bin", "min", "max", "count"};

    private final SubmissionMapper submissionMapper;
    private final SubmissionUserReadPort userReadPort;
    private final ProblemFactsPort problemFacts;
    private final ObjectMapper objectMapper;

    @Override
    public SubmissionVO toVO(Submission submission) {
        SubmissionVO vo = new SubmissionVO();

        vo.setId(submission.getId());
        vo.setProblemId(submission.getProblemId());
        vo.setUserId(submission.getUserId());
        vo.setLanguage(submission.getLanguage());
        vo.setCode(submission.getCode());
        vo.setStatus(submission.getStatus());
        vo.setRuntime(submission.getRuntime());
        vo.setMemory(submission.getMemory());
        vo.setNotes(submission.getNotes());
        vo.setCreatedAt(submission.getCreatedAt());
        vo.setRuntimePercentile(submission.getRuntimePercentile());
        vo.setMemoryPercentile(submission.getMemoryPercentile());

        // P0-1 security projection: filter testDetails by user visibility
        // (SAMPLE / null=legacy sample) for vo.tests; skip HIDDEN entirely.
        if (submission.getTestDetails() != null && !submission.getTestDetails().isEmpty()) {
            List<Submission.TestCaseDetail> userVisibleDetails = new ArrayList<>();
            Submission.TestCaseDetail sampleFirstFailure = null;
            Submission.TestCaseDetail hiddenFirstFailure = null;
            for (Submission.TestCaseDetail detail : submission.getTestDetails()) {
                if (CaseScope.isUserVisible(detail.getCaseScope())) {
                    userVisibleDetails.add(detail);
                    if (sampleFirstFailure == null && detail.getStatus() != null
                            && !"Accepted".equals(detail.getStatus())) {
                        sampleFirstFailure = detail;
                    }
                } else if (hiddenFirstFailure == null && detail.getStatus() != null
                        && !"Accepted".equals(detail.getStatus())) {
                    hiddenFirstFailure = detail;
                }
            }

            List<SubmissionVO.TestResult> tests = new ArrayList<>(userVisibleDetails.size());
            for (int i = 0; i < userVisibleDetails.size(); i++) {
                Submission.TestCaseDetail detail = userVisibleDetails.get(i);
                SubmissionVO.TestResult test = new SubmissionVO.TestResult();
                test.setId("test-" + submission.getId() + "-" + (i + 1));
                test.setStatus(detail.getStatus() != null ? detail.getStatus() : submission.getStatus());
                test.setRuntime(detail.getTime() != null ? detail.getTime() : submission.getRuntime());
                test.setMemory(detail.getMemory() != null ? detail.getMemory() : submission.getMemory());
                tests.add(test);
            }
            vo.setTests(tests);

            Submission.TestCaseDetail failureToExpose = sampleFirstFailure != null
                    ? sampleFirstFailure
                    : hiddenFirstFailure;
            if (failureToExpose != null) {
                if ("Compile Error".equals(failureToExpose.getStatus())) {
                    vo.setCompilerError(failureToExpose.getDetail());
                }
                vo.setErrorDetail(failureToExpose.getDetail());

                if (sampleFirstFailure != null) {
                    if (failureToExpose.getInputs() != null && !failureToExpose.getInputs().isEmpty()) {
                        StringBuilder inputBuilder = new StringBuilder();
                        for (Submission.TestCaseDetail.InputParam input : failureToExpose.getInputs()) {
                            if (inputBuilder.length() > 0) {
                                inputBuilder.append(", ");
                            }
                            inputBuilder.append(input.getName()).append(" = ").append(input.getValue());
                        }
                        vo.setInput(inputBuilder.toString());
                    }
                    vo.setOutput(failureToExpose.getOutput());
                    vo.setExpectedOutput(failureToExpose.getExpectedOutput());
                }
            }
        }

        applyUserSummary(vo, submission.getUserId());
        ProblemFactsPort.ProblemDisplayFacts facts =
                problemFacts != null ? problemFacts.findDisplayFacts(submission.getProblemId()) : null;
        applyProblemSummary(vo, facts);

        return vo;
    }

    @Override
    public SubmissionVO toVO(Submission submission,
                             Map<Long, ProblemFactsPort.ProblemDisplayFacts> batchFacts) {
        SubmissionVO vo = toVO(submission);
        ProblemFactsPort.ProblemDisplayFacts facts =
                batchFacts != null ? batchFacts.get(submission.getProblemId()) : null;
        if (facts != null) {
            applyProblemSummary(vo, facts);
        }
        return vo;
    }

    private void applyUserSummary(SubmissionVO vo, String userId) {
        SubmissionUserReadPort.UserSummary user =
                userReadPort != null ? userReadPort.findById(userId) : null;
        if (user == null) {
            return;
        }
        SubmissionVO.UserInfo userInfo = new SubmissionVO.UserInfo();
        userInfo.setId(user.id());
        userInfo.setUsername(user.username());
        userInfo.setName(user.name());
        userInfo.setAvatar(user.avatar());
        vo.setUser(userInfo);
    }

    private void applyProblemSummary(SubmissionVO vo, ProblemFactsPort.ProblemDisplayFacts facts) {
        if (facts == null) {
            return;
        }
        SubmissionVO.ProblemInfo problemInfo = new SubmissionVO.ProblemInfo();
        problemInfo.setId(facts.id());
        problemInfo.setTitle(facts.title());
        problemInfo.setSlug(facts.slug());
        vo.setProblem(problemInfo);
    }

    @Override
    public SubmissionDetailVO toDetailVO(Submission submission, PerformanceStats stats) {
        SubmissionVO baseVo = toVO(submission);

        SubmissionDetailVO vo = new SubmissionDetailVO();
        BeanUtils.copyProperties(baseVo, vo);

        if (baseVo.getUser() != null) {
            SubmissionDetailVO.UserInfo userInfo = new SubmissionDetailVO.UserInfo();
            userInfo.setId(baseVo.getUser().getId());
            userInfo.setUsername(baseVo.getUser().getUsername());
            userInfo.setName(baseVo.getUser().getName());
            userInfo.setAvatar(baseVo.getUser().getAvatar());
            vo.setUser(userInfo);
        }

        if (baseVo.getProblem() != null) {
            SubmissionDetailVO.ProblemInfo problemInfo = new SubmissionDetailVO.ProblemInfo();
            problemInfo.setId(baseVo.getProblem().getId());
            problemInfo.setTitle(baseVo.getProblem().getTitle());
            problemInfo.setSlug(baseVo.getProblem().getSlug());
            vo.setProblem(problemInfo);
        }

        if (baseVo.getTests() != null) {
            List<SubmissionDetailVO.TestResult> tests = baseVo.getTests().stream()
                    .map(t -> {
                        SubmissionDetailVO.TestResult r = new SubmissionDetailVO.TestResult();
                        r.setId(t.getId());
                        r.setStatus(t.getStatus());
                        r.setRuntime(t.getRuntime());
                        r.setMemory(t.getMemory());
                        return r;
                    })
                    .toList();
            vo.setTests(tests);
        }

        if (stats != null) {
            vo.setRuntimePercentile(stats.runtimePercentile());
            vo.setRuntimeDistBinsMs(normalizeBins(stats.runtimeDistBinsMs()));
            vo.setMemoryPercentile(stats.memoryPercentile());
            vo.setMemoryDistBinsMb(normalizeBins(stats.memoryDistBinsMb()));
        } else {
            vo.setRuntimeDistBinsMs(normalizeBins(submission.getRuntimeDistBinsMs()));
            vo.setMemoryDistBinsMb(normalizeBins(submission.getMemoryDistBinsMb()));
        }

        return vo;
    }

    private List<Integer> normalizeBins(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<Integer> out = new ArrayList<>(list.size());
            for (Object item : list) {
                Integer v = extractIntegerValue(item);
                if (v != null) {
                    out.add(v);
                }
            }
            return out;
        }
        if (raw instanceof String s) {
            try {
                Object parsed = objectMapper.readValue(s, Object.class);
                return normalizeBins(parsed);
            } catch (Exception e) {
                log.debug("Failed to parse bins JSON string: {}", s, e);
                return List.of();
            }
        }
        return List.of();
    }

    private static Integer extractIntegerValue(Object item) {
        if (item == null) return null;
        if (item instanceof Number n) return n.intValue();
        if (item instanceof Map<?, ?> map) {
            for (String key : BIN_KEYS) {
                Object v = map.get(key);
                if (v instanceof Number n) return n.intValue();
            }
        }
        return null;
    }

    @Override
    public List<String> aggregateDates(String userId, Integer year) {
        return submissionMapper.findSubmissionDatesByYear(userId, year);
    }

    @Override
    public LearningProgressDTO aggregateLearningProgress(String userId) {
        LearningProgressDTO progress = new LearningProgressDTO();

        List<WeeklyProgressDTO> weeklyData = submissionMapper.findWeeklyProgress(userId);
        List<LearningProgressDTO.WeeklyProgress> weeklyProgress = weeklyData.stream()
                .map(row -> new LearningProgressDTO.WeeklyProgress(
                        row.getWeekRange(),
                        row.getSolvedCount(),
                        row.getTimeSpentHours()))
                .toList();
        progress.setWeeklyProgress(weeklyProgress);

        // Difficulty progress reserved for a follow-up; matches pre-deepening shape.
        progress.setDifficultyProgress(new ArrayList<>());

        int totalProblems = weeklyProgress.stream()
                .mapToInt(LearningProgressDTO.WeeklyProgress::getSolved)
                .sum();
        double totalTimeHours = weeklyProgress.stream()
                .mapToDouble(LearningProgressDTO.WeeklyProgress::getTimeSpent)
                .sum();

        progress.setTotalProblems(totalProblems);
        progress.setTotalTimeHours(totalTimeHours);
        progress.setAvgTimePerProblem(totalProblems > 0 ? totalTimeHours / totalProblems : 0);

        Integer streak = submissionMapper.calculateStreak(userId);
        progress.setCurrentStreak(streak == null ? 0 : streak);
        progress.setLongestStreak(progress.getCurrentStreak());

        return progress;
    }

    @Override
    public SubmissionHistoryDTO aggregateHistory(String userId) {
        SubmissionHistoryDTO history = new SubmissionHistoryDTO();

        List<MonthlySubmissionStatsDTO> monthlyData = submissionMapper.findMonthlySubmissionStats(userId);
        List<SubmissionHistoryDTO.MonthlySubmission> monthly = monthlyData.stream()
                .map(row -> new SubmissionHistoryDTO.MonthlySubmission(
                        row.getMonth(),
                        row.getTotalCount(),
                        row.getAcceptedCount()))
                .toList();
        history.setMonthly(monthly);

        List<LanguageStatsDTO> languageData = submissionMapper.findLanguageStats(userId);
        List<SubmissionHistoryDTO.LanguageSubmission> languages = languageData.stream()
                .map(row -> new SubmissionHistoryDTO.LanguageSubmission(
                        row.getLanguage(),
                        row.getCount()))
                .toList();
        history.setLanguages(languages);

        int totalSubmissions = monthly.stream()
                .mapToInt(SubmissionHistoryDTO.MonthlySubmission::getCount)
                .sum();
        int totalAccepted = monthly.stream()
                .mapToInt(SubmissionHistoryDTO.MonthlySubmission::getAccepted)
                .sum();

        history.setTotalSubmissions(totalSubmissions);
        history.setTotalAccepted(totalAccepted);
        history.setAcceptanceRate(totalSubmissions > 0 ? (double) totalAccepted / totalSubmissions : 0);

        return history;
    }

    @Override
    public List<SubmissionStatusMeta> getStatusCatalog() {
        return Arrays.stream(com.ulticode.domain.submission.enums.SubmissionStatus.values())
                .map(s -> toStatusMeta(s, SubmissionStatusCatalog.forStatus(s)))
                .toList();
    }

    private SubmissionStatusMeta toStatusMeta(com.ulticode.domain.submission.enums.SubmissionStatus status,
                                              SubmissionStatusCatalog.Entry entry) {
        SubmissionStatusMeta meta = new SubmissionStatusMeta();
        meta.setKey(status.getDisplayName());
        meta.setCode(status.name());
        meta.setLabel(status.getDisplayName());
        if (entry != null) {
            meta.setDescription(entry.description());
            meta.setSuggestion(entry.suggestion());
            meta.setSeverity(entry.severity());
            meta.setSortOrder(entry.sortOrder());
        }
        meta.setCategory(status.getCategory());
        meta.setIsTerminal(status.isTerminal());
        return meta;
    }
}
