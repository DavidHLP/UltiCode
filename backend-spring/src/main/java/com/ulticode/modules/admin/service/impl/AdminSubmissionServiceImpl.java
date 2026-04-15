package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.*;
import com.ulticode.modules.admin.service.AdminSubmissionService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of AdminSubmissionService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSubmissionServiceImpl implements AdminSubmissionService {

    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;
    private final QueueService queueService;

    @Override
    public PageResult<AdminSubmissionVO> getSubmissions(AdminSubmissionQueryDTO query) {
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;

        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();

        // Search filter (username or problem title - need to join tables)
        // For now, we'll do a basic search and then enrich the results
        if (StringUtils.hasText(query.getSearch())) {
            // Search by submission ID or language (direct fields)
            // For username/problem title, we'll need to fetch and filter
            wrapper.and(w -> w
                    .like(Submission::getId, "%" + query.getSearch() + "%")
                    .or()
                    .eq(Submission::getLanguage, query.getSearch()));
        }

        // User ID filter
        if (StringUtils.hasText(query.getUserId())) {
            wrapper.eq(Submission::getUserId, query.getUserId());
        }

        // Problem ID filter
        if (query.getProblemId() != null) {
            wrapper.eq(Submission::getProblemId, query.getProblemId());
        }

        // Status filter
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Submission::getStatus, query.getStatus());
        }

        // Language filter
        if (StringUtils.hasText(query.getLanguage())) {
            wrapper.eq(Submission::getLanguage, query.getLanguage());
        }

        // Date range filter
        if (query.getStartDate() != null) {
            wrapper.ge(Submission::getCreatedAt, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(Submission::getCreatedAt, query.getEndDate());
        }

        // Sorting
        boolean isAsc = !"desc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        switch (sortBy) {
            case "createdAt" -> wrapper.orderBy(true, isAsc, Submission::getCreatedAt);
            case "runtime" -> wrapper.orderBy(true, isAsc, Submission::getRuntime);
            case "memory" -> wrapper.orderBy(true, isAsc, Submission::getMemory);
            case "status" -> wrapper.orderBy(true, isAsc, Submission::getStatus);
            default -> wrapper.orderBy(true, isAsc, Submission::getCreatedAt);
        }

        Page<Submission> pageResult = new Page<>(page, limit);
        Page<Submission> result = submissionMapper.selectPage(pageResult, wrapper);

        // Enrich with user and problem information
        List<AdminSubmissionVO> vos = result.getRecords().stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());

        // Apply search filter on username/problem title if needed
        // (This is a simplified approach - for production, consider using a proper query with joins)
        if (StringUtils.hasText(query.getSearch())) {
            String searchLower = query.getSearch().toLowerCase();
            vos = vos.stream()
                    .filter(vo -> {
                        // Check if username matches
                        if (vo.getUsername() != null && vo.getUsername().toLowerCase().contains(searchLower)) {
                            return true;
                        }
                        // Check if problem title matches
                        if (vo.getProblemTitle() != null && vo.getProblemTitle().toLowerCase().contains(searchLower)) {
                            return true;
                        }
                        // Check if ID matches (already filtered by SQL)
                        if (vo.getId() != null && vo.getId().toLowerCase().contains(searchLower)) {
                            return true;
                        }
                        // Check if language matches exactly (already filtered by SQL)
                        if (vo.getLanguage() != null && vo.getLanguage().equalsIgnoreCase(query.getSearch())) {
                            return true;
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        }

        return PageResult.of(
                vos,
                result.getTotal(),
                page,
                limit
        );
    }

    @Override
    public AdminSubmissionVO getSubmission(String id) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }
        return toAdminVOWithDetails(submission);
    }

    @Override
    public SubmissionStatistics getStatistics() {
        SubmissionStatistics stats = new SubmissionStatistics();

        // Total submissions
        Long total = submissionMapper.selectCount(null);
        stats.setTotal(total);

        // By status
        List<SubmissionStatistics.StatusCount> byStatus = new ArrayList<>();
        Map<String, Long> statusCounts = getAllSubmissions().stream()
                .collect(Collectors.groupingBy(Submission::getStatus, Collectors.counting()));
        statusCounts.forEach((status, count) -> {
            SubmissionStatistics.StatusCount sc = new SubmissionStatistics.StatusCount();
            sc.setStatus(status);
            sc.setCount(count);
            byStatus.add(sc);
        });
        stats.setByStatus(byStatus);

        // By language
        List<SubmissionStatistics.LanguageCount> byLanguage = new ArrayList<>();
        Map<String, Long> languageCounts = getAllSubmissions().stream()
                .collect(Collectors.groupingBy(Submission::getLanguage, Collectors.counting()));
        languageCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(entry -> {
                    SubmissionStatistics.LanguageCount lc = new SubmissionStatistics.LanguageCount();
                    lc.setLanguage(entry.getKey());
                    lc.setCount(entry.getValue());
                    byLanguage.add(lc);
                });
        stats.setByLanguage(byLanguage);

        // Last 24 hours
        LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
        Long last24h = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().ge(Submission::getCreatedAt, yesterday)
        );
        stats.setLast24h(last24h);

        // Pending count
        Long pending = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().eq(Submission::getStatus, "Pending")
        );
        stats.setPending(pending);

        return stats;
    }

    @Override
    public List<StatusOption> getStatuses() {
        List<StatusOption> options = new ArrayList<>();

        // Pending
        StatusOption pending = new StatusOption();
        pending.setKey("Pending");
        pending.setLabel("Pending");
        pending.setCategory("pending");
        options.add(pending);

        // Accepted
        StatusOption accepted = new StatusOption();
        accepted.setKey("Accepted");
        accepted.setLabel("Accepted");
        accepted.setCategory("accepted");
        options.add(accepted);

        // Wrong Answer
        StatusOption wrongAnswer = new StatusOption();
        wrongAnswer.setKey("Wrong Answer");
        wrongAnswer.setLabel("Wrong Answer");
        wrongAnswer.setCategory("error");
        options.add(wrongAnswer);

        // Time Limit Exceeded
        StatusOption tle = new StatusOption();
        tle.setKey("Time Limit Exceeded");
        tle.setLabel("Time Limit Exceeded");
        tle.setCategory("error");
        options.add(tle);

        // Memory Limit Exceeded
        StatusOption mle = new StatusOption();
        mle.setKey("Memory Limit Exceeded");
        mle.setLabel("Memory Limit Exceeded");
        mle.setCategory("error");
        options.add(mle);

        // Runtime Error
        StatusOption runtimeError = new StatusOption();
        runtimeError.setKey("Runtime Error");
        runtimeError.setLabel("Runtime Error");
        runtimeError.setCategory("error");
        options.add(runtimeError);

        // Compilation Error
        StatusOption compilationError = new StatusOption();
        compilationError.setKey("Compilation Error");
        compilationError.setLabel("Compilation Error");
        compilationError.setCategory("error");
        options.add(compilationError);

        return options;
    }

    @Override
    public List<String> getLanguages() {
        return getAllSubmissions().stream()
                .map(Submission::getLanguage)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public RejudgeResult rejudge(String id, boolean notifyUser) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            RejudgeResult result = new RejudgeResult();
            result.setSubmissionId(id);
            result.setSuccess(false);
            result.setError("Submission not found");
            return result;
        }

        RejudgeResult result = new RejudgeResult();
        result.setSubmissionId(id);
        result.setOldStatus(submission.getStatus());

        try {
            // D-04: Reuse existing QueueService.enqueueJudgeJob()
            // D-14: LOW priority as marker (D-19: FIFO queue ignores priority -- worker enhancement deferred)
            queueService.enqueueJudgeJob(
                submission.getId(),
                String.valueOf(submission.getProblemId()),
                submission.getUserId(),
                submission.getLanguage(),
                submission.getCode()
            );

            // Reset submission status to Pending for re-evaluation
            submission.setStatus("Pending");

            // D-23: Increment retry count to track rejudge attempts
            submission.setRetryCount(
                submission.getRetryCount() != null ? submission.getRetryCount() + 1 : 1
            );
            submissionMapper.updateById(submission);

            result.setSuccess(true);
            result.setNewStatus("Pending");
            log.info("Rejudge initiated for submission: {} (retryCount={})",
                id, submission.getRetryCount());
        } catch (Exception e) {
            log.error("Failed to enqueue rejudge for submission: {}", id, e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        return result;
    }

    @Override
    public BatchRejudgeResponse batchRejudge(List<String> ids, boolean notifyUsers) {
        // D-05: Batch size limit of 50
        if (ids.size() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "Batch size exceeds maximum of 50");
        }

        BatchRejudgeResponse response = new BatchRejudgeResponse();
        response.setTotal(ids.size());
        response.setResults(new ArrayList<>());
        int successful = 0;
        int failed = 0;

        for (String id : ids) {
            RejudgeResult result = rejudge(id, notifyUsers);
            response.getResults().add(result);
            if (result.getSuccess()) {
                successful++;
            } else {
                failed++;
            }
        }

        response.setSuccessful(successful);
        response.setFailed(failed);
        return response;
    }

    /**
     * Convert Submission entity to AdminSubmissionVO (list view).
     */
    private AdminSubmissionVO toAdminVO(Submission submission) {
        if (submission == null) {
            return null;
        }

        AdminSubmissionVO vo = new AdminSubmissionVO();
        vo.setId(submission.getId());
        vo.setProblemId(submission.getProblemId());
        vo.setUserId(submission.getUserId());
        vo.setLanguage(submission.getLanguage());
        vo.setStatus(submission.getStatus());
        vo.setRuntime(submission.getRuntime());
        vo.setMemory(submission.getMemory());
        vo.setCreatedAt(submission.getCreatedAt());

        // Calculate code length
        vo.setCodeLength(submission.getCode() != null ? submission.getCode().length() : 0);

        // Fetch user info
        User user = userMapper.selectById(submission.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
        }

        // Fetch problem info
        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem != null) {
            vo.setProblemTitle(problem.getTitle());
            vo.setProblemSlug(problem.getSlug());
        }

        return vo;
    }

    /**
     * Convert Submission entity to AdminSubmissionVO with full details.
     */
    private AdminSubmissionVO toAdminVOWithDetails(Submission submission) {
        AdminSubmissionVO vo = toAdminVO(submission);
        if (vo != null) {
            vo.setCode(submission.getCode());
            vo.setNotes(submission.getNotes());
            vo.setRuntimePercentile(submission.getRuntimePercentile());
            vo.setMemoryPercentile(submission.getMemoryPercentile());
            vo.setTestDetails(submission.getTestDetails());
            vo.setMemoryDistBinsMb(submission.getMemoryDistBinsMb());
            vo.setRuntimeDistBinsMs(submission.getRuntimeDistBinsMs());
        }
        return vo;
    }

    /**
     * Get all submissions (for statistics).
     * In production, this should use optimized queries instead of loading all records.
     */
    private List<Submission> getAllSubmissions() {
        // Use a reasonable limit for statistics
        Page<Submission> page = new Page<>(1, 10000);
        return submissionMapper.selectPage(page, null).getRecords();
    }
}
