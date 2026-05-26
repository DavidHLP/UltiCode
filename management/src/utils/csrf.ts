/**
 * CSRF Token Management Utility
 *
 * This is a thin wrapper around the shared csrfManager from shared/auth-core.
 * It provides convenience functions for CSRF token management while ensuring
 * a single source of truth for the token across the application.
 *
 * The underlying csrfManager survives page refreshes via the refreshFromResponse
 * method that extracts tokens from API responses.
 */

import { createCsrfTokenManager, type CsrfTokenManager } from '@/shared/auth-core/src/csrf'

// Single instance of the CSRF token manager
const csrfManager: CsrfTokenManager = createCsrfTokenManager()

/**
 * Store the CSRF token
 * @param token - The CSRF token from the login response
 */
export function setCsrfToken(token: string): void {
  csrfManager.setToken(token)
}

/**
 * Retrieve the current CSRF token
 * @returns The CSRF token or null if not set
 */
export function getCsrfToken(): string | null {
  return csrfManager.getToken()
}

/**
 * Clear the stored CSRF token
 * Called on logout to clean up
 */
export function clearCsrfToken(): void {
  csrfManager.clearToken()
}

/**
 * Export the csrfManager instance for direct access to refreshFromResponse
 * This is used in auth.ts to sync tokens from API responses
 */
export { csrfManager }
