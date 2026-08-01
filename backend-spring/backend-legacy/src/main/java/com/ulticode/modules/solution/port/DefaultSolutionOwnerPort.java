package com.ulticode.modules.solution.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link SolutionOwnerPort}.
 *
 * <p>Uses {@link ProblemExistencePort} for the denormalised {@code problem.has_solution}
 * write side-effect when solutions are deleted, keeping the solution module free
 * of direct {@code ProblemMapper} or {@code Problem} entity imports.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSolutionOwnerPort implements SolutionOwnerPort {

    private final SolutionMapper solutionMapper;
    private final ProblemExistencePort problemExistencePort;

    @Override
    @Transactional
    public FlagResult flagSolution(String id, String reason, LocalDateTime flaggedAt) {
        Solution solution = loadOrThrow(id);

        boolean oldIsFlagged = Boolean.TRUE.equals(solution.getIsFlagged());
        String oldFlaggedReason = solution.getFlaggedReason() != null ? solution.getFlaggedReason() : "";

        solution.setIsFlagged(true);
        solution.setFlaggedReason(reason);
        solution.setFlaggedAt(flaggedAt);

        solutionMapper.updateById(solution);
        log.info("Solution flagged: {} reason: {}", id, reason);

        return new FlagResult(solution.getUserId(), oldIsFlagged, oldFlaggedReason);
    }

    @Override
    @Transactional
    public FlagResult unflagSolution(String id) {
        Solution solution = loadOrThrow(id);

        boolean oldIsFlagged = Boolean.TRUE.equals(solution.getIsFlagged());
        String oldFlaggedReason = solution.getFlaggedReason() != null ? solution.getFlaggedReason() : "";

        solution.setIsFlagged(false);
        solution.setFlaggedReason(null);
        solution.setFlaggedAt(null);

        solutionMapper.updateById(solution);
        log.info("Solution unflagged: {}", id);

        return new FlagResult(solution.getUserId(), oldIsFlagged, oldFlaggedReason);
    }

    @Override
    @Transactional
    public DeleteResult deleteSolution(String id) {
        Solution solution = loadOrThrow(id);

        solutionMapper.deleteById(id);

        LambdaQueryWrapper<Solution> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Solution::getProblemId, solution.getProblemId());
        long remainingCount = solutionMapper.selectCount(countWrapper);

        if (remainingCount == 0) {
            problemExistencePort.markHasSolution(solution.getProblemId(), false);
        }

        log.info("Solution deleted: {}", id);
        return new DeleteResult(solution.getUserId(), solution.getTitle() != null ? solution.getTitle() : "", solution.getProblemId());
    }

    @Override
    @Transactional
    public void setPublished(String id, boolean published, LocalDateTime publishedAt) {
        Solution solution = loadOrThrow(id);

        solution.setIsPublished(published);
        solution.setPublishedAt(published ? publishedAt : null);

        solutionMapper.updateById(solution);
        log.info("Solution setPublished id={} published={}", id, published);
    }

    @Override
    public Set<String> findExistingIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return solutionMapper.selectBatchIds(ids).stream()
                .map(Solution::getId)
                .collect(Collectors.toSet());
    }

    private Solution loadOrThrow(String id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }
        return solution;
    }

    @Override
    public String resolveAuthorId(String id) {
        Solution solution = solutionMapper.selectById(id);
        return solution != null ? solution.getUserId() : null;
    }
}
