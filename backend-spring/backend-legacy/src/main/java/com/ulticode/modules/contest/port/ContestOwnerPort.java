package com.ulticode.modules.contest.port;

import com.ulticode.modules.contest.dto.AddContestProblemDTO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;

import java.util.List;

/**
 * P3-OWNER-001-B: owner-only write surface for the {@code contests},
 * {@code contest_problems}, and {@code contest_announcements}
 * tables. The default implementation lives in the contest module
 * (the OWNER); admin code never imports the underlying mappers
 * or the {@code Contest} / {@code ContestProblem} /
 * {@code ContestAnnouncement} entities for the write path.
 *
 * <p>Commands reuse the existing
 * {@code com.ulticode.modules.contest.dto.*} DTOs (the contest
 * module already owns the DTOs; no new Command record needed
 * for the minimum viable seam). The port returns primitive ids
 * so the boundary does not leak entities; admin callers re-fetch
 * via the existing {@code AdminContestProjection} for VO
 * composition.
 *
 * <p>Side effects (WebSocket push for announcements) stay in the
 * admin caller; the port owns the database writes only.
 */
public interface ContestOwnerPort {

    // ─── Contest row writes ──────────────────────────────────────

    /**
     * Create a contest. Returns the new contest id. The contest
     * and any provided contest-problem attachments are persisted
     * in the same transaction so a mid-list failure rolls back
     * the whole contest (no partial persistence).
     *
     * @param command the create-contest command
     * @param userId the creator (auth subject id)
     * @return the new contest id
     */
    String createContest(CreateContestDTO command, String userId);

    /**
     * Partial-update a contest. Optional fields on {@code command}
     * are applied with set-if-present semantics; if either
     * {@code command.problems} or {@code command.problemIds} is
     * present, the contest's problem set is replaced in the same
     * transaction.
     */
    void updateContest(String id, UpdateContestDTO command);

    /**
     * Soft-delete a contest. The contest must be in a
     * state that allows deletion (UPCOMING or FINISHED).
     *
     * @param id the contest id
     * @param deletedBy the actor performing the delete (auth subject id)
     */
    void deleteContest(String id, String deletedBy);

    /**
     * Transition a contest to RUNNING. The contest must be
     * in UPCOMING and have at least one attached problem.
     */
    void startContest(String id);

    /**
     * Transition a contest to FINISHED. The contest must be in
     * RUNNING.
     */
    void endContest(String id);

    // ─── Announcement writes ────────────────────────────────────

    /**
     * Create a contest announcement. Returns the new
     * announcement id. The admin caller is responsible for any
     * downstream push (WebSocket, etc.).
     */
    String createAnnouncement(String contestId, String title, String content, Boolean isPinned);

    /**
     * Partial-update a contest announcement. Null fields on the
     * command are not applied.
     */
    void updateAnnouncement(String contestId, String announcementId,
                            String title, String content, Boolean isPinned);

    /**
     * Delete a contest announcement.
     */
    void deleteAnnouncement(String contestId, String announcementId);
}
