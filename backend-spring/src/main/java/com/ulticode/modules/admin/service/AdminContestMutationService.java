package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.ContestAnnouncement;

/**
 * Deep write module for the admin contest surface &mdash; the single owner
 * of the Contest mutation policy.
 *
 * <p>Concentrates every contest write the admin module owns behind one
 * coherent seam:
 * <ul>
 *   <li><b>Contest lifecycle transitions</b> &mdash; create, update
 *       (UPCOMING-only), soft-delete (UPCOMING/FINISHED-only),
 *       UPCOMING-&gt;RUNNING (start, requires &ge;1 problem),
 *       RUNNING-&gt;FINISHED (end).</li>
 *   <li><b>Write invariants</b> &mdash; existence checks, status guards,
 *       slug-conflict mapping, problem-existence guards, duplicate-problem
 *       guards, index computation.</li>
 *   <li><b>Audit context</b> &mdash; every mutation publishes
 *       {@link com.ulticode.common.util.AuditContext} old/new values for the
 *       {@link com.ulticode.common.annotation.Audited @Audited} aspect.</li>
 *   <li><b>Announcement CRUD</b> &mdash; create / update / delete contest
 *       announcements, with the WebSocket push delegated to
 *       {@link com.ulticode.modules.admin.port.ContestAnnouncementPushPort}.</li>
 *   <li><b>Scored problem attachment</b> &mdash; create and update accept a
 *       scored {@code problems} list so the contest row and every
 *       {@code ContestProblem} persist in one transaction; the cross-module
 *       problem-count read used by the start guard stays narrowed to
 *       {@link com.ulticode.modules.admin.port.AdminContestReadPort}.</li>
 * </ul>
 *
 * <p>This is the admin-write companion to the ADR-0011
 * {@link com.ulticode.modules.admin.projection.AdminContestProjection}
 * read module. Together they replace the single fat
 * {@code AdminContestServiceImpl}: the projection owns every entity-to-VO
 * shape and read; this module owns every mutation. The two never cross
 * except through the {@link AdminContestProjection#toAdminVO} /
 * {@link AdminContestProjection#generateSlug} shape helpers, so the
 * controller contract on write return values is unchanged.
 *
 * <p>The read-side methods that previously lived on
 * {@link AdminContestService} (list / detail / announcements-list /
 * rankings) stay there as a thin projection facade &mdash; this module
 * does not touch reads.
 *
 * <p>Behavior is preserved exactly from the legacy single-service shape:
 * same status guards, same error codes, same audit-context payloads, same
 * slug-conflict catch, same fire-and-forget announcement push.
 *
 * @author ulticode
 * @see AdminContestService the read facade
 * @see com.ulticode.modules.admin.projection.AdminContestProjection the read module
 */
public interface AdminContestMutationService {

    /**
     * Create a new contest with optional problem assignment.
     *
     * <p>Generates a URL-friendly slug via the projection, inserts the
     * contest, bulk-inserts any provided problem links, and publishes the
     * create audit context. Maps a slug-unique-constraint violation to
     * {@link com.ulticode.common.exception.ErrorCode#CONTEST_SLUG_EXISTS}.
     *
     * @param dto    the contest creation data
     * @param userId the creating admin's user id
     * @return the created contest as an admin VO (shape owned by the projection)
     */
    AdminContestVO createContest(CreateContestDTO dto, String userId);

    /**
     * Update an existing contest (UPCOMING status only). Replaces the
     * contest-problem set when {@code problemIds} is provided and
     * recomputes the coupled {@code endTime} when {@code duration} changes.
     *
     * @param id  the contest id
     * @param dto the partial update data
     * @return the updated contest as an admin VO
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#CONTEST_NOT_FOUND}
     *         when the contest does not exist, or
     *         {@link com.ulticode.common.exception.ErrorCode#CONTEST_ONLY_REGISTER_UPCOMING}
     *         when the contest is not UPCOMING
     */
    AdminContestVO updateContest(String id, UpdateContestDTO dto);

    /**
     * Soft-delete a contest (UPCOMING or FINISHED only). Stamps the
     * deleted-at timestamp and the deleting admin's id.
     *
     * @param id the contest id
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#CONTEST_NOT_FOUND}
     *         when the contest does not exist or is in a non-deletable status
     */
    void deleteContest(String id);

    /**
     * Start a contest (UPCOMING -&gt; RUNNING). Requires at least one linked
     * problem; the count is read through
     * {@link com.ulticode.modules.admin.port.AdminContestReadPort}.
     *
     * @param id the contest id
     * @return the started contest as an admin VO
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#CONTEST_NOT_FOUND}
     *         when the contest does not exist or has no problems, or
     *         {@link com.ulticode.common.exception.ErrorCode#CONTEST_NOT_STARTED}
     *         when the contest is not UPCOMING
     */
    AdminContestVO startContest(String id);

    /**
     * End a contest (RUNNING -&gt; FINISHED).
     *
     * @param id the contest id
     * @return the ended contest as an admin VO
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#CONTEST_NOT_FOUND}
     *         when the contest does not exist, or
     *         {@link com.ulticode.common.exception.ErrorCode#CONTEST_ENDED}
     *         when the contest is not RUNNING
     */
    AdminContestVO endContest(String id);

    /**
     * Create a contest announcement, persist it, and broadcast it to the
     * contest's WebSocket subscribers via
     * {@link com.ulticode.modules.admin.port.ContestAnnouncementPushPort}.
     * The push is best-effort fire-and-forget (D-12); the persisted row is
     * the durable record.
     *
     * @param contestId the contest id
     * @param title     the announcement title
     * @param content   the announcement content
     * @param isPinned  whether to pin the announcement ({@code null} treated as {@code false})
     * @return the created announcement
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#CONTEST_NOT_FOUND}
     *         when the contest does not exist
     */
    ContestAnnouncement createAnnouncement(String contestId, String title, String content, Boolean isPinned);

    /**
     * Update an existing contest announcement (partial: title, content, isPinned).
     *
     * @param contestId      the contest id
     * @param announcementId the announcement id
     * @param title          the new title (optional; {@code null} leaves it unchanged)
     * @param content        the new content (optional; {@code null} leaves it unchanged)
     * @param isPinned       the new pinned flag (optional; {@code null} leaves it unchanged)
     * @return the updated announcement
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#BAD_REQUEST}
     *         when the announcement does not exist under the contest
     */
    ContestAnnouncement updateAnnouncement(String contestId, String announcementId, String title, String content, Boolean isPinned);

    /**
     * Delete a contest announcement.
     *
     * @param contestId      the contest id
     * @param announcementId the announcement id
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#BAD_REQUEST}
     *         when the announcement does not exist under the contest
     */
    void deleteAnnouncement(String contestId, String announcementId);
}
