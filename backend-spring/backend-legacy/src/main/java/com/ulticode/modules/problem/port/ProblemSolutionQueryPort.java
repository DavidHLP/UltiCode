package com.ulticode.modules.problem.port;

/**
 * Outbound port for querying solution counts associated with a problem.
 *
 * <p>Extracted from {@code DefaultProblemProjection}'s direct dependency on
 * {@code SolutionMapper} to decouple the problem module from the solution
 * module. When the problem domain migrates to {@code backend-app}, this port
 * moves with it and the adapter stays in the original module or is replaced
 * by an RPC-backed implementation.
 *
 * @author ulticode
 */
public interface ProblemSolutionQueryPort {

    /**
     * Count published solutions for the given problem.
     *
     * @param problemId the problem ID
     * @return solution count, or 0 if none
     */
    long countSolutionsByProblemId(Long problemId);
}
