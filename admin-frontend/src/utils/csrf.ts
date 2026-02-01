/**
 * CSRF Token Management Utility
 *
 * This utility handles the storage and retrieval of CSRF tokens
 * returned by the backend after successful authentication.
 *
 * CSRF tokens are stored in memory (not localStorage) to prevent
 * XSS attacks from stealing them.
 */

let csrfToken: string | null = null

/**
 * Store the CSRF token
 * @param token - The CSRF token from the login response
 */
export function setCsrfToken(token: string): void {
  csrfToken = token
}

/**
 * Retrieve the current CSRF token
 * @returns The CSRF token or null if not set
 */
export function getCsrfToken(): string | null {
  return csrfToken
}

/**
 * Clear the stored CSRF token
 * Called on logout to clean up
 */
export function clearCsrfToken(): void {
  csrfToken = null
}
