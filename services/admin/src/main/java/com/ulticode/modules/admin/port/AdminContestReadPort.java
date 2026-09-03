package com.ulticode.modules.admin.port;

/**
 * Read port the admin module uses to inspect contest aggregates without
 * reaching across into the contest module's mappers and entities.
 *
 * Phase 2 of the AdminReadModel seam (ADR-0011), continuing the series
 * opened by the submission and comment read seams. CONTEXT.md foreshadowed
 * the contest phase; this port starts it.
 *
 * <p>The admin contest service legitimately owns contest writes
 * (create / update / delete / start / finish) &mdash; those are admin's
 * CRUD targets. What is <em>not</em> admin's business is querying raw
 * contest-side mappers to answer read questions such as "how many
 * problems does this contest have?". That cross-module read is narrowed
 * to this port; the production adapter
 * ({@link com.ulticode.modules.admin.port.adapter.AdminContestReadAdapter})
 * hides {@code ContestProblemMapper} and the entity layer.
 *
 * <p>The deletion test passes: deleting this port would force
 * {@code AdminContestServiceImpl} back into importing
 * {@code ContestProblemMapper} just to read a count.
 *
 * @author ulticode
 */
public interface AdminContestReadPort {

    /**
     * Number of problems linked to a contest.
     *
     * <p>Used by the start-contest guard (a contest cannot start with zero
     * problems) and by the add-problem flow (to compute the next problem
     * index).
     *
     * @param contestId the contest id (must be non-null)
     * @return the linked-problem count; {@code 0} when none or unknown
     */
    long countProblemsByContestId(String contestId);
}
