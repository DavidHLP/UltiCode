/**
 * CSRF Token Management Utility
 *
 * This utility handles the storage and retrieval of CSRF tokens
 * returned by the backend after successful authentication.
 *
 * CSRF tokens are stored in memory (not localStorage) to prevent
 * XSS attacks from stealing them.
 */

let csrfToken: string | null = null;

/**
 * Retrieve the current CSRF token
 * @returns The CSRF token or null if not set
 */
export function getCsrfToken(): string | null {
  return csrfToken;
}

/**
 * CSRF Manager object for compatibility with auth store
 */
export const csrfManager = {
  getToken: () => csrfToken,
  setToken: (token: string) => { csrfToken = token; },
  clearToken: () => { csrfToken = null; },
  refreshFromResponse: (response: { csrfToken?: string }) => {
    if (response.csrfToken) {
      csrfToken = response.csrfToken;
    }
  },
};
