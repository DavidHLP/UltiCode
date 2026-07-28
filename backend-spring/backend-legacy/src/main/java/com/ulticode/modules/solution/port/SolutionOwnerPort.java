package com.ulticode.modules.solution.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * P3-OWNER-001-E: owner-only write surface for the {@code solutions} table
 * in the solution module.
 *
 * <p>Before this port, {@code AdminSolutionServiceImpl} reached directly into
 * {@link com.ulticode.modules.solution.mapper.SolutionMapper} and
 * {@link com.ulticode.modules.problem.mapper.ProblemMapper}.
 * The Admin module's P3-OWNER-001-E boundary forbids foreign-mapper WRITE
 * methods (ArchUnit rule P3-OWNER-001-F), so every admin caller of these writes
 * must go through this port.
 *
 * @author ulticode
 */
public interface SolutionOwnerPort {

    /**
     * Flag a solution for moderation.
     *
     * @param id solution ID
     * @param reason flag reason
     * @param flaggedAt wall-clock timestamp of flagging
     * @return flag result container with audit details (authorUserId, oldIsFlagged, oldFlaggedReason)
     */
    FlagResult flagSolution(String id, String reason, LocalDateTime flaggedAt);

    /**
     * Remove moderation flag from a solution.
     *
     * @param id solution ID
     * @return flag result container with audit details
     */
    FlagResult unflagSolution(String id);

    /**
     * Delete a solution and update the problem's {@code has_solution} status if no solutions remain.
     *
     * @param id solution ID
     * @return delete result container with audit details (authorUserId, title, problemId)
     */
    DeleteResult deleteSolution(String id);

    /**
     * Set solution publication status.
     *
     * @param id solution ID
     * @param published target published state
     * @param publishedAt timestamp when published is true (or null if unpublishing)
     */
    void setPublished(String id, boolean published, LocalDateTime publishedAt);

    /**
     * Batch check existence of solution IDs.
     *
     * @param ids candidate solution IDs
     * @return set of existing solution IDs
     */
    Set<String> findExistingIds(List<String> ids);

    /**
     * Result wrapper for flag/unflag operations carrying audit snapshot state.
     */
    record FlagResult(String authorUserId, boolean oldIsFlagged, String oldFlaggedReason) {}

    /**
     * Result wrapper for delete operations carrying audit snapshot state.
     */
    record DeleteResult(String authorUserId, String title, Long problemId) {}
}
