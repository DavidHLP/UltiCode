package com.ulticode.modules.problemlist.port;

/**
 * Cross-module existence seam the problem-list write path uses against the
 * problem module. Closes the leak where {@code ProblemListServiceImpl.addProblem}
 * previously injected {@code com.ulticode.modules.problem.mapper.ProblemMapper}
 * and read {@code com.ulticode.modules.problem.entity.Problem} directly to
 * validate a problem id before inserting a list relation (2026-07-13
 * architecture review, "Concentrate Problem List mutations" candidate).
 *
 * <p><b>Consumer-owned</b> (ADR-0001): the problem-list module defines the
 * narrow read it needs; the problem module supplies the adapter. This keeps
 * the problem-list compile-time dependency graph inside its own module
 * boundary and lets the problem module add cache, event, or audit behaviour
 * at the seam without a downstream recompile.
 *
 * <p>The solution module owns its own broader
 * {@code com.ulticode.modules.solution.port.ProblemExistencePort} (which also
 * flips {@code has_solution}); that port and this one are intentionally
 * separate &mdash; each consumer narrows the seam to its own need.
 *
 * @author ulticode
 */
public interface ProblemExistencePort {

    /**
     * @param problemId the problem id to check; null-safe
     * @return {@code true} if a problem row exists for the id
     */
    boolean exists(Long problemId);
}
