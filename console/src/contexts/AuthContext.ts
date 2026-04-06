/**
 * Authentication Context
 *
 * Provides centralized session expiration handling and WebSocket auth lifecycle.
 * 401/403 error handling is done in request.ts interceptor — this module
 * only manages the session expired callback.
 */

import { watch } from "vue";
import { getSocketManager } from "@/lib/socket";
import { useAuthStore } from "@/stores/auth";

// Singleton state
let sessionExpiredCallback: (() => void) | null = null;

/**
 * Initialize the authentication context
 * Called once during app bootstrap
 */
export function initializeAuthContext(): void {
  // Session expired callback is set by main.ts
  // No response interceptor needed — request.ts handles 401/403
}

/**
 * Set callback for session expiration
 * Called when a 401/403 is received for an authenticated user
 */
export function onSessionExpired(callback: () => void): void {
  sessionExpiredCallback = callback;
}

/**
 * Get the current session expired callback
 * Used by request.ts to trigger session expiration handling
 */
export function getSessionExpiredCallback(): (() => void) | null {
  return sessionExpiredCallback;
}

/**
 * Check if user is currently authenticated
 */
export function isAuthenticated(): boolean {
  try {
    const authStore = useAuthStore();
    return authStore.isAuthenticated;
  } catch {
    return false;
  }
}

/**
 * Get current user if authenticated
 */
export function getCurrentUser() {
  try {
    const authStore = useAuthStore();
    return authStore.user;
  } catch {
    return null;
  }
}

/**
 * Request a fresh user state from the server
 */
export async function refreshUserState(): Promise<boolean> {
  try {
    const authStore = useAuthStore();
    const user = await authStore.fetchUser();
    return !!user;
  } catch {
    return false;
  }
}

/**
 * Setup WebSocket connection based on auth state
 */
export function setupWebSocketAuth(): void {
  const authStore = useAuthStore();
  const socketManager = getSocketManager();

  watch(
    () => authStore.isAuthenticated,
    (isAuth) => {
      if (isAuth) {
        socketManager.connect();
      } else {
        socketManager.disconnect();
      }
    },
    { immediate: true },
  );
}

export default {
  initializeAuthContext,
  onSessionExpired,
  getSessionExpiredCallback,
  isAuthenticated,
  getCurrentUser,
  refreshUserState,
  setupWebSocketAuth,
};
