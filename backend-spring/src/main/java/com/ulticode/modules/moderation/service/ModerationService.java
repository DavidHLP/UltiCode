package com.ulticode.modules.moderation.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.moderation.dto.*;

import java.util.List;

/**
 * Service interface for moderation operations.
 */
public interface ModerationService {

    // ==================== Queue Operations ====================

    /**
     * Get paginated queue items.
     *
     * @param query the query parameters
     * @return paginated queue items
     */
    PageResult<ModerationQueueVO> getQueueItems(QueryModerationQueueDTO query);

    /**
     * Get a specific queue item by ID.
     *
     * @param id the queue item ID
     * @return the queue item
     */
    ModerationQueueVO getQueueItem(String id);

    /**
     * Get moderation statistics.
     *
     * @return the statistics
     */
    ModerationStatsVO getStats();

    /**
     * Find queue item by entity.
     *
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @return the queue item or null
     */
    ModerationQueueVO findByEntity(String entityType, String entityId);

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

    /**
     * Get reports for a specific entity.
     *
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @return list of reports
     */
    List<ReportVO> getReportsForEntity(String entityType, String entityId);

    /**
     * Get paginated reports.
     *
     * @param query the query parameters
     * @return paginated reports
     */
    PageResult<ReportVO> getReports(QueryReportsDTO query);

    /**
     * Get a specific report by ID.
     *
     * @param id the report ID
     * @return the report
     */
    ReportVO getReport(String id);

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
     * Get paginated appeals.
     *
     * @param query the query parameters
     * @return paginated appeals
     */
    PageResult<AppealVO> getAppeals(QueryAppealsDTO query);

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
     * Get appeals for the current user.
     *
     * @param appellantId the appellant ID
     * @return list of user's appeals
     */
    List<AppealVO> getMyAppeals(String appellantId);

    /**
     * Get appeal statistics.
     *
     * @return the appeal statistics
     */
    AppealStatsVO getAppealStats();

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
