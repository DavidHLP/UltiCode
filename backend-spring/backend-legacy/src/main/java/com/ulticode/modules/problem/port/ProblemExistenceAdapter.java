package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.app.api.service.ProblemExistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for the problem-existence seams. Owns the
 * read-check-write cycle for the {@code has_solution} column so the solution
 * write path stops mutating the {@link Problem} entity directly across the
 * module seam, and exposes the existence read both consumers need.
 *
 * <p>Implements two consumer-owned ports (each consumer narrows the seam to
 * its own need, per ADR-0001):
 * <ul>
 *   <li>{@link ProblemExistencePort} (solution) &mdash; {@code exists} +
 *       {@code markHasSolution}.</li>
 *   <li>{@code com.ulticode.modules.problemlist.port.ProblemExistencePort}
 *       (problem-list) &mdash; {@code exists} only, used by
 *       {@code ProblemListServiceImpl.addProblem} to validate a problem id
 *       without importing {@link ProblemMapper}/{@link Problem}.</li>
 * </ul>
 *
 * <p>Both ports share the same {@code exists(Long)} signature, so a single
 * method body satisfies each. The {@code problemlist} port is referenced by
 * fully-qualified name in the {@code implements} clause to avoid a simple-name
 * clash with the solution port.
 *
 * <p><b>Non-throwing contract</b>: missing problem rows are silent no-ops for
 * {@link #markHasSolution(Long, boolean)} (matches the previous inline
 * defensive null check in {@code SolutionServiceImpl.delete(...)}).
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class ProblemExistenceAdapter
        implements ProblemExistencePort,
        com.ulticode.modules.problemlist.port.ProblemExistencePort {

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
