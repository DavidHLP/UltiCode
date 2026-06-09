package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.*;
import com.ulticode.modules.admin.service.AdminSubmissionService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        // Search filter — resolve at DB level by pre-fetching matching user/problem IDs
        if (StringUtils.hasText(query.getSearch())) {
            String search = query.getSearch();

            // Find user IDs matching the search term
            List<String> matchingUserIds = userMapper.selectList(
                    new LambdaQueryWrapper<User>().like(User::getUsername, search)
            ).stream().map(User::getId).collect(Collectors.toList());

            // Find problem IDs matching the search term
            List<Long> matchingProblemIds = problemMapper.selectList(
                    new LambdaQueryWrapper<Problem>().like(Problem::getTitle, search)
            ).stream().map(Problem::getId).collect(Collectors.toList());

            wrapper.and(w -> {
                w.like(Submission::getId, search)
                        .or().eq(Submission::getLanguage, search);
                if (!matchingUserIds.isEmpty()) {
                    w.or().in(Submission::getUserId, matchingUserIds);
                }
                if (!matchingProblemIds.isEmpty()) {
                    w.or().in(Submission::getProblemId, matchingProblemIds);
                }
            });
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

        // Batch-load users and problems to avoid N+1 queries (WR-05)
        Map<String, User> userMap = new HashMap<>();
        Map<Long, Problem> problemMap = new HashMap<>();
        if (!result.getRecords().isEmpty()) {
            Set<String> userIds = result.getRecords().stream()
                    .map(Submission::getUserId)
                    .collect(Collectors.toSet());
            Set<Long> problemIds = result.getRecords().stream()
                    .map(Submission::getProblemId)
                    .collect(Collectors.toSet());

            if (!userIds.isEmpty()) {
                userMap = userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
            }
            if (!problemIds.isEmpty()) {
                problemMap = problemMapper.selectBatchIds(problemIds).stream()
                        .collect(Collectors.toMap(Problem::getId, p -> p));
            }
        }

        // Enrich with user and problem information using batch-loaded maps
        Map<String, User> finalUserMap = userMap;
        Map<Long, Problem> finalProblemMap = problemMap;
        List<AdminSubmissionVO> vos = result.getRecords().stream()
                .map(s -> toAdminVO(s, finalUserMap, finalProblemMap))
                .collect(Collectors.toList());

        // All filtering now at DB level — use database total for correct pagination
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

        // By status — aggregate SQL query instead of loading all records
        List<SubmissionStatistics.StatusCount> byStatus = new ArrayList<>();
        List<Map<String, Object>> statusRows = submissionMapper.countByStatus();
        for (Map<String, Object> row : statusRows) {
            SubmissionStatistics.StatusCount sc = new SubmissionStatistics.StatusCount();
            sc.setStatus((String) row.get("status"));
            sc.setCount(((Number) row.get("count")).longValue());
            byStatus.add(sc);
        }
        stats.setByStatus(byStatus);

        // By language — aggregate SQL query instead of loading all records
        List<SubmissionStatistics.LanguageCount> byLanguage = new ArrayList<>();
        List<Map<String, Object>> languageRows = submissionMapper.countByLanguage();
        for (Map<String, Object> row : languageRows) {
            SubmissionStatistics.LanguageCount lc = new SubmissionStatistics.LanguageCount();
            lc.setLanguage((String) row.get("language"));
            lc.setCount(((Number) row.get("count")).longValue());
            byLanguage.add(lc);
        }
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
        // Derive filter options from the canonical enum so the dropdown
        // stays in sync with both the DB (displayName) and statistics
        // (category). Returns all 11 statuses including transient ones
        // (Judging) so admins can see and filter on every observed state.
        return Arrays.stream(SubmissionStatus.values())
            .map(s -> {
                StatusOption opt = new StatusOption();
                opt.setKey(s.getDisplayName());
                opt.setLabel(s.getDisplayName());
                opt.setCode(s.name());
                opt.setCategory(s.getCategory());
                return opt;
            })
            .collect(Collectors.toList());
    }

    @Override
    public List<LanguageOption> getLanguages() {
        return submissionMapper.findDistinctLanguages().stream()
            .map(code -> {
                LanguageOption opt = new LanguageOption();
                opt.setKey(code);
                opt.setLabel(humanizeLanguage(code));
                return opt;
            })
            .collect(Collectors.toList());
    }

    /**
     * Convert a language code stored in the database to a human-readable
     * display label. Falls back to title-cased code for unknown languages.
     *
     * @param code DB-stored language code (e.g. {@code "cpp"})
     * @return display label (e.g. {@code "C++"})
     */
    private String humanizeLanguage(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case "cpp" -> "C++";
            case "c" -> "C";
            case "csharp" -> "C#";
            case "java" -> "Java";
            case "python" -> "Python";
            case "javascript" -> "JavaScript";
            case "typescript" -> "TypeScript";
            case "go" -> "Go";
            case "rust" -> "Rust";
            case "ruby" -> "Ruby";
            case "kotlin" -> "Kotlin";
            case "swift" -> "Swift";
            default -> code.substring(0, 1).toUpperCase() + code.substring(1);
        };
    }

    @Override
    @Audited(action = AuditActionUtil.REQUEUE_SUBMISSION, entityType = AuditActionUtil.ENTITY_SUBMISSION, userIdFrom = "id")
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
            // Reset submission status to Pending for re-evaluation
            submission.setStatus("Pending");

            // D-23: Increment retry count to track rejudge attempts
            submission.setRetryCount(
                submission.getRetryCount() != null ? submission.getRetryCount() + 1 : 1
            );
            submissionMapper.updateById(submission);

            // D-04: Enqueue after DB update to avoid orphaned jobs on DB failure
            queueService.enqueueJudgeJob(
                submission.getId(),
                String.valueOf(submission.getProblemId()),
                submission.getUserId(),
                submission.getLanguage(),
                submission.getCode()
            );

            result.setSuccess(true);
            result.setNewStatus("Pending");
            // Surface rejudge metadata to the caller so the admin UI can
            // detect that a rejudge actually happened even when old and
            // new status are identical (e.g. Pending -> Pending).
            result.setRejudgedAt(Instant.now());
            result.setRetryCount(submission.getRetryCount());
            log.info("Rejudge initiated for submission: {} (retryCount={})",
                id, submission.getRetryCount());
        // broad catch: all failures map to same error response
        } catch (Exception e) {
            log.error("Failed to enqueue rejudge for submission: {}", id, e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        if (result.getSuccess()) {
            AuditContext.setOldValues(java.util.Map.of(
                "oldStatus", result.getOldStatus() != null ? result.getOldStatus() : "",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
            AuditContext.setNewValues(java.util.Map.of(
                "newStatus", "Pending",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
        }

        return result;
    }

    @Override
    public BatchRejudgeResponse batchRejudge(List<String> submissionIds, boolean notifyUsers) {
        // Non-null, non-empty, and size<=50 are enforced by Bean Validation
        // on the controller (see BatchRejudgeRequest @NotEmpty/@Size and
        // @Valid on the @RequestBody), so we can drop the silent null/empty
        // branch that previously masked client bugs.
        BatchRejudgeResponse response = new BatchRejudgeResponse();
        response.setTotal(submissionIds.size());
        response.setResults(new ArrayList<>(submissionIds.size()));
        int successful = 0;
        int failed = 0;

        for (String id : submissionIds) {
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
     * Convert Submission entity to AdminSubmissionVO using pre-loaded maps (batch).
     */
    private AdminSubmissionVO toAdminVO(Submission submission, Map<String, User> userMap, Map<Long, Problem> problemMap) {
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
        vo.setCodeLength(submission.getCode() != null ? submission.getCode().length() : 0);

        User user = userMap.get(submission.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
        }

        Problem problem = problemMap.get(submission.getProblemId());
        if (problem != null) {
            vo.setProblemTitle(problem.getTitle());
            vo.setProblemSlug(problem.getSlug());
        }

        return vo;
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

}
