package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.bulk.AdminBulkExecutor;
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
    private final AdminBulkExecutor bulkExecutor;

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.FLAG_SOLUTION, entityType = AuditVocabulary.ENTITY_SOLUTION)
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
    @Audited(action = AuditVocabulary.UNFLAG_SOLUTION, entityType = AuditVocabulary.ENTITY_SOLUTION)
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
    @Audited(action = AuditVocabulary.DELETE_SOLUTION, entityType = AuditVocabulary.ENTITY_SOLUTION)
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
    @Audited(action = AuditVocabulary.BULK_SOLUTION_ACTION, entityType = AuditVocabulary.ENTITY_SOLUTION, captureNewState = false)
    public List<BulkActionResult> bulkAction(List<String> ids, String action) {
        AuditContext.setEntityId(String.join(",", ids));

        // Pre-check existence in a single batched query (BUG-Q4 perf fix):
        // passed to the executor as an existence guard so non-existent ids
        // short-circuit to a not-found outcome without running the action.
        Set<String> existingIds = solutionMapper.selectBatchIds(ids).stream()
                .map(Solution::getId)
                .collect(Collectors.toSet());

        AdminBulkExecutor.Run run = bulkExecutor.run(ids, action, id -> {
            switch (action) {
                case "publish" -> {
                    LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
                    wrapper.eq(Solution::getId, id)
                            .set(Solution::getIsPublished, true)
                            .set(Solution::getPublishedAt, LocalDateTime.now(clock));
                    solutionMapper.update(null, wrapper);
                }
                case "unpublish" -> {
                    LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
                    wrapper.eq(Solution::getId, id)
                            .set(Solution::getIsPublished, false)
                            .set(Solution::getPublishedAt, (LocalDateTime) null);
                    solutionMapper.update(null, wrapper);
                }
                case "delete" -> deleteSolution(id);
                case "unflag" -> unflagSolution(id);
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            }
        }, existingIds::contains);

        List<BulkActionResult> results = new ArrayList<>(run.items().size());
        for (AdminBulkExecutor.ItemOutcome outcome : run.items()) {
            if (outcome instanceof AdminBulkExecutor.NotFound) {
                results.add(BulkActionResult.failure(outcome.id(), BulkActionResult.NOT_FOUND_MESSAGE));
            } else if (outcome instanceof AdminBulkExecutor.Success) {
                results.add(BulkActionResult.success(outcome.id()));
            } else {
                results.add(BulkActionResult.failure(outcome.id(), outcome.errorOrNull()));
            }
        }
        return results;
    }
}
