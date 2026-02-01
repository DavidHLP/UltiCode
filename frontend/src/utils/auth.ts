/**
 * @deprecated Auth is now handled via httpOnly cookies
 * These functions are kept for backward compatibility during migration
 */

const TOKEN_KEY = "ulticode_token";
const USER_ID_KEY = "ulticode_user_id";

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
 * Note: This is a basic client-side check.
 * For reliable authentication status, make an API call.
 *
 * @returns true if authentication data exists, false otherwise
 */
export function isAuthenticated(): boolean {
  // Check for either localStorage token (legacy) or make API call
  return !!getToken();
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
