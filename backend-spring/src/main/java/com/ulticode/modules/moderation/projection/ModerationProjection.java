package com.ulticode.modules.moderation.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.moderation.dto.AppealStatsVO;
import com.ulticode.modules.moderation.dto.AppealVO;
import com.ulticode.modules.moderation.dto.ModerationQueueVO;
import com.ulticode.modules.moderation.dto.ModerationStatsVO;
import com.ulticode.modules.moderation.dto.QueryAppealsDTO;
import com.ulticode.modules.moderation.dto.QueryModerationQueueDTO;
import com.ulticode.modules.moderation.dto.QueryReportsDTO;
import com.ulticode.modules.moderation.dto.ReportVO;
import com.ulticode.modules.moderation.entity.Appeal;

import java.util.List;

/**
 * Deep module that owns all entity-to-VO projection and read-side aggregation
 * for the moderation domain (queue items, reports, appeals).
 *
 * <p>Replaces the projection methods previously embedded in
 * {@code ModerationServiceImpl}. Callers that only need read views
 * ({@code ModerationController} list / detail / stats paths for queue, reports
 * and appeals) cross this seam and stay free of state-change concerns.
 * Callers that mutate state still hold a {@code ModerationService} reference;
 * the service delegates the view shapes it still returns (the post-action
 * queue reload, the post-create / post-review appeal view) here so the
 * projection rules live in one place.
 *
 * <p>Why a separate module and not "a helper class" or "moved methods":
 * <ul>
 *   <li><b>Locality</b>: the three entity-to-VO projections
 *       ({@code toQueueVO} with its batched user map and the
 *       {@code solution_comment} parent-id resolution, {@code toReportVO},
 *       {@code toAppealVO}), the three list query builders
 *       ({@code buildQueueWrapper} / {@code buildReportWrapper} /
 *       {@code buildAppealWrapper} with their shared sort-default policy), and
 *       the two statistics aggregations ({@code stats} with its eight counts
 *       and the {@code toCountMap} group-by adapter, {@code appealStats}) all
 *       carry non-trivial read policy. Sitting next to the moderation
 *       state-machine ({@code performAction} / {@code reviewAppeal} /
 *       {@code createReport}) made every projection tweak land in the same
 *       file as the write paths. They are now concentrated here.</li>
 *   <li><b>Leverage</b>: the queue endpoints ({@code listQueueItems} /
 *       {@code queueItemById} / {@code queueItemByEntity}) share the same
 *       batched user fetch ({@code buildUserMap}) and the same
 *       {@code toQueueVO} mapping. Sharing inside one module beats sharing
 *       across N call sites or via a service facade.</li>
 *   <li><b>Interface is the test surface</b>: the read paths are tested here
 *       with mocks for the mappers. The state-change paths in
 *       {@code ModerationServiceImpl} no longer have to mock twelve
 *       collaborators just to exercise a projection.</li>
 * </ul>
 *
 * <p>Dependency category: <b>in-process</b> (no I/O that cannot be exercised
 * with mocks). No adapter is needed at the external seam; the default adapter
 * is the only implementation.
 *
 * @author ulticode
 */
public interface ModerationProjection {

    // ------------------------------------------------------------------
    // Queue projection
    // ------------------------------------------------------------------

    /**
     * List moderation queue items with pagination, filters and sorting.
     * Builds the {@code status} / {@code entityType} / {@code assignedTo} /
     * {@code primaryCategory} / {@code minPriority} filter chain and the
     * {@code priority} / {@code createdAt} / {@code updatedAt} sort selection,
     * defaulting to priority-desc, created-asc. Batch-loads the referenced
     * users (author / assignee / reviewer) once per page to avoid N+1.
     *
     * @param query the query parameters
     * @return paginated result of queue VOs; never {@code null}
     */
    PageResult<ModerationQueueVO> listQueueItems(QueryModerationQueueDTO query);

    /**
     * Get a single queue item by id, projected with its referenced users.
     *
     * @param id the queue item id
     * @return the queue VO; never {@code null}
     * @throws com.ulticode.common.exception.BusinessException
     *         ({@link com.ulticode.common.exception.ErrorCode#MODERATION_QUEUE_NOT_FOUND})
     *         if no queue item exists with the given id
     */
    ModerationQueueVO queueItemById(String id);

    /**
     * Find the queue item for a given moderated entity, projected with its
     * referenced users.
     *
     * @param entityType the entity type
     * @param entityId   the entity id
     * @return the queue VO, or {@code null} if no queue item exists for the entity
     */
    ModerationQueueVO queueItemByEntity(String entityType, String entityId);

    /**
     * Aggregate moderation statistics: pending / under-review / resolved /
     * dismissed / resolved-today counts, average resolution time, pending
     * appeals, and group-by-category / group-by-entity-type distributions.
     *
     * @return the statistics VO; never {@code null}
     */
    ModerationStatsVO stats();

    // ------------------------------------------------------------------
    // Report projection
    // ------------------------------------------------------------------

    /**
     * List all reports filed against a given entity, newest first.
     *
     * @param entityType the entity type
     * @param entityId   the entity id
     * @return list of report VOs; never {@code null}
     */
    List<ReportVO> reportsForEntity(String entityType, String entityId);

    /**
     * List reports with pagination, filters and sorting. Builds the
     * {@code status} / {@code category} / {@code reporterId} /
     * {@code entityType} / {@code entityId} filter chain, defaulting the sort
     * to created-desc.
     *
     * @param query the query parameters
     * @return paginated result of report VOs; never {@code null}
     */
    PageResult<ReportVO> listReports(QueryReportsDTO query);

    /**
     * Get a single report by id.
     *
     * @param id the report id
     * @return the report VO; never {@code null}
     * @throws com.ulticode.common.exception.BusinessException
     *         ({@link com.ulticode.common.exception.ErrorCode#MODERATION_QUEUE_NOT_FOUND})
     *         if no report exists with the given id
     */
    ReportVO reportById(String id);

    // ------------------------------------------------------------------
    // Appeal projection
    // ------------------------------------------------------------------

    /**
     * List appeals with pagination, filters and sorting. Builds the
     * {@code status} / {@code queueId} / {@code appellantId} filter chain,
     * defaulting the sort to created-desc.
     *
     * @param query the query parameters
     * @return paginated result of appeal VOs; never {@code null}
     */
    PageResult<AppealVO> listAppeals(QueryAppealsDTO query);

    /**
     * List appeals filed by a given appellant, newest first.
     *
     * @param appellantId the appellant id
     * @return list of appeal VOs; never {@code null}
     */
    List<AppealVO> myAppeals(String appellantId);

    /**
     * Aggregate appeal statistics: counts of pending / under-review /
     * approved / rejected appeals.
     *
     * @return the appeal statistics VO; never {@code null}
     */
    AppealStatsVO appealStats();

    /**
     * Project a single {@link Appeal} entity to its VO, loading the appellant
     * and reviewer user names. Facade for the moderation write paths
     * ({@code createAppeal} / {@code reviewAppeal} / the authorisation-guarded
     * {@code getAppeal}) so they return the same view shape the read paths
     * serve, without holding the projection rules themselves.
     *
     * @param appeal the appeal entity; may be {@code null}
     * @return the appeal VO, or {@code null} if the input is {@code null}
     */
    AppealVO toAppealVO(Appeal appeal);
}
