package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.port.ProblemExistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for the problem-existence seams. Owns the
 * read-check-write cycle for the {@code has_solution} column so the solution
 * write path stops mutating the {@link Problem} entity directly across the
 * module seam, and exposes the existence read both consumers need.
 *
 * <p>The adapter implements the app-api owner contract used by the solution
 * write path. The problem-list consumer is rebound to this same contract in
 * the consumer cutover child rather than importing this implementation.
 *
 * <p><b>Non-throwing contract</b>: missing problem rows are silent no-ops for
 * {@link #markHasSolution(Long, boolean)} (matches the previous inline
 * defensive null check in {@code SolutionServiceImpl.delete(...)}).
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class ProblemExistenceAdapter implements ProblemExistencePort {

    private final ProblemMapper problemMapper;

    @Override
    public boolean exists(Long problemId) {
        if (problemId == null) {
            return false;
        }
        return problemMapper.selectById(problemId) != null;
    }

    @Override
    public void markHasSolution(Long problemId, boolean hasSolution) {
        if (problemId == null) {
            return;
        }
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            return;
        }
        if (hasSolution == Boolean.TRUE.equals(problem.getHasSolution())) {
            return;
        }
        problem.setHasSolution(hasSolution);
        problemMapper.updateById(problem);
    }
}
