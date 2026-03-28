/**
 * Authentication utilities
 *
 * Note: Authentication is now handled via httpOnly cookies (access_token, refresh_token)
 * The auth store (@/stores/auth) manages user state and authentication status
 */

import { useAuthStore } from "@/stores/auth";

/**
 * Get current user ID from auth store
 * This is a convenience function for components that need just the user ID
 */
export function fetchCurrentUserId(): string | null {
  const authStore = useAuthStore();
  return authStore.userId || null;
}

/**
 * Check if user is authenticated
 * For reliable authentication status, use auth store's isAuthenticated computed.
 *
 * @deprecated Use authStore.isAuthenticated instead
 * @returns true if user is authenticated
 */
export function isAuthenticated(): boolean {
  const authStore = useAuthStore();
  return authStore.isAuthenticated;
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

/**
 * @deprecated Tokens are now stored in httpOnly cookies - this function does nothing
 */
export function removeToken(): void {
  // No-op - tokens are managed by cookies
}

/**
 * @deprecated User ID is now managed by auth store - this function does nothing
 */
export function removeUserId(): void {
  // No-op - user ID is managed by auth store
}
