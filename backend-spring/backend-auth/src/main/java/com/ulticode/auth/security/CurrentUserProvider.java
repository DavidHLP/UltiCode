package com.ulticode.auth.security;

/**
 * Seam for obtaining the currently authenticated user ID inside backend-auth.
 */
public interface CurrentUserProvider {

    /**
     * @return current authenticated user ID, or null if unauthenticated.
     */
    String getCurrentUserId();
}
