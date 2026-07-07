package com.ulticode.modules.admin.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;

/**
 * Read-side projection for admin user management &mdash; a deep module that
 * owns every entity-to-VO projection rule, paginated list-query builder, and
 * cross-module enrichment (submission/solution stats via
 * {@link com.ulticode.modules.admin.port.AdminUserStatsReadPort}; role + direct
 * permissions via {@link com.ulticode.modules.permission.mapper.RolePermissionMapper}
 * and {@link com.ulticode.modules.permission.service.PermissionService}) for
 * the admin user surface.
 *
 * <p>This is the same shallow cluster lifted out of
 * {@link com.ulticode.modules.admin.service.UserManagementService} for the
 * Stage 2 rollout of ADR-0011: the paginated list read ({@link #getUsers},
 * ~50 LoC of query building + entity&rarr;VO shaping with <b>no</b>
 * stats/permission enrichment to keep the list path fast), and the
 * single-detail read ({@link #getUserById}, with full stats + permissions
 * snapshot). Sitting next to the write state machine (CRUD / ban / bulk
 * operations) in the same service made every projection tweak land in the
 * same file as the write paths.
 *
 * <p>After the deepening:
 * <ul>
 *   <li>{@link com.ulticode.modules.admin.service.UserManagementService}
 *       keeps the write state machine only (create / update / delete / ban /
 *       unban / reset-password / bulk operations). Write paths return the
 *       post-write VO by delegating to {@link #getUserById(String)} and never
 *       call any projection helper directly.</li>
 *   <li>{@link com.ulticode.modules.admin.service.UserPermissionService}
 *       also delegates to {@link #getUserById(String)} to compose the
 *       post-grant / post-revoke VO. The cross-service collaboration point
 *       (documented on the legacy {@code UserManagementService.getUserById}
 *       javadoc) now sits behind this seam.</li>
 *   <li>The controller depends on this projection directly for reads and on
 *       the service for writes.</li>
 * </ul>
 *
 * <p>All methods are pure reads; none mutate user state. The single-item
 * read throws {@link com.ulticode.common.exception.ErrorCode#USER_NOT_FOUND}
 * to preserve the access contract observed by the controller and by
 * {@link com.ulticode.modules.admin.service.UserPermissionService}.
 *
 * <p>Mirrors the {@link AdminSubmissionProjection}
 * / {@code ModerationProjection} / {@code AchievementProjection} shape
 * exactly. Cross-module entity imports ({@code User}, {@code RolePermission},
 * {@code UserPermission}) and their mappers live behind this seam; the admin
 * user services no longer import them.
 *
 * @author ulticode
 * @see AdminSubmissionProjection
 * @see com.ulticode.modules.moderation.projection.ModerationProjection
 * @see com.ulticode.modules.achievement.projection.AchievementProjection
 */
public interface AdminUserProjection {

    /**
     * Get a paginated list of users with filters (search across username /
     * email / name, role, active / banned status) and sorting. Returns the
     * <b>list-view</b> VO shape: entity&rarr;VO mapping only, <b>without</b>
     * stats or permissions enrichment. The detail path ({@link #getUserById})
     * owns enrichment; keeping the list path lean protects the paginated read
     * from N+1 stats / permission explosions across a page of users.
     *
     * @param query query parameters including filters, pagination, and sorting
     * @return paginated result of admin user VOs (list-view shape, no stats / permissions)
     */
    PageResult<AdminUserVO> getUsers(AdminUserQueryDTO query);

    /**
     * Get a single user by ID with full detail enrichment: stats snapshot
     * (submissions / accepted / solutions / streak via
     * {@link com.ulticode.modules.admin.port.AdminUserStatsReadPort}) and
     * permissions snapshot (role permissions + direct permissions, with
     * expired direct permissions filtered out).
     *
     * <p>This method is the collaboration point used by
     * {@link com.ulticode.modules.admin.service.UserPermissionService}
     * after a grant / revoke to return the latest VO, and by
     * {@link com.ulticode.modules.admin.service.UserManagementService}
     * after a write (update / ban / unban) to compose the post-write VO.
     *
     * @param id user ID
     * @return admin user VO with full stats + permissions snapshot
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#USER_NOT_FOUND}
     *         when the user does not exist
     */
    AdminUserVO getUserById(String id);
}
