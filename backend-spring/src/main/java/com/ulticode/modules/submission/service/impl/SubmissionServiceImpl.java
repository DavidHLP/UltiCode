package com.ulticode.modules.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.LanguageStatsDTO;
import com.ulticode.modules.submission.dto.LearningProgressDTO;
import com.ulticode.modules.submission.dto.MonthlySubmissionStatsDTO;
import com.ulticode.modules.submission.dto.SubmissionHistoryDTO;
import com.ulticode.modules.submission.dto.SubmissionQueryDTO;
import com.ulticode.modules.submission.dto.SubmissionStatusMeta;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.dto.WeeklyProgressDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.websocket.service.RealtimeService;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.ContestSubmission;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of SubmissionService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;
    private final QueueService queueService;
    private final RealtimeService realtimeService;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final ContestMapper contestMapper;
    private final ContestParticipantMapper contestParticipantMapper;

    /**
     * Supported languages for submission.
     */
    private static final List<String> SUPPORTED_LANGUAGES = List.of(
            "javascript", "python", "java", "c", "cpp"
    );

    @Override
    @Transactional
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        // Validate user ID
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_USER_ID_REQUIRED);
        }

        // Validate code is not empty
        if (!StringUtils.hasText(createDTO.getCode())) {
            throw new BusinessException(ErrorCode.SUBMISSION_CODE_EMPTY);
        }

        // Validate language is supported
        String language = createDTO.getLanguage().toLowerCase();
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED);
        }

        // Verify problem exists
        Problem problem = problemMapper.selectById(createDTO.getProblemId());
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        // Verify user exists
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Create submission with Pending status
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID().toString());
        submission.setUserId(userId);
        submission.setProblemId(createDTO.getProblemId());
        submission.setLanguage(language);
        submission.setCode(createDTO.getCode());
        submission.setStatus("Pending");
        submission.setRuntime(0);
        submission.setMemory(0.0);
        submission.setCreatedAt(LocalDateTime.now());
        submission.setTestDetails(new ArrayList<>());

        // Save submission
        submissionMapper.insert(submission);

        log.info("Created submission {} for user {} and problem {}", submission.getId(), userId, createDTO.getProblemId());

        // --- Contest submission recording (D-04, D-05, D-06) ---
        try {
            recordContestSubmissionIfNeeded(submission.getId(), userId, createDTO.getProblemId());
        } catch (Exception e) {
            log.warn("Failed to record contest submission for submission {}", submission.getId(), e);
            // Don't fail the main submission -- contest recording is supplementary
        }

        try {
            queueService.enqueueJudgeJob(
                    submission.getId(),
                    String.valueOf(createDTO.getProblemId()),
                    userId,
                    language,
                    createDTO.getCode());
            log.info("Enqueued judge job for submission {}", submission.getId());
        // broad catch: enqueue failure falls back to system error status
        } catch (Exception e) {
            log.error("Failed to enqueue judge job for submission {}", submission.getId(), e);
            submission.setStatus("System Error");
            submission.setNotes("Judge queue unavailable — submission was not processed");
            submissionMapper.updateById(submission);
        }

        return toVO(submission);
    }

    @Override
    public SubmissionVO findById(String id, String userId) {
        Submission submission = submissionMapper.selectById(id);

        if (submission == null) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        // Access control: users can only see their own submissions
        if (StringUtils.hasText(userId) && !submission.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        return toVO(submission);
    }

    @Override
    public PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_USER_ID_REQUIRED);
        }

        int page = query.getPage() != null ? query.getPage() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;

        Page<Submission> pageParam = new Page<>(page, pageSize);
        IPage<SubmissionMapper.SubmissionWithProblem> result =
                submissionMapper.findByUserIdWithProblem(userId, pageParam);

        List<SubmissionVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();

        return PageResult.of(voList, result.getTotal(), page, pageSize);
    }

    @Override
    public PageResult<SubmissionVO> findByProblemId(Long problemId, String userId, SubmissionQueryDTO query) {
        int page = query.getPage() != null ? query.getPage() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;

        Page<Submission> pageParam = new Page<>(page, pageSize);
        IPage<SubmissionMapper.SubmissionWithProblem> result =
                submissionMapper.findByProblemIdWithProblem(problemId, userId, pageParam);

        List<SubmissionVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();

        return PageResult.of(voList, result.getTotal(), page, pageSize);
    }

    @Override
    public SubmissionVO findBest(Long problemId, String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_USER_ID_REQUIRED);
        }

        Optional<Submission> bestSubmission = submissionMapper.findBestByProblemIdAndUserId(problemId, userId);

        return bestSubmission.map(this::toVO).orElse(null);
    }

    @Override
    public Optional<Submission> getSubmissionEntity(String id) {
        return Optional.ofNullable(submissionMapper.selectById(id));
    }

    @Override
    public void updateSubmissionResult(String submissionId, String status, int runtime,
                                        Double memory, List<Submission.TestCaseDetail> testDetails) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            log.warn("Cannot update result: submission {} not found", submissionId);
            return;
        }
        submission.setStatus(status);
        submission.setRuntime(runtime);
        submission.setMemory(memory);
        submission.setTestDetails(testDetails);
        submissionMapper.updateById(submission);
        log.info("Updated submission {} status={}, runtime={}ms, memory={}",
                submissionId, status, runtime, memory != null ? memory + "MB" : "N/A");
    }

    /**
     * Overload: convert SubmissionWithProblem DTO to SubmissionVO using pre-loaded problem data.
     * Eliminates N+1 problem lookups in list views.
     */
    public SubmissionVO toVO(SubmissionMapper.SubmissionWithProblem submission) {
        SubmissionVO vo = new SubmissionVO();

        // Basic fields
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
        vo.setMemoryDistBinsMb(submission.memoryDistBinsMb());

        // Add user info (still fetched per-submission for user data)
        User user = userMapper.selectById(submission.userId());
        if (user != null) {
            SubmissionVO.UserInfo userInfo = new SubmissionVO.UserInfo();
            userInfo.setId(user.getId());
            userInfo.setUsername(user.getUsername());
            userInfo.setAvatar(user.getAvatar());
            vo.setUser(userInfo);
        }

        // Add problem info from pre-loaded DTO (eliminates N+1)
        if (submission.problemTitle() != null) {
            SubmissionVO.ProblemInfo problemInfo = new SubmissionVO.ProblemInfo();
            problemInfo.setId(submission.problemId());
            problemInfo.setTitle(submission.problemTitle());
            problemInfo.setSlug(submission.problemSlug());
            vo.setProblem(problemInfo);
        }

        return vo;
    }

    @Override
    public SubmissionVO toVO(Submission submission) {
        SubmissionVO vo = new SubmissionVO();

        // Basic fields
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
        vo.setMemoryDistBinsMb(submission.getMemoryDistBinsMb());

        // Convert test details to test results
        if (submission.getTestDetails() != null && !submission.getTestDetails().isEmpty()) {
            List<SubmissionVO.TestResult> tests = new ArrayList<>();
            for (int i = 0; i < submission.getTestDetails().size(); i++) {
                Submission.TestCaseDetail detail = submission.getTestDetails().get(i);
                SubmissionVO.TestResult test = new SubmissionVO.TestResult();
                test.setId("test-" + submission.getId() + "-" + (i + 1));
                test.setStatus(detail.getStatus() != null ? detail.getStatus() : submission.getStatus());
                test.setRuntime(detail.getTime() != null ? detail.getTime() : submission.getRuntime());
                test.setMemory(detail.getMemory() != null ? detail.getMemory() : submission.getMemory());
                tests.add(test);
            }
            vo.setTests(tests);

            // Extract error information from first failing test
            for (Submission.TestCaseDetail detail : submission.getTestDetails()) {
                if (detail.getStatus() != null && !"Accepted".equals(detail.getStatus())) {
                    if ("Compile Error".equals(detail.getStatus())) {
                        vo.setCompilerError(detail.getDetail());
                    }
                    vo.setErrorDetail(detail.getDetail());

                    // Format input
                    if (detail.getInputs() != null && !detail.getInputs().isEmpty()) {
                        StringBuilder inputBuilder = new StringBuilder();
                        for (Submission.TestCaseDetail.InputParam input : detail.getInputs()) {
                            if (inputBuilder.length() > 0) {
                                inputBuilder.append(", ");
                            }
                            inputBuilder.append(input.getName()).append(" = ").append(input.getValue());
                        }
                        vo.setInput(inputBuilder.toString());
                    }

                    vo.setOutput(detail.getOutput());
                    vo.setExpectedOutput(detail.getExpectedOutput());
                    break;
                }
            }
        }

        // Add user info
        User user = userMapper.selectById(submission.getUserId());
        if (user != null) {
            SubmissionVO.UserInfo userInfo = new SubmissionVO.UserInfo();
            userInfo.setId(user.getId());
            userInfo.setUsername(user.getUsername());
            userInfo.setAvatar(user.getAvatar());
            vo.setUser(userInfo);
        }

        // Add problem info
        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem != null) {
            SubmissionVO.ProblemInfo problemInfo = new SubmissionVO.ProblemInfo();
            problemInfo.setId(problem.getId());
            problemInfo.setTitle(problem.getTitle());
            problemInfo.setSlug(problem.getSlug());
            vo.setProblem(problemInfo);
        }

        return vo;
    }

    @Override
    public List<String> getSubmissionDates(String userId, Integer year) {
        return submissionMapper.findSubmissionDatesByYear(userId, year);
    }

    @Override
    public LearningProgressDTO getLearningProgress(String userId) {
        LearningProgressDTO progress = new LearningProgressDTO();

        // Get weekly progress
        List<WeeklyProgressDTO> weeklyData = submissionMapper.findWeeklyProgress(userId);
        List<LearningProgressDTO.WeeklyProgress> weeklyProgress = weeklyData.stream()
                .map(row -> new LearningProgressDTO.WeeklyProgress(
                        row.getWeekRange(),
                        row.getSolvedCount(),
                        row.getTimeSpentHours()))
                .toList();
        progress.setWeeklyProgress(weeklyProgress);

        // Get difficulty progress (reuse existing data from getUserStats pattern)
        // For now, return empty list - can be enhanced later
        progress.setDifficultyProgress(new ArrayList<>());

        // Calculate totals
        int totalProblems = weeklyProgress.stream()
                .mapToInt(LearningProgressDTO.WeeklyProgress::getSolved)
                .sum();
        double totalTimeHours = weeklyProgress.stream()
                .mapToDouble(LearningProgressDTO.WeeklyProgress::getTimeSpent)
                .sum();

        progress.setTotalProblems(totalProblems);
        progress.setTotalTimeHours(totalTimeHours);
        progress.setAvgTimePerProblem(totalProblems > 0 ? totalTimeHours / totalProblems : 0);

        // Get current streak
        Integer streak = submissionMapper.calculateStreak(userId);
        progress.setCurrentStreak(streak != null ? streak : 0);

        // Longest streak - for now same as current, can be enhanced with historical data
        progress.setLongestStreak(progress.getCurrentStreak());

        return progress;
    }

    @Override
    public SubmissionHistoryDTO getSubmissionHistory(String userId) {
        SubmissionHistoryDTO history = new SubmissionHistoryDTO();

        // Get monthly stats
        List<MonthlySubmissionStatsDTO> monthlyData = submissionMapper.findMonthlySubmissionStats(userId);
        List<SubmissionHistoryDTO.MonthlySubmission> monthly = monthlyData.stream()
                .map(row -> new SubmissionHistoryDTO.MonthlySubmission(
                        row.getMonth(),
                        row.getTotalCount(),
                        row.getAcceptedCount()))
                .toList();
        history.setMonthly(monthly);

        // Get language stats
        List<LanguageStatsDTO> languageData = submissionMapper.findLanguageStats(userId);
        List<SubmissionHistoryDTO.LanguageSubmission> languages = languageData.stream()
                .map(row -> new SubmissionHistoryDTO.LanguageSubmission(
                        row.getLanguage(),
                        row.getCount()))
                .toList();
        history.setLanguages(languages);

        // Calculate totals
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
    public List<SubmissionStatusMeta> getStatuses() {
        List<SubmissionStatusMeta> statuses = new ArrayList<>();

        // Pending
        SubmissionStatusMeta pending = new SubmissionStatusMeta();
        pending.setKey("Pending");
        pending.setCode("PENDING");
        pending.setLabel("Pending");
        pending.setDescription("Submission is waiting to be judged");
        pending.setSuggestion("Please wait for the judging to complete");
        pending.setCategory("pending");
        pending.setSeverity("info");
        pending.setIsTerminal(false);
        pending.setSortOrder(0);
        statuses.add(pending);

        // Judging
        SubmissionStatusMeta judging = new SubmissionStatusMeta();
        judging.setKey("Judging");
        judging.setCode("JUDGING");
        judging.setLabel("Judging");
        judging.setDescription("Submission is being judged");
        judging.setSuggestion("Please wait for the judging to complete");
        judging.setCategory("pending");
        judging.setSeverity("info");
        judging.setIsTerminal(false);
        judging.setSortOrder(1);
        statuses.add(judging);

        // Accepted
        SubmissionStatusMeta accepted = new SubmissionStatusMeta();
        accepted.setKey("Accepted");
        accepted.setCode("ACCEPTED");
        accepted.setLabel("Accepted");
        accepted.setDescription("All test cases passed");
        accepted.setSuggestion("Congratulations! Your solution is correct.");
        accepted.setCategory("success");
        accepted.setSeverity("success");
        accepted.setIsTerminal(true);
        accepted.setSortOrder(2);
        statuses.add(accepted);

        // Wrong Answer
        SubmissionStatusMeta wrongAnswer = new SubmissionStatusMeta();
        wrongAnswer.setKey("Wrong Answer");
        wrongAnswer.setCode("WRONG_ANSWER");
        wrongAnswer.setLabel("Wrong Answer");
        wrongAnswer.setDescription("Your output was incorrect");
        wrongAnswer.setSuggestion("Check your algorithm and edge cases");
        wrongAnswer.setCategory("error");
        wrongAnswer.setSeverity("error");
        wrongAnswer.setIsTerminal(true);
        wrongAnswer.setSortOrder(3);
        statuses.add(wrongAnswer);

        // Time Limit Exceeded
        SubmissionStatusMeta tle = new SubmissionStatusMeta();
        tle.setKey("Time Limit Exceeded");
        tle.setCode("TIME_LIMIT_EXCEEDED");
        tle.setLabel("Time Limit Exceeded");
        tle.setDescription("Your program took too long to execute");
        tle.setSuggestion("Optimize your algorithm or reduce unnecessary operations");
        tle.setCategory("error");
        tle.setSeverity("error");
        tle.setIsTerminal(true);
        tle.setSortOrder(4);
        statuses.add(tle);

        // Memory Limit Exceeded
        SubmissionStatusMeta mle = new SubmissionStatusMeta();
        mle.setKey("Memory Limit Exceeded");
        mle.setCode("MEMORY_LIMIT_EXCEEDED");
        mle.setLabel("Memory Limit Exceeded");
        mle.setDescription("Your program used too much memory");
        mle.setSuggestion("Optimize memory usage or use more efficient data structures");
        mle.setCategory("error");
        mle.setSeverity("error");
        mle.setIsTerminal(true);
        mle.setSortOrder(5);
        statuses.add(mle);

        // Output Limit Exceeded
        SubmissionStatusMeta ole = new SubmissionStatusMeta();
        ole.setKey("Output Limit Exceeded");
        ole.setCode("OUTPUT_LIMIT_EXCEEDED");
        ole.setLabel("Output Limit Exceeded");
        ole.setDescription("Your program produced too much output");
        ole.setSuggestion("Check for infinite loops that produce output");
        ole.setCategory("error");
        ole.setSeverity("error");
        ole.setIsTerminal(true);
        ole.setSortOrder(6);
        statuses.add(ole);

        // Runtime Error
        SubmissionStatusMeta runtimeError = new SubmissionStatusMeta();
        runtimeError.setKey("Runtime Error");
        runtimeError.setCode("RUNTIME_ERROR");
        runtimeError.setLabel("Runtime Error");
        runtimeError.setDescription("Your program crashed during execution");
        runtimeError.setSuggestion("Check for division by zero, null pointer, array out of bounds, etc.");
        runtimeError.setCategory("error");
        runtimeError.setSeverity("error");
        runtimeError.setIsTerminal(true);
        runtimeError.setSortOrder(7);
        statuses.add(runtimeError);

        // Compile Error
        SubmissionStatusMeta compileError = new SubmissionStatusMeta();
        compileError.setKey("Compile Error");
        compileError.setCode("COMPILE_ERROR");
        compileError.setLabel("Compile Error");
        compileError.setDescription("Your code failed to compile");
        compileError.setSuggestion("Check syntax errors and make sure your code is valid");
        compileError.setCategory("error");
        compileError.setSeverity("error");
        compileError.setIsTerminal(true);
        compileError.setSortOrder(8);
        statuses.add(compileError);

        // Presentation Error
        SubmissionStatusMeta presentationError = new SubmissionStatusMeta();
        presentationError.setKey("Presentation Error");
        presentationError.setCode("PRESENTATION_ERROR");
        presentationError.setLabel("Presentation Error");
        presentationError.setDescription("Your output format is incorrect");
        presentationError.setSuggestion("Check for extra spaces, newlines, or formatting issues");
        presentationError.setCategory("error");
        presentationError.setSeverity("warning");
        presentationError.setIsTerminal(true);
        presentationError.setSortOrder(9);
        statuses.add(presentationError);

        // System Error
        SubmissionStatusMeta systemError = new SubmissionStatusMeta();
        systemError.setKey("System Error");
        systemError.setCode("SYSTEM_ERROR");
        systemError.setLabel("System Error");
        systemError.setDescription("An error occurred on our end");
        systemError.setSuggestion("Please try again later or contact support");
        systemError.setCategory("system");
        systemError.setSeverity("error");
        systemError.setIsTerminal(true);
        systemError.setSortOrder(10);
        statuses.add(systemError);

        return statuses;
    }

    /**
     * Record contest submission if user is participating in an active contest containing this problem.
     * Per D-04: creates ContestSubmission alongside regular Submission in same transaction.
     * Per D-06: only records if user has STARTED status (matches DB enum).
     */
    private void recordContestSubmissionIfNeeded(String submissionId, String userId, Long problemId) {
        // 1. Find contest_problems containing this problem
        List<ContestProblem> contestProblems = contestProblemMapper.findByProblemId(problemId);

        for (ContestProblem cp : contestProblems) {
            // 2. Check if contest is RUNNING
            Contest contest = contestMapper.selectById(cp.getContestId());
            if (contest == null || !ContestStatus.RUNNING.name().equals(contest.getStatus())) {
                continue;
            }

            // 3. Check if user has STARTED status (D-06 -- matches DB enum 'STARTED')
            Optional<ContestParticipant> participant = contestParticipantMapper
                    .findByContestIdAndUserId(cp.getContestId(), userId);
            if (participant.isEmpty() ||
                    !ContestParticipantStatus.STARTED.name().equals(participant.get().getStatus())) {
                continue;
            }

            // 4. Create ContestSubmission (D-05)
            ContestSubmission cs = new ContestSubmission();
            cs.setSubmissionId(submissionId);
            cs.setContestId(cp.getContestId());
            cs.setContestProblemId(cp.getId());
            cs.setParticipantId(participant.get().getId());
            cs.setTimeFromStart((int) Duration.between(
                    contest.getStartTime(), LocalDateTime.now()).getSeconds());
            cs.setIsAccepted(false); // Will be updated when judge completes
            cs.setSubmittedAt(LocalDateTime.now());
            contestSubmissionMapper.insert(cs);
            realtimeService.markDirty(contest.getId());

            // Only record for the first matching active contest
            break;
        }
    }
}
