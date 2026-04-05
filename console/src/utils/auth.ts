/**
 * Authentication utilities
 *
 * ⚠️ DEPRECATED - This module is deprecated. Use `@/composables/useAuth` instead.
 *
 * Migration guide:
 * ```ts
 * // OLD (deprecated)
 * import { isAuthenticated, fetchCurrentUserId } from '@/utils/auth';
 * if (isAuthenticated()) { ... }
 *
 * // NEW (recommended)
 * import { useAuth } from '@/composables/useAuth';
 * const { isAuthenticated, userId } = useAuth();
 * if (isAuthenticated.value) { ... }
 * ```
 *
 * Note: Authentication is handled via httpOnly cookies (access_token, refresh_token)
 * The auth store (@/stores/auth) manages user state and authentication status
 */

import { useAuthStore } from "@/stores/auth";
import { apiGet } from "@/utils/request";

/**
 * Get current user ID from auth store
 *
 * @deprecated Use `useAuth().userId` composable instead
 * @example
 * ```ts
 * // OLD
 * import { fetchCurrentUserId } from '@/utils/auth';
 * const userId = fetchCurrentUserId();
 *
 * // NEW
 * import { useAuth } from '@/composables/useAuth';
 * const { userId } = useAuth();
 * ```
 */
export function fetchCurrentUserId(): string | null {
  const authStore = useAuthStore();
  return authStore.userId || null;
}

/**
 * Check if user is authenticated
 *
 * @deprecated Use `useAuth().isAuthenticated` composable instead
 * @example
 * ```ts
 * // OLD
 * import { isAuthenticated } from '@/utils/auth';
 * if (isAuthenticated()) { ... }
 *
 * // NEW
 * import { useAuth } from '@/composables/useAuth';
 * const { isAuthenticated } = useAuth();
 * if (isAuthenticated.value) { ... }
 * ```
 */
export function isAuthenticated(): boolean {
  const authStore = useAuthStore();
  return authStore.isAuthenticated;
}

/**
 * Verify authentication by calling the API
 *
 * Note: This function makes a real API call. For reactive authentication
 * state, use the `useAuth()` composable instead.
 *
 * @returns Promise<boolean> - true if authenticated, false otherwise
 */
export async function verifyAuth(): Promise<boolean> {
  try {
    // Use apiGet to ensure the request goes through the axios interceptor chain
    // (CSRF token attach, locale headers, error handling, etc.)
    await apiGet("/auth/me", { skipErrorHandler: true });
    return true;
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
