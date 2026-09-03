package com.ulticode.modules.admin.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;

/**
 * Compatibility projection for admin user reads.
 *
 * <p>The list operation owns the lean list-view entity-to-VO mapping. The
 * detail operation delegates to the Admin-internal
 * {@link com.ulticode.modules.admin.query.AdminUserDetailQuery} seam, which
 * owns cross-owner aggregation and explicit section availability.
 *
 * <p>The legacy surface remains so existing controllers and write services
 * keep their established {@link com.ulticode.common.response.Result} and
 * {@link com.ulticode.common.exception.BusinessException} behavior while the
 * detail implementation is deepened behind one use-case interface.
 *
 * @author ulticode
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
     * Get a single user by ID through the detail query seam. Optional profile,
     * stats, and permission sections remain visible on the returned VO when
     * available; an unavailable section is marked by
     * {@link AdminUserVO#getDegradationStatus()} instead of becoming a
     * successful empty value.
     *
     * <p>Authoritative account not-found and owner transport failures retain
     * their established {@link com.ulticode.common.exception.BusinessException}
     * mappings for existing HTTP callers.
     *
     * @param id user ID
     * @return admin user VO with any available detail sections
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.admin.error.AdminErrorCode#USER_NOT_FOUND}
     *         when the user does not exist
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.admin.error.AdminErrorCode#OWNER_QUERY_UNAVAILABLE}
     *         when Auth cannot answer whether the user exists
     */
    AdminUserVO getUserById(String id);
}
