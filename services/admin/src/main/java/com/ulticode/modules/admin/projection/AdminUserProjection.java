package com.ulticode.modules.admin.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;

/**
 * Compatibility projection for admin user list reads.
 *
 * <p>The list operation owns the lean list-view entity-to-VO mapping. The
 * detail operation is owned by the Admin-internal AdminUserDetailQuery seam,
 * which owns cross-owner aggregation and explicit section availability.
 *
 * @author ulticode
 */
public interface AdminUserProjection {

    /**
     * Get a paginated list of users with filters (search across username /
     * email / name, role, active / banned status) and sorting. Returns the
     * <b>list-view</b> VO shape: entity&rarr;VO mapping only, <b>without</b>
     * stats or permissions enrichment. The detail path owns enrichment;
     * keeping the list path lean protects the paginated read
     * from N+1 stats / permission explosions across a page of users.
     *
     * @param query query parameters including filters, pagination, and sorting
     * @return paginated result of admin user VOs (list-view shape, no stats / permissions)
     */
    PageResult<AdminUserVO> getUsers(AdminUserQueryDTO query);
}
