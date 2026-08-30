package com.ulticode.modules.submission.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.submission.api.dto.LanguageStatsDTO;
import com.ulticode.submission.api.dto.LearningProgressDTO;
import com.ulticode.submission.api.dto.MonthlySubmissionStatsDTO;
import com.ulticode.submission.api.dto.PerformanceStats;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionHistoryDTO;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.dto.WeeklyProgressDTO;
import com.ulticode.submission.api.catalog.SubmissionStatusCatalog;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.domain.submission.enums.CaseScope;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.modules.submission.stats.SubmissionStreakCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default adapter of {@link SubmissionProjection}.
 *
 * <p>Sits at the same seam as any future read-side adapter (e.g. a cached or
 * pre-materialised read-model adapter would satisfy the same interface).
 * The state-change service still depends on this interface, not on the four
 * mappers it used to need.
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.mode:dev-lite}' == 'legacy-rollback'")
@RequiredArgsConstructor
public class DefaultSubmissionProjection implements SubmissionProjection {

    /** Keys inspected in order when extracting a numeric value from a bin map. */
    private static final String[] BIN_KEYS = {"value", "bin", "min", "max", "count"};

    private final SubmissionMapper submissionMapper;
    private final SubmissionStreakCalculator submissionStreakCalculator;
    private final SubmissionUserReadPort userReadPort;
    private final ProblemFactsPort problemFacts;
    private final ObjectMapper objectMapper;

    @Override
    public SubmissionListItemVO toListItemVO(SubmissionMapper.SubmissionWithProblem submission) {
        SubmissionListItemVO vo = new SubmissionListItemVO();
        vo.setId(submission.id());
        vo.setStatus(submission.status());
        vo.setLanguage(submission.language());
        vo.setRuntime(submission.runtime());
        vo.setMemory(submission.memory());
        vo.setCreatedAt(submission.createdAt());
        vo.setNotes(submission.notes());

        if (submission.problemTitle() != null) {
            SubmissionListItemVO.ProblemSummary problemSummary = new SubmissionListItemVO.ProblemSummary();
            problemSummary.setId(submission.problemId());
            problemSummary.setTitle(submission.problemTitle());
            problemSummary.setSlug(submission.problemSlug());
            vo.setProblem(problemSummary);
        }

        return vo;
    }

    @Override
    public SubmissionDetailVO toDetailVO(Submission submission, PerformanceStats stats) {
        return toDetailVO(submission, stats, null);
    }

    @Override
    public SubmissionDetailVO toDetailVO(
            Submission submission,
            PerformanceStats stats,
            Map<Long, ProblemFactsPort.ProblemDisplayFacts> batchFacts) {
        SubmissionVO baseVo;
        if (batchFacts == null) {
            baseVo = toVO(submission);
        } else {
            List<String> userIds = submission.getUserId() == null
                    ? List.of()
                    : List.of(submission.getUserId());
            baseVo = toVO(submission, findUsers(userIds), batchFacts);
        }

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

    @Override
    public SubmissionVO toVO(Submission submission) {
        return toVOs(List.of(submission)).get(0);
    }

    private SubmissionVO toVO(
            Submission submission,
            Map<String, SubmissionUserReadPort.UserSummary> users,
            Map<Long, ProblemFactsPort.ProblemDisplayFacts> factsById) {
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
        vo.setMemoryDistBinsMb(normalizeBins(submission.getMemoryDistBinsMb()));

        // P0-1 security projection: filter testDetails by user visibility
        // (SAMPLE / null=legacy sample) for vo.tests; skip HIDDEN entirely.
        if (submission.getTestDetails() != null && !submission.getTestDetails().isEmpty()) {
            List<Submission.TestCaseDetail> userVisibleDetails = new ArrayList<>();
            List<Submission.TestCaseDetail> hiddenDetails = new ArrayList<>();
            for (Submission.TestCaseDetail detail : submission.getTestDetails()) {
                if (CaseScope.isUserVisible(detail.getCaseScope())) {
                    userVisibleDetails.add(detail);
                } else {
                    hiddenDetails.add(detail);
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

            // First-failing detail extraction (P0-1):
            //   Prefer the first USER-VISIBLE (SAMPLE / null=legacy) failing detail
            //   so the user sees their own sample I/O + error.
            //   If only HIDDEN cases failed, set vo.errorDetail only (without input /
            //   output / expectedOutput) so the user knows something failed without
            //   leaking hidden case contents.
            Submission.TestCaseDetail sampleFirstFailure = null;
            Submission.TestCaseDetail hiddenFirstFailure = null;
            for (Submission.TestCaseDetail detail : submission.getTestDetails()) {
                if (detail.getStatus() == null || "Accepted".equals(detail.getStatus())) {
                    continue;
                }
                if (CaseScope.isUserVisible(detail.getCaseScope()) && sampleFirstFailure == null) {
                    sampleFirstFailure = detail;
                } else if (!CaseScope.isUserVisible(detail.getCaseScope()) && hiddenFirstFailure == null) {
                    hiddenFirstFailure = detail;
                }
            }

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

        applyUserSummary(vo, users.get(submission.getUserId()));

        ProblemFactsPort.ProblemDisplayFacts facts = factsById.get(submission.getProblemId());
        if (facts != null) {
            SubmissionVO.ProblemInfo problemInfo = new SubmissionVO.ProblemInfo();
            problemInfo.setId(facts.id());
            problemInfo.setTitle(facts.title());
            problemInfo.setSlug(facts.slug());
            vo.setProblem(problemInfo);
        }

        return vo;
    }

    @Override
    public SubmissionVO toVO(SubmissionMapper.SubmissionWithProblem submission) {
        return toVO(submission, findUsers(List.of(submission.userId())));
    }

    @Override
    public List<SubmissionVO> toVO(List<SubmissionMapper.SubmissionWithProblem> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        Set<String> userIds = submissions.stream()
                .map(SubmissionMapper.SubmissionWithProblem::userId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, SubmissionUserReadPort.UserSummary> users = findUsers(userIds);
        return submissions.stream().map(row -> toVO(row, users)).toList();
    }

    @Override
    public List<SubmissionVO> toVOs(List<Submission> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        Set<String> userIds = submissions.stream()
                .map(Submission::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, SubmissionUserReadPort.UserSummary> users = findUsers(userIds);
        Set<Long> problemIds = submissions.stream()
                .map(Submission::getProblemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProblemFactsPort.ProblemDisplayFacts> facts = problemFacts == null
                ? Map.of()
                : problemFacts.findDisplayFactsBatch(problemIds);
        return submissions.stream().map(submission -> toVO(submission, users, facts)).toList();
    }

    private SubmissionVO toVO(
            SubmissionMapper.SubmissionWithProblem submission,
            Map<String, SubmissionUserReadPort.UserSummary> users) {
        SubmissionVO vo = new SubmissionVO();

        vo.setId(submission.id());
        vo.setProblemId(submission.problemId());
        vo.setUserId(submission.userId());
        vo.setLanguage(submission.language());
        vo.setCode(submission.code());
        vo.setStatus(submission.status());
        vo.setRuntime(submission.runtime());
        vo.setMemory(submission.memory());
        vo.setNotes(submission.notes());
        vo.setCreatedAt(submission.createdAt());
        vo.setRuntimePercentile(submission.runtimePercentile());
        vo.setMemoryPercentile(submission.memoryPercentile());
        vo.setMemoryDistBinsMb(normalizeBins(submission.memoryDistBinsMb()));

        applyUserSummary(vo, users.get(submission.userId()));

        if (submission.problemTitle() != null) {
            SubmissionVO.ProblemInfo problemInfo = new SubmissionVO.ProblemInfo();
            problemInfo.setId(submission.problemId());
            problemInfo.setTitle(submission.problemTitle());
            problemInfo.setSlug(submission.problemSlug());
            vo.setProblem(problemInfo);
        }

        return vo;
    }

    private void applyUserSummary(SubmissionVO vo, Object userObj) {
        if (userObj == null) {
            return;
        }
        SubmissionVO.UserInfo userInfo = new SubmissionVO.UserInfo();
        if (userObj instanceof SubmissionUserReadPort.UserSummary user) {
            userInfo.setId(user.id());
            userInfo.setUsername(user.username());
            userInfo.setName(user.name());
            userInfo.setAvatar(user.avatar());
        } else if (userObj instanceof Map<?, ?> map) {
            userInfo.setId(map.get("id") != null ? map.get("id").toString() : null);
            userInfo.setUsername(map.get("username") != null ? map.get("username").toString() : null);
            userInfo.setName(map.get("name") != null ? map.get("name").toString() : null);
            userInfo.setAvatar(map.get("avatar") != null ? map.get("avatar").toString() : null);
        }
        vo.setUser(userInfo);
    }

    private Map<String, SubmissionUserReadPort.UserSummary> findUsers(Iterable<String> userIds) {
        if (userReadPort == null) {
            return Map.of();
        }
        Map<String, SubmissionUserReadPort.UserSummary> users = userReadPort.findAllById(userIds);
        return users == null ? Map.of() : users;
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

        int streak = submissionStreakCalculator.computeStreak(userId);
        progress.setCurrentStreak(streak);
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
        return Arrays.stream(SubmissionStatus.values())
                .map(SubmissionStatusCatalog::toMeta)
                .toList();
    }

    /**
     * Normalise distribution bins into {@code List<Integer>} for JSON serialisation.
     *
     * <p>Accepts the various shapes the data may arrive in:
     * <ul>
     *   <li>{@code List<Integer>} — already the target shape, returned as-is (defensive copy).</li>
     *   <li>{@code String} — JSON-encoded array (from {@code JacksonTypeHandler}
     *       when the entity field is declared as {@code Object}). Parsed via Jackson.</li>
     *   <li>{@code List<Map<String, Number>>} — performance-stats shape with
     *       bin metadata. Extracted via the {@code value/bin/min/max/count} field.</li>
     *   <li>{@code null} / other — returns an empty list.</li>
     * </ul>
     */
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
}
