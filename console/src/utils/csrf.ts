/**
 * CSRF Token Management Utility
 *
 * This utility handles the storage and retrieval of CSRF tokens
 * returned by the backend after successful authentication.
 *
 * CSRF tokens are stored in memory (not localStorage) to prevent
 * XSS attacks from stealing them. On page refresh, falls back to
 * reading from the csrf_token cookie set by the backend.
 */

let csrfToken: string | null = null;

function readCsrfFromCookie(): string | null {
  const value = `; ${document.cookie}`;
  const parts = value.split(`; csrf_token=`);
  if (parts.length === 2) return parts.pop()?.split(";").shift() || null;
  return null;
}

/**
 * Retrieve the current CSRF token.
 * Falls back to reading from cookie (e.g. after page refresh).
 * @returns The CSRF token or null if not set
 */
export function getCsrfToken(): string | null {
  return csrfToken || readCsrfFromCookie();
}

/**
 * CSRF Manager object for compatibility with auth store
 */
export const csrfManager = {
  getToken: () => csrfToken || readCsrfFromCookie(),
  setToken: (token: string) => { csrfToken = token; },
  clearToken: () => { csrfToken = null; },
  refreshFromResponse: (response: { csrfToken?: string }) => {
    if (response.csrfToken) {
      csrfToken = response.csrfToken;
    }
  },
};
