package com.ulticode.modules.moderation.port;

import com.ulticode.modules.moderation.dto.AppealVO;
import com.ulticode.modules.moderation.dto.BatchActionResultVO;
import com.ulticode.modules.moderation.dto.BatchModerationActionDTO;
import com.ulticode.modules.moderation.dto.CreateAppealDTO;
import com.ulticode.modules.moderation.dto.CreateReportDTO;
import com.ulticode.modules.moderation.dto.ModerationQueueVO;
import com.ulticode.modules.moderation.dto.PerformModerationActionDTO;
import com.ulticode.modules.moderation.dto.ReviewAppealDTO;

/**
 * Write surface for the moderation domain — the <b>moderation state machine</b>.
 *
 * <p>Extracted from the {@code ModerationServiceImpl} facade. Owns every state
 * mutation on the moderation domain behind one interface:
 * <ul>
 *   <li>the <em>queue</em> write paths — claim / assign / unassign,
 *       {@code performAction} (action-record write + queue resolution +
 *       report-status fan-out via the {@link
 *       com.ulticode.modules.moderation.service.impl.ModerationActionHandler}
 *       strategy), and {@code batchAction} (loop + per-item error capture);</li>
 *   <li>the <em>report intake</em> — {@code createReport} (report insert +
 *       queue upsert + author resolution);</li>
 *   <li>the <em>appeal lifecycle</em> — {@code createAppeal} (appellant +
 *       appealable-state guard + queue → {@code APPEAL_PENDING}) and
 *       {@code reviewAppeal} (decision + action record + queue resolution);</li>
 *   <li>the three <em>action-sink</em> callbacks ({@link #createUserWarning},
 *       {@link #createUserBan}, {@link #updateContentFlagStatus}) invoked by the
 *       {@code ModerationActionHandler} strategies through
 *       {@code ActionContext}. They are first-class methods on this port — not
 *       a separate sink interface — because writing a {@code UserWarning},
 *       {@code UserBan}, or content flag row <em>is</em> a moderation write, so
 *       it belongs on the write surface rather than on an internal helper.</li>
 * </ul>
 *
 * <p>The read paths (queue / report / appeal list, detail, stats) live on
 * {@link com.ulticode.modules.moderation.projection.ModerationProjection}; the
 * authorisation-guarded appeal lookup ({@code getAppeal}) stays on
 * {@code ModerationService} because it is a read with a guard, not a state
 * change.
 *
 * <p>Why a deep module and not "the service as before":
 * <ul>
 *   <li><b>Locality</b>: the moderation state machine — the
 *       {@code PENDING → UNDER_REVIEW → RESOLVED / DISMISSED /
 *       APPEAL_PENDING} queue transitions, the appeal decision flow, the
 *       action-record-on-every-mutation invariant, and the strategy-dispatch
 *       side effects (warning / ban / content flag) — now sits in one module
 *       instead of leaking across a 424-line facade that also carried
 *       projection delegation and twelve injected collaborators.</li>
 *   <li><b>Leverage</b>: {@code ModerationController} hits this port for every
 *       write (seven endpoints); {@code batchAction} reuses {@code performAction}
 *       internally; the {@code ActionContext} sink contract lets the handler
 *       strategies stay pure (no service back-reference).</li>
 *   <li><b>Interface is the test surface</b>: a state-machine unit test mocks
 *       the mappers + projection only — no controller assembly, no read-path
 *       collaborators — because the read concern lives on
 *       {@code ModerationProjection} and is not on this seam.</li>
 * </ul>
 *
 * <p>Dependency category: <b>in-process</b>. The default adapter is the only
 * provider today; the {@code ModerationService} facade stays as a thin delegate
 * so the controller and any cross-module caller see zero behavioural change.
 * Tests can substitute a fake.
 *
 * @author ulticode
 */
public interface ModerationWritePort {

    // ------------------------------------------------------------------
    // Queue write paths
    // ------------------------------------------------------------------

    /**
     * Atomically claim a queue item for the current moderator. Uses a
     * conditional update so two concurrent claims cannot both land; on a
     * zero-affected result the method re-reads the row to distinguish
     * "not found" ({@code MODERATION_QUEUE_NOT_FOUND}) from
     * "already assigned to another moderator" ({@code MODERATION_ALREADY_ASSIGNED}),
     * and treats "already assigned to this moderator" as success.
     *
     * @param id          the queue item id
     * @param moderatorId the claiming moderator id
     * @return the post-claim queue view (projected)
     */
    ModerationQueueVO claimItem(String id, String moderatorId);

    /**
     * Assign a queue item to a specific moderator (admin override). Verifies
     * the queue item and the target moderator both exist.
     *
     * @param id          the queue item id
     * @param moderatorId the assigning moderator id
     * @param assignedTo  the target moderator id
     * @return the post-assign queue view (projected)
     */
    ModerationQueueVO assignItem(String id, String moderatorId, String assignedTo);

    /**
     * Remove the assignment from a queue item.
     *
     * @param id          the queue item id
     * @param moderatorId the moderator id
     * @return the post-unassign queue view (projected)
     */
    ModerationQueueVO unassignItem(String id, String moderatorId);

    /**
     * Perform a moderation action on a queue item. Writes the
     * {@code ModerationAction} audit row, dispatches the action through the
     * {@link com.ulticode.modules.moderation.service.impl.ModerationActionHandler}
     * strategy (which may write a warning / ban / content flag via the
     * action-sink methods below), resolves the queue item, and fans the
     * related reports out to {@code RESOLVED} (or {@code DISMISSED}).
     *
     * @param id          the queue item id
     * @param dto         the action details
     * @param moderatorId the moderator id
     * @return the post-action queue view (projected)
     */
    ModerationQueueVO performAction(String id, PerformModerationActionDTO dto, String moderatorId);

    /**
     * Perform the same action across many queue items, capturing per-item
     * failures. Always returns a {@link BatchActionResultVO} so the caller
     * receives per-item error detail even when every item fails; the caller
     * inspects {@code successCount} / {@code errors} to decide UX.
     *
     * @param dto         the batch action details
     * @param moderatorId the moderator id
     * @return the batch result (never {@code null})
     */
    BatchActionResultVO batchAction(BatchModerationActionDTO dto, String moderatorId);

    // ------------------------------------------------------------------
    // Report intake
    // ------------------------------------------------------------------

    /**
     * File a new report. Inserts the report (rejecting duplicates with
     * {@code MODERATION_ALREADY_REPORTED}), resolves the content author,
     * and upserts the moderation queue item (creating it with the report's
     * category or bumping its report count), then links the report to the
     * queue.
     *
     * @param dto        the report details
     * @param reporterId the reporter id
     */
    void createReport(CreateReportDTO dto, String reporterId);

    // ------------------------------------------------------------------
    // Appeal lifecycle
    // ------------------------------------------------------------------

    /**
     * Create an appeal for a resolved queue item. Guards that the appellant is
     * the content author ({@code MODERATION_NOT_AUTHOR}) and that the queue
     * item is in an appealable state ({@code RESOLVED}, else
     * {@code MODERATION_CANNOT_APPEAL}); inserts the appeal and flips the
     * queue item to {@code APPEAL_PENDING}.
     *
     * @param dto         the appeal details
     * @param appellantId the appellant id
     * @return the created appeal view (projected)
     */
    AppealVO createAppeal(CreateAppealDTO dto, String appellantId);

    /**
     * Review a pending appeal. Writes the decision, the appeal-review action
     * record, and resolves the queue item ({@code APPEAL_APPROVED} /
     * {@code APPEAL_REJECTED} resolution). Rejects re-review of an already
     * decided appeal with {@code MODERATION_APPEAL_ALREADY_REVIEWED}.
     *
     * @param id          the appeal id
     * @param dto         the review decision
     * @param moderatorId the moderator id
     * @return the updated appeal view (projected)
     */
    AppealVO reviewAppeal(String id, ReviewAppealDTO dto, String moderatorId);

    // ------------------------------------------------------------------
    // Action-sink callbacks (invoked by ModerationActionHandler strategies)
    // ------------------------------------------------------------------

    /**
     * Persist a {@code UserWarning} for the moderated content's author. The
     * {@code ModerationActionHandler} strategies invoke this through
     * {@code ActionContext} when an action resolves to a warning.
     *
     * @param userId   the warned user id
     * @param queueId  the originating queue item id
     * @param reason   free-text reason ({@code null} → default placeholder)
     * @param category moderation category ({@code null} → {@code OTHER})
     * @param actionId the {@code ModerationAction} id that triggered the warning
     */
    void createUserWarning(String userId, String queueId, String reason, String category, String actionId);

    /**
     * Persist a {@code UserBan} (temporary or permanent) and flip the user's
     * ban status. The {@code ModerationActionHandler} strategies invoke this
     * through {@code ActionContext} when an action resolves to a ban.
     *
     * @param userId        the banned user id
     * @param queueId       the originating queue item id
     * @param reason        free-text reason ({@code null} → default placeholder)
     * @param category      moderation category
     * @param bannedById    the moderator who imposed the ban
     * @param actionId      the {@code ModerationAction} id that triggered the ban
     * @param durationDays  ban duration in days ({@code null} when permanent)
     * @param isPermanent   whether the ban is permanent
     */
    void createUserBan(String userId, String queueId, String reason, String category,
                       String bannedById, String actionId, Integer durationDays, boolean isPermanent);

    /**
     * Flip the flag status on a piece of moderated content (forum post /
     * forum comment / solution / solution comment / problem). The
     * {@code ModerationActionHandler} strategies invoke this through
     * {@code ActionContext} for hide / restore actions.
     *
     * @param entityType the content entity type
     * @param entityId   the content entity id
     * @param isFlagged  the new flag status
     * @param reason     the flag reason (may be {@code null} on restore)
     */
    void updateContentFlagStatus(String entityType, String entityId, boolean isFlagged, String reason);
}
