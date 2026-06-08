package com.ulticode.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Security utility class for accessing current user information from Spring Security context.
 */
public final class SecurityUtil {

    private SecurityUtil() {
        // Utility class, prevent instantiation
    }

    /**
     * Get the current authenticated user's ID.
     *
     * @return the user ID, or null if not authenticated
     */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return null;
    }

    /**
     * Get the current authenticated user's username.
     *
     * @return the username, or null if not authenticated
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())
                && authentication.getDetails() != null) {
            return authentication.getDetails().toString();
        }
        return null;
    }

    /**
     * Check if the current user is authenticated.
     *
     * @return true if authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
            && !"anonymousUser".equals(authentication.getPrincipal());
    }

    /**
     * Check if the current user has a specific role.
     *
     * @param role the role to check (without ROLE_ prefix)
     * @return true if the user has the role, false otherwise
     */
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + role));
    }

    /**
     * Check if the current user has any of the specified roles.
     *
     * <p>Used by service-layer guards to align with controller-level
     * {@code @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")} so that
     * SUPER_ADMIN callers are not incorrectly rejected by service code
     * that only matches the singular "ADMIN" role.
     *
     * @param roles the roles to check (without ROLE_ prefix)
     * @return true if the user has at least one of the roles, false otherwise
     */
    public static boolean hasAnyRole(String... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || roles == null || roles.length == 0) {
            return false;
        }
        Set<String> userRoles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        for (String role : roles) {
            if (userRoles.contains("ROLE_" + role)) {
                return true;
            }
        }
        return false;
    }
}
