package com.ulticode.auth.api.service;

import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.common.rpc.RpcResult;

import java.util.List;

/**
 * Read-only role-template query service.
 *
 * <p>Returns the permission set granted to all accounts holding a given
 * role, without any per-user overrides or expiry metadata. Each entry
 * has {@code source = "role"} and {@code expiresAt = null} because
 * role-template permissions are perpetual at the role level.
 *
 * <p>This service exists so that admin projections in consumer modules
 * (notably {@code DefaultAdminUserProjection}) can render role-template
 * permissions in the user-detail view without importing legacy entity
 * or mapper types.
 *
 * <p>Provider implementation lives in {@code backend-auth}; consumer
 * access is via Dubbo RPC.
 */
public interface RoleTemplateService {

    /**
     * Look up the permission template for a role.
     *
     * @param role the role name (e.g. {@code "ADMIN"},
     *             {@code "MODERATOR"}); must be non-null and non-blank
     * @return success with a list of {@link PermissionEntry} objects
     *         (each with {@code source = "role"},
     *         {@code expiresAt = null}); an empty list if the role has
     *         no template permissions. Failure codes:
     *         {@code ROLE_NOT_FOUND} (40404) when the role does
     */
    RpcResult<List<PermissionEntry>> getRoleTemplate(String role);
}
