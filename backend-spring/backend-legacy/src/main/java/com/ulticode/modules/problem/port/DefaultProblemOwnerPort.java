package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.mapper.ProblemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * P3-OWNER-001-A: default {@link ProblemOwnerPort} implementation.
 * Lives in the problem module (the OWNER), uses the problem
 * module's own {@link ProblemMapper} for the actual writes. The
 * legacy admin code never imports this class directly; it
 * injects the port interface so the seam is a real abstraction.
 *
 * <p>{@code @Transactional} on each write method so the port owns
 * its transaction boundary. A future Dubbo provider (P4-RPC-001)
 * is a one-class swap: the default adapter is replaced by a
 * {@code @DubboService} consumer wrapper, the wire shape is
 * unchanged.
 *
 * <p>The {@code moderateProblem} and {@code flagProblem} methods
 * mirror the underlying mapper signatures; the bulk variants
 * wrap the mapper in a single transaction so a mid-list failure
 * rolls back the whole batch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProblemOwnerPort implements ProblemOwnerPort {

    private final ProblemMapper problemMapper;

    @Override
    @Transactional
    public void flagProblem(Long id, String reason, String reportedBy) {
        final int affected = problemMapper.flagProblem(id, reason, reportedBy);
        log.info("ProblemOwnerPort.flagProblem id={} reason={} reporter={} affected={}",
                id, reason, reportedBy, affected);
    }

    @Override
    @Transactional
    public void moderateProblem(Long id, String status, String notes, String reviewedBy) {
        final int affected = problemMapper.moderateProblem(id, status, notes, reviewedBy);
        log.info("ProblemOwnerPort.moderateProblem id={} status={} reviewer={} affected={}",
                id, status, reviewedBy, affected);
    }

    @Override
    @Transactional
    public int restoreDeletedByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        final int restored = problemMapper.restoreDeletedByIds(ids);
        log.info("ProblemOwnerPort.restoreDeletedByIds count={} restored={}", ids.size(), restored);
        return restored;
    }

    @Override
    @Transactional
    public int moderateProblems(List<Long> ids, String status, String notes, String reviewedBy) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // The mapper method is named batchModerateProblems but the
        // port's verb is moderateProblems to match the singular /
        // plural pair (moderateProblem / moderateProblems).
        final int affected = problemMapper.batchModerateProblems(ids, status, notes, reviewedBy);
        log.info("ProblemOwnerPort.moderateProblems count={} status={} reviewer={} affected={}",
                ids.size(), status, reviewedBy, affected);
        return affected;
    }

    @Override
    @Transactional
    public void updateDifficulty(Long id, String difficulty) {
        if (id == null || difficulty == null || difficulty.isBlank()) {
            return;
        }
        // P3-OWNER-001-A: the port owns the difficulty write; the
        // existence + read-after-write + business validation lives
        // in the admin caller (it has the user-facing error path).
        // The mapper's BaseMapper.updateById takes an entity; the
        // port is a Command surface, so we denormalize to a
        // lightweight Problem shell here. The read-back below
        // matches the original AdminProblemServiceImpl.edit branch.
        final int affected = problemMapper.updateDifficulty(id, difficulty);
        log.info("ProblemOwnerPort.updateDifficulty id={} difficulty={} affected={}",
                id, difficulty, affected);
    }
}
