// ---------------------------------------------------------------------------
// CSRF Token Manager
// ---------------------------------------------------------------------------
// Manages the CSRF token in memory, survives page refreshes via the
// `refreshFromResponse` helper that extracts the token from API responses.
//
// Usage:
//   const csrf = createCsrfTokenManager();
//   csrf.setToken('abc123');
//   csrf.getToken();          // 'abc123'
//   csrf.clearToken();
//   csrf.refreshFromResponse({ csrfToken: 'new-token' });
// ---------------------------------------------------------------------------

/**
 * Factory function that creates an isolated CSRF token manager.
 * Call `getToken()` / `setToken()` / `clearToken()` on the returned object.
 *
 * Use `refreshFromResponse` to automatically sync the token after any
 * API call that returns a `csrfToken` field in its response body.
 *
 * @example
 * const csrf = createCsrfTokenManager();
 * csrf.setToken('initial-token');
 *
 * // After an API call:
 * csrf.refreshFromResponse(await fetch('/api/auth/me').then(r => r.json()));
 * csrf.getToken();   // latest token from the response
 */
export function createCsrfTokenManager(): CsrfTokenManager {
  let token: string | null = null;

  return {
    getToken(): string | null {
      return token;
    },

    setToken(newToken: string): void {
      if (!newToken || typeof newToken !== 'string') {
        token = null;
        return;
      }
      token = newToken;
    },

    clearToken(): void {
      token = null;
    },

    /**
     * Extract the `csrfToken` field from an API response object and
     * update the stored token if present.
     *
     * Safe to call even when `response.csrfToken` is missing — it simply
     * does nothing in that case.
     *
     * @param response - An object that may contain a `csrfToken` string field.
     */
    refreshFromResponse(response: { csrfToken?: string } | null | undefined): void {
      if (response && typeof response.csrfToken === 'string' && response.csrfToken.length > 0) {
        token = response.csrfToken;
      }
    },
  };
}

/**
 * Interface returned by `createCsrfTokenManager()` so callers get
 * a fully-typed object without coupling to the internal closure variable.
 */
export interface CsrfTokenManager {
  /** Returns the current token, or `null` if none is set. */
  getToken(): string | null;

  /** Stores a new token, replacing any previously stored value. */
  setToken(token: string): void;

  /** Clears the stored token. */
  clearToken(): void;

  /**
   * Updates the stored token from an API response's `csrfToken` field.
   * Silently ignores responses that don't contain the field.
   */
  refreshFromResponse(response: { csrfToken?: string } | null | undefined): void;
}
