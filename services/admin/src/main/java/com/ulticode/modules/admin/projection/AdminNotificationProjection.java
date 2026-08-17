package com.ulticode.modules.admin.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminNotificationQueryDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.notification.api.dto.NotificationAdminDTO;

/**
 * Read-side deep module for the admin system-notification surface &mdash;
 * owns every entity-to-{@code AdminNotificationVO} projection rule, the
 * paginated list read, the batch creator enrichment and the lightweight
 * "announcement intent" VO builder used when every recipient opts out of a
 * broadcast.
 *
 * <p>Lifted out per ADR-0011 (admin projection series): a Stage 4 deepening
 * matching the {@code AdminSubmissionProjection} /
 * {@code AdminUserProjection} / {@code AdminForumProjection} /
 * {@code AdminSolutionProjection} / {@code AdminContestProjection} shape.
 * Before the extraction:
 * <ul>
 *   <li>The 18 KB {@code AdminNotificationServiceImpl} mixed the write state
 *       machine (create / update / soft-delete system announcements) with
 *       read-side concerns (paginated list read with sort-field whitelist,
 *       batch creator User enrichment, three {@code toAdminVO} overloads,
 *       the {@code buildAnnouncementVo} helper for the all-opted-out
 *       case).</li>
 *   <li>The sort-field whitelist and the pagination-default rules sat next
 *       to the write path &mdash; so every UI filter tweak had to land in
 *       the same file as the broadcast write logic.</li>
 *   <li>The cross-mapper User lookup that powers the {@code creator} field
 *       on the VO leaked across the admin seam into the orchestration
 *       service.</li>
 * </ul>
 *
 * <p>After the deepening:
 * <ul>
 *   <li>{@link com.ulticode.modules.admin.service.AdminNotificationService}
 *       keeps the write state machine only (create / update / soft-delete
 *       system announcements, broadcast preference gating). Write paths
 *       that return {@code AdminNotificationVO} ({@code createSystemNotification},
 *       {@code updateSystemNotification}) call
 *       {@link #toAdminVO(com.ulticode.notification.api.dto.NotificationAdminDTO)}
 *       so the controller contract is unchanged &mdash; the shape rule no
 *       longer lives in the service.</li>
 *   <li>Future admins or port-driven consumers depend on this projection for
 *       reads and on the service for writes &mdash; mirroring the
 *       AdminSubmissionProjection / -ModerationProjection pattern documented
 *       in the deep-modules index of {@code backend-spring/AGENTS.md}.</li>
 * </ul>
 *
 * <p>Cross-module read access ({@code UserMapper.selectBatchIds} for the
 * {@code creator} field on the VO) lives behind this seam; the orchestration
 * service no longer imports it for read enrichment.
 *
 * @author ulticode
 * @see com.ulticode.modules.admin.projection.AdminSubmissionProjection
 * @see com.ulticode.modules.admin.projection.AdminContestProjection
 * @see com.ulticode.modules.moderation.projection.ModerationProjection
 */
public interface AdminNotificationProjection {

    /**
     * Get a paginated list of deduplicated system announcements with
     * server-side filtering (keyword / type / category / announcementId) and
     * sorting (createdAt / title / type / category / announcementId). The
     * resulting {@code AdminNotificationVO}s carry the creator enrichment
     * already batch-loaded via {@code UserMapper.selectBatchIds}.
     *
     * <p>Invalid {@code sortBy} values are silently dropped to {@code null}
     * so the mapper-level {@code <otherwise>n.created_at</otherwise>} takes
     * over &mdash; matching the legacy behaviour carried by
     * {@code AdminNotificationServiceImpl}.
     *
     * @param query query parameters including filters, pagination, and sorting
     * @return paginated result of admin notification VOs
     */
    PageResult<AdminNotificationVO> getSystemNotifications(AdminNotificationQueryDTO query);

    /**
     * Project a {@link NotificationAdminDTO} to the
     * {@link AdminNotificationVO} shape, including the nested
     * {@code creator} enrichment. Pure shape rule &mdash; performs a
     * user lookup only when the input carries a
     * {@code createdBy} entry.
     *
     * <p>Convenience overload for the write path
     * ({@code createSystemNotification} /
     * {@code updateSystemNotification}) which already holds a single DTO
     * and wants to round-trip the VO without a list batch-load.
     *
     * @param notification source DTO (may be {@code null})
     * @return projected admin notification VO, or {@code null} when the
     *         input is {@code null}
     */
    AdminNotificationVO toAdminVO(NotificationAdminDTO notification);

    /**
     * Build a lightweight VO for an announcement that produced zero
     * deliveries (every recipient opted out). No row is persisted; this only
     * gives the admin a response payload and an audit anchor carrying the
     * {@code announcementId}.
     *
     * @param request        the original create request (title / content / type)
     * @param category       the resolved category (defaults to SYSTEM)
     * @param announcementId the UUID assigned to this broadcast
     * @return admin notification VO carrying only the announcement metadata
     */
    AdminNotificationVO buildAnnouncementVO(CreateSystemNotificationRequest request,
                                            String category,
                                            String announcementId);
}
