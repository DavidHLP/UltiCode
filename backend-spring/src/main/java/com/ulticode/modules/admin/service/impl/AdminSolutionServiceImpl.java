package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminSolutionVO;
import com.ulticode.modules.admin.projection.AdminSolutionProjection;
import com.ulticode.modules.admin.service.AdminSolutionService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Write-side implementation of {@link AdminSolutionService}.
 *
 * <p><strong>Writes only</strong> after the ADR-0011 Stage 2 extraction. The
 * read cluster (paginated list with active / soft-deleted branches, flagged
 * derivation, single-detail enrichment) moved to
 * {@link AdminSolutionProjection} / {@code DefaultAdminSolutionProjection}.
 * Cross-module entity imports for read enrichment ({@code User},
 * {@code UserMapper}) left this service; {@link Problem} and
 * {@link ProblemMapper} stay because {@link #deleteSolution} owns the
 * denormalised {@code Problem.hasSolution} write side-effect.
 *
 * <p>Write methods that return {@link AdminSolutionVO} (flag / unflag) compose
 * it by delegating to {@link AdminSolutionProjection#getSolution(String)}
 * for the post-write VO shape &mdash; same pattern
 * {@code UserPermissionServiceImpl} uses against {@code AdminUserProjection}.
 *
 * <p>All four write methods are {@code @Audited}; the catalog at
 * {@code common/audit/AuditPolicy} lists them under this class's FQN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSolutionServiceImpl implements AdminSolutionService {

    private final SolutionMapper solutionMapper;
    private final ProblemMapper problemMapper;
    private final AdminSolutionProjection solutionProjection;
    private final Clock clock;

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.FLAG_SOLUTION, entityType = AuditActionUtil.ENTITY_SOLUTION)
    public AdminSolutionVO flagSolution(String id, String reason) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }

        // Audit identity fix: the @Audited aspect no longer reads a method param named "id"
        // (which would have been the solution id, not the author). Instead, the affected
        // user is the solution author; the performer is resolved from SecurityContext.
        AuditContext.setUserId(solution.getUserId());
        AuditContext.setEntityId(id);

        AuditContext.setOldValues(Map.of(
            "isFlagged", solution.getIsFlagged() != null ? solution.getIsFlagged() : false,
            "flaggedReason", solution.getFlaggedReason() != null ? solution.getFlaggedReason() : ""
        ));

        LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Solution::getId, id)
                .set(Solution::getIsFlagged, true)
                .set(Solution::getFlaggedReason, reason)
                .set(Solution::getFlaggedAt, LocalDateTime.now(clock));

        solutionMapper.update(null, wrapper);

        AuditContext.setNewValues(Map.of("isFlagged", true, "flaggedReason", reason != null ? reason : ""));

        log.info("Solution flagged: {} reason: {}", id, reason);

        return solutionProjection.getSolution(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.UNFLAG_SOLUTION, entityType = AuditActionUtil.ENTITY_SOLUTION)
    public AdminSolutionVO unflagSolution(String id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }

        AuditContext.setUserId(solution.getUserId());
        AuditContext.setEntityId(id);

        AuditContext.setOldValues(Map.of(
            "isFlagged", solution.getIsFlagged() != null ? solution.getIsFlagged() : false,
            "flaggedReason", solution.getFlaggedReason() != null ? solution.getFlaggedReason() : ""
        ));

        LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Solution::getId, id)
                .set(Solution::getIsFlagged, false)
                .set(Solution::getFlaggedReason, null)
                .set(Solution::getFlaggedAt, null);

        solutionMapper.update(null, wrapper);

        AuditContext.setNewValues(Map.of("isFlagged", false, "flaggedReason", ""));

        log.info("Solution unflagged: {}", id);

        return solutionProjection.getSolution(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.DELETE_SOLUTION, entityType = AuditActionUtil.ENTITY_SOLUTION)
    public void deleteSolution(String id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }

        AuditContext.setUserId(solution.getUserId());
        AuditContext.setEntityId(id);

        AuditContext.setOldValues(Map.of(
            "title", solution.getTitle() != null ? solution.getTitle() : "",
            "problemId", solution.getProblemId()
        ));

        solutionMapper.deleteById(id);

        LambdaQueryWrapper<Solution> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Solution::getProblemId, solution.getProblemId());
        long remainingCount = solutionMapper.selectCount(countWrapper);

        if (remainingCount == 0) {
            Problem problem = problemMapper.selectById(solution.getProblemId());
            if (problem != null && Boolean.TRUE.equals(problem.getHasSolution())) {
                problem.setHasSolution(false);
                problemMapper.updateById(problem);
            }
        }

        log.info("Solution deleted: {}", id);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.BULK_SOLUTION_ACTION, entityType = AuditActionUtil.ENTITY_SOLUTION, captureNewState = false)
    public List<BulkActionResult> bulkAction(List<String> ids, String action) {
        AuditContext.setEntityId(String.join(",", ids));
        List<BulkActionResult> results = new ArrayList<>();

        // Pre-check existence in a single batched query (BUG-Q4, perf fix per code review
        // M-1): replaces the previous N+1 selectById loop. MyBatis-Plus update returns 0
        // affected rows silently for non-existent ids, so we must verify presence first.
        Set<String> existingIds = solutionMapper.selectBatchIds(ids).stream()
                .map(Solution::getId)
                .collect(Collectors.toSet());

        for (String id : ids) {
            if (!existingIds.contains(id)) {
                results.add(BulkActionResult.failure(id, BulkActionResult.NOT_FOUND_MESSAGE));
                continue;
            }
            try {
                switch (action) {
                    case "publish" -> {
                        LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
                        wrapper.eq(Solution::getId, id)
                                .set(Solution::getIsPublished, true)
                                .set(Solution::getPublishedAt, LocalDateTime.now(clock));
                        solutionMapper.update(null, wrapper);
                        results.add(BulkActionResult.success(id));
                    }
                    case "unpublish" -> {
                        LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
                        wrapper.eq(Solution::getId, id)
                                .set(Solution::getIsPublished, false)
                                .set(Solution::getPublishedAt, (LocalDateTime) null);
                        solutionMapper.update(null, wrapper);
                        results.add(BulkActionResult.success(id));
                    }
                    case "delete" -> {
                        deleteSolution(id);
                        results.add(BulkActionResult.success(id));
                    }
                    case "unflag" -> {
                        unflagSolution(id);
                        results.add(BulkActionResult.success(id));
                    }
                    default -> {
                        results.add(BulkActionResult.failure(id, "Unknown action: " + action));
                    }
                }
            } catch (RuntimeException e) {
                log.error("Failed to perform action {} on solution {}: {}", action, id, e.getMessage());
                results.add(BulkActionResult.failure(id, e.getMessage()));
            }
        }

        return results;
    }
}
