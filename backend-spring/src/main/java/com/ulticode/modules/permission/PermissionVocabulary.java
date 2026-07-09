package com.ulticode.modules.permission;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Permission vocabulary — single source of truth for the allowed
 * {@code user_permissions.action} / {@code user_permissions.resource}
 * ENUMs and the super-admin-only guard predicate.
 *
 * <p>Previously the action / resource whitelists lived as inline
 * {@code Set.of(...)} literals inside {@code PermissionServiceImpl}, and
 * the "MANAGE_PERMISSIONS:SYSTEM" super-admin guard lived as an inline
 * string compare inside
 * {@code UserPermissionServiceImpl.requireSuperAdminForManagePermissionsSystem}.
 * The two implementations owned overlapping strings of the same vocabulary;
 * adding a new action, dropping an unused resource, or relaxing the guard
 * meant hunting through two unrelated files.
 *
 * <p>This class is the vocabulary's only owner. It mirrors
 * {@code com.ulticode.common.audit.AuditVocabulary}'s role for the audit
 * string constants, but is a Spring {@code @Component} (not a static
 * utility class) because the super-admin-only guard is a real predicate
 * with logic, not a constant. Constructor injection keeps callers free of
 * static lookups and makes the guard unit-testable through Mockito.
 *
 * <p><strong>Single source of truth contract.</strong>
 * The action / resource string values declared here must stay in sync with:
 * <ul>
 *   <li>{@code user_permissions.action} / {@code user_permissions.resource}
 *       ENUMs in {@code init-db/migrations/} (DDL).</li>
 *   <li>The entity javadoc on
 *       {@code com.ulticode.modules.permission.entity.UserPermission}.</li>
 *   <li>The OpenAPI examples on
 *       {@code com.ulticode.modules.admin.dto.GrantPermissionRequest} /
 *       {@code RevokePermissionRequest}.</li>
 * </ul>
 *
 * @author ulticode
 */
@Component
public class PermissionVocabulary {

    // -----------------------------------------------------------------------
    // Action constants — keep in sync with user_permissions.action ENUM.
    // -----------------------------------------------------------------------

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_READ = "READ";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_MODERATE = "MODERATE";
    public static final String ACTION_PUBLISH = "PUBLISH";
    public static final String ACTION_MANAGE_USERS = "MANAGE_USERS";
    public static final String ACTION_MANAGE_PERMISSIONS = "MANAGE_PERMISSIONS";

    // -----------------------------------------------------------------------
    // Resource constants — keep in sync with user_permissions.resource ENUM.
    // -----------------------------------------------------------------------

    public static final String RESOURCE_USER = "USER";
    public static final String RESOURCE_PROBLEM = "PROBLEM";
    public static final String RESOURCE_CONTEST = "CONTEST";
    public static final String RESOURCE_SOLUTION = "SOLUTION";
    public static final String RESOURCE_FORUM_POST = "FORUM_POST";
    public static final String RESOURCE_FORUM_COMMENT = "FORUM_COMMENT";
    public static final String RESOURCE_SYSTEM = "SYSTEM";
    public static final String RESOURCE_PROBLEM_LIST = "PROBLEM_LIST";
    public static final String RESOURCE_TAG = "TAG";

    // -----------------------------------------------------------------------
    // Whitelist sets — single source of truth for "is this action/resource
    // legal to grant/revoke via the admin endpoint".
    //
    // Set.of() is safe here: every element is a non-null compile-time
    // constant String. (The Map.of() null-safety rule does not apply.)
    // -----------------------------------------------------------------------

    private static final Set<String> ALLOWED_ACTIONS = Set.of(
        ACTION_CREATE, ACTION_READ, ACTION_UPDATE, ACTION_DELETE,
        ACTION_MODERATE, ACTION_PUBLISH,
        ACTION_MANAGE_USERS, ACTION_MANAGE_PERMISSIONS);

    private static final Set<String> ALLOWED_RESOURCES = Set.of(
        RESOURCE_USER, RESOURCE_PROBLEM, RESOURCE_CONTEST, RESOURCE_SOLUTION,
        RESOURCE_FORUM_POST, RESOURCE_FORUM_COMMENT, RESOURCE_SYSTEM,
        RESOURCE_PROBLEM_LIST, RESOURCE_TAG);

    // -----------------------------------------------------------------------
    // HIGH-1 super-admin-only capability — the (action, resource) pair that
    // represents "manage others' permissions". Granting or revoking this
    // capability is a privileged operation: a plain ADMIN who could grant
    // it would be able to indirectly elevate their own authority.
    // -----------------------------------------------------------------------

    private static final String SUPER_ADMIN_ONLY_ACTION = ACTION_MANAGE_PERMISSIONS;
    private static final String SUPER_ADMIN_ONLY_RESOURCE = RESOURCE_SYSTEM;

    /**
     * Whitelist of grantable action codes. Returned as the same immutable
     * {@code Set.of(...)} instance every call — safe to share with callers.
     */
    public Set<String> allowedActions() {
        return ALLOWED_ACTIONS;
    }

    /**
     * Whitelist of grantable resource codes. Returned as the same immutable
     * {@code Set.of(...)} instance every call — safe to share with callers.
     */
    public Set<String> allowedResources() {
        return ALLOWED_RESOURCES;
    }

    /**
     * @return {@code true} iff {@code action} is in the allowed-action
     *         whitelist (non-null, ENUM member).
     */
    public boolean isAllowedAction(String action) {
        return action != null && ALLOWED_ACTIONS.contains(action);
    }

    /**
     * @return {@code true} iff {@code resource} is in the allowed-resource
     *         whitelist (non-null, ENUM member).
     */
    public boolean isAllowedResource(String resource) {
        return resource != null && ALLOWED_RESOURCES.contains(resource);
    }

    /**
     * HIGH-1 guard predicate. Returns {@code true} iff the given
     * {@code (action, resource)} pair represents the privileged
     * "manage permissions on the system" capability — the only capability
     * that must be guarded to SUPER_ADMIN role.
     *
     * <p>Centralised here so the admin guard
     * ({@code UserPermissionServiceImpl.requireSuperAdminForManagePermissionsSystem})
     * no longer owns the magic-string compare, and so adding a future
     * super-admin-only capability means changing one method instead of
     * adding a new private guard helper per pair.
     */
    public boolean isSuperAdminOnly(String action, String resource) {
        return SUPER_ADMIN_ONLY_ACTION.equals(action)
            && SUPER_ADMIN_ONLY_RESOURCE.equals(resource);
    }
}