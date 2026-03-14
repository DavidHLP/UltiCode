/**
 * @deprecated Auth is now handled via httpOnly cookies
 * These functions are kept for backward compatibility during migration
 */

const TOKEN_KEY = "ulticode_token";
const USER_ID_KEY = "ulticode_user_id";
const HAS_SESSION_KEY = "ulticode_has_session";

/**
 * @deprecated Tokens are now stored in httpOnly cookies
 */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

/**
 * @deprecated Tokens are now stored in httpOnly cookies
 */
export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

/**
 * @deprecated Tokens are now stored in httpOnly cookies
 */
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY);
}

/**
 * @deprecated User ID is now managed via cookies/session
 */
export function setUserId(userId: string) {
  localStorage.setItem(USER_ID_KEY, userId);
}

/**
 * @deprecated User ID is now managed via cookies/session
 */
export function removeUserId() {
  localStorage.removeItem(USER_ID_KEY);
}

/**
 * @deprecated Use the API to verify authentication status
 */
export function fetchCurrentUserId(): string | null {
  const stored = localStorage.getItem(USER_ID_KEY);
  if (stored) return stored;
  return null;
}

/**
 * Check if user is authenticated
 * Uses the session flag set during login to determine auth status.
 * For reliable authentication status, use auth store's isAuthenticated computed.
 *
 * @returns true if session flag exists, false otherwise
 */
export function isAuthenticated(): boolean {
  // Check for session flag (set during login/registration)
  // This is the new authentication method using httpOnly cookies
  return localStorage.getItem(HAS_SESSION_KEY) === "true";
}

/**
 * Set the session flag to indicate user has an active session
 */
export function setSessionFlag(): void {
  localStorage.setItem(HAS_SESSION_KEY, "true");
}

/**
 * Clear the session flag
 */
export function clearSessionFlag(): void {
  localStorage.removeItem(HAS_SESSION_KEY);
}

/**
 * Verify authentication by calling the API
 * This is the recommended way to check authentication status
 *
 * @returns Promise<boolean> - true if authenticated, false otherwise
 */
export async function verifyAuth(): Promise<boolean> {
  try {
    // Make a lightweight API call to verify auth
    // The endpoint should return 401 if not authenticated
    const response = await fetch(
      `${import.meta.env.VITE_API_BASE_URL || "http://localhost:9001"}/auth/me`,
      {
        method: "GET",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
      },
    );
    return response.ok;
  } catch {
    return false;
  }
}
