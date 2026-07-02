package com.ulticode.modules.moderation.service;

import com.ulticode.modules.moderation.dto.AppealVO;
import com.ulticode.modules.moderation.dto.BatchActionResultVO;
import com.ulticode.modules.moderation.dto.BatchModerationActionDTO;
import com.ulticode.modules.moderation.dto.CreateAppealDTO;
import com.ulticode.modules.moderation.dto.CreateReportDTO;
import com.ulticode.modules.moderation.dto.ModerationQueueVO;
import com.ulticode.modules.moderation.dto.PerformModerationActionDTO;
import com.ulticode.modules.moderation.dto.ReviewAppealDTO;

/**
 * Service interface for moderation <em>state-change</em> operations.
 *
 * <p>The read paths (queue list / detail / stats, report list / detail, appeal
 * list / detail / stats) live on {@link com.ulticode.modules.moderation.projection.ModerationProjection};
 * controllers depend on that seam directly for reads and on this service for
 * writes and for the authorisation-guarded appeal lookup. This interface
 * dropped ten pure-read methods when the projection module was extracted —
 * they were earning no locality next to the write paths and forced every
 * projection test to mock twelve collaborators.
 */
public interface ModerationService {

    // ==================== Queue Operations ====================

    /**
     * Claim a queue item for the current moderator.
     *
     * @param id           the queue item ID
     * @param moderatorId  the moderator ID
     * @return the updated queue item
     */
    ModerationQueueVO claimItem(String id, String moderatorId);

    /**
     * Assign a queue item to a specific moderator.
     *
     * @param id           the queue item ID
     * @param moderatorId  the assigning moderator ID
     * @param assignedTo   the target moderator ID
     * @return the updated queue item
     */
    ModerationQueueVO assignItem(String id, String moderatorId, String assignedTo);

    /**
     * Remove assignment from a queue item.
     *
     * @param id          the queue item ID
     * @param moderatorId the moderator ID
     * @return the updated queue item
     */
    ModerationQueueVO unassignItem(String id, String moderatorId);

    /**
     * Perform a moderation action on a queue item.
     *
     * @param id          the queue item ID
     * @param dto         the action details
     * @param moderatorId the moderator ID
     * @return the updated queue item
     */
    ModerationQueueVO performAction(String id, PerformModerationActionDTO dto, String moderatorId);

    /**
     * Perform a batch action on multiple queue items.
     *
     * @param dto         the batch action details
     * @param moderatorId the moderator ID
     * @return the batch result
     */
    BatchActionResultVO batchAction(BatchModerationActionDTO dto, String moderatorId);

    // ==================== Report Operations ====================

    /**
     * Create a new report.
     *
     * @param dto        the report details
     * @param reporterId the reporter ID
     */
    void createReport(CreateReportDTO dto, String reporterId);

    // ==================== Appeal Operations ====================

    /**
     * Create a new appeal.
     *
     * @param dto         the appeal details
     * @param appellantId the appellant ID
     * @return the created appeal
     */
    AppealVO createAppeal(CreateAppealDTO dto, String appellantId);

    /**
     * Get a specific appeal by ID.
     *
     * <p>Authorization: only the appellant themselves, or a MOD/ADMIN/SUPER_ADMIN, may read.
     * Other authenticated users receive 403 Forbidden.
     *
     * @param id            the appeal ID
     * @param currentUserId the requesting user's ID (from SecurityContext)
     * @return the appeal
     * @throws com.ulticode.common.exception.BusinessException 404 if not found, 403 if not authorized
     */
    AppealVO getAppeal(String id, String currentUserId);

    /**
     * Review an appeal.
     *
     * @param id           the appeal ID
     * @param dto          the review decision
     * @param moderatorId  the moderator ID
     * @return the updated appeal
     */
    AppealVO reviewAppeal(String id, ReviewAppealDTO dto, String moderatorId);
}
