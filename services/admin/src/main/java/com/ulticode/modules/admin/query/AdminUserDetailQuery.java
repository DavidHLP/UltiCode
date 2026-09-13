package com.ulticode.modules.admin.query;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.AdminUserVO;

/**
 * Admin-internal seam for loading one user's complete detail read model.
 *
 * <p>The implementation owns provider ordering, cross-owner aggregation, and
 * fail-closed availability semantics. Callers do not need to know which RPC
 * supplies a section, how many provider calls are required, or how a top-level
 * detail failure maps to an Admin business error.
 */
public interface AdminUserDetailQuery {

    /**
     * Load one user's account, profile, statistics, and authorization facts.
     *
     * <p>A returned result distinguishes an authoritative not-found from an
     * owner transport failure. A found user may still have unavailable
     * optional sections; those sections are never represented as successful
     * empty values.
     *
     * @param userId Auth account identifier
     * @return detail result with top-level and per-section availability
     */
    AdminUserDetailResult loadUserDetail(String userId);

    /**
     * Load a user's detail VO and translate top-level query failures once at
     * the Admin detail seam.
     *
     * <p>A null result and an authoritative not-found both preserve the
     * established {@link AdminErrorCode#USER_NOT_FOUND} behavior. A transport
     * failure or an invalid found result preserves the established
     * {@link AdminErrorCode#OWNER_QUERY_UNAVAILABLE} behavior.
     *
     * @param userId Auth account identifier
     * @return detail VO with available optional sections
     * @throws BusinessException with {@link AdminErrorCode#USER_NOT_FOUND}
     *         when the account is absent
     * @throws BusinessException with
     *         {@link AdminErrorCode#OWNER_QUERY_UNAVAILABLE} when the query
     *         cannot provide a valid detail
     */
    default AdminUserVO loadUserDetailOrThrow(String userId) {
        AdminUserDetailResult result = loadUserDetail(userId);
        if (result == null || result.failure() == AdminUserDetailResult.Failure.NOT_FOUND) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }
        if (result.failure() == AdminUserDetailResult.Failure.TRANSPORT_UNAVAILABLE
                || result.user() == null) {
            throw new BusinessException(
                    AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                    "Admin user detail query unavailable");
        }
        return result.user();
    }
}
