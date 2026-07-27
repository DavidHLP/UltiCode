package com.ulticode.auth.permission;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Permission vocabulary — single source of truth for the allowed
 * {@code user_permissions.action} / {@code user_permissions.resource}
 * ENUMs and the super-admin-only guard predicate.
 */
@Component
public class PermissionVocabulary {

    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "CREATE", "READ", "UPDATE", "DELETE",
            "MODERATE", "PUBLISH", "MANAGE_USERS", "MANAGE_PERMISSIONS"
    );

    private static final Set<String> ALLOWED_RESOURCES = Set.of(
            "USER", "PROBLEM", "CONTEST", "SOLUTION",
            "FORUM_POST", "FORUM_COMMENT", "SYSTEM", "PROBLEM_LIST", "TAG"
    );

    /**
     * Check if an action string matches the allowed action vocabulary.
     */
    public boolean isAllowedAction(String action) {
        return StringUtils.hasText(action) && ALLOWED_ACTIONS.contains(action.trim().toUpperCase());
    }

    /**
     * Check if a resource string matches the allowed resource vocabulary.
     */
    public boolean isAllowedResource(String resource) {
        return StringUtils.hasText(resource) && ALLOWED_RESOURCES.contains(resource.trim().toUpperCase());
    }

    /**
     * Predicate for super-admin-only MANAGE_PERMISSIONS:SYSTEM guard.
     */
    public boolean isSuperAdminOnlyPermission(String action, String resource) {
        return "MANAGE_PERMISSIONS".equalsIgnoreCase(action) && "SYSTEM".equalsIgnoreCase(resource);
    }
}
