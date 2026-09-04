package com.ulticode.modules.problem.port;

/**
 * Read-and-write seam the solution write path uses against the
 * problem module. Closes the cross-module leak where
 * {@code SolutionServiceImpl} previously injected
 * {@code com.ulticode.modules.problem.mapper.ProblemMapper} directly
 * and mutated {@code problem.has_solution} from the solution write
 * path (architecture review 2026-07-09 candidate 3).
 *
 * <p>The port owns two operations the solution write path needs:
 * <ul>
 *   <li>{@link #exists(Long)} &mdash; existence check used by
 *       {@code create(...)} to validate the problem id before
 *       inserting a solution.</li>
 *   <li>{@link #markHasSolution(Long, boolean)} &mdash; flip the
 *       {@code has_solution} column. The adapter is responsible for
 *       the read-check-write cycle so callers stop reaching into
 *       {@code Problem} entity mutation.</li>
 * </ul>
 *
 * <p><b>Why provider-local</b>: this consumer-owned seam is located in the
 * Problem module because both the Solution and ProblemList modules use the
 * same Problem-owned {@code has_solution} side effect. The Adapter remains
 * the only component that touches the Problem Mapper.
 *
 * @author ulticode
 */
public interface ProblemExistencePort {

    /**
     * @param problemId the problem id to check; null-safe
     * @return {@code true} if a non-deleted problem row exists for the id
     */
    boolean exists(Long problemId);

    /**
     * Flip the {@code has_solution} column for the given problem.
     * No-op when the problem row is missing, or when the column
     * already holds the requested value (avoids a pointless write
     * and matches the previous defensive
     * {@code if (!Boolean.TRUE.equals(problem.getHasSolution()))}
     * guard the solution write path inlined).
     *
     * @param problemId    the problem id; null-safe
     * @param hasSolution  the new value
     */
    void markHasSolution(Long problemId, boolean hasSolution);
}
