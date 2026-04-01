/**
 * Authentication Context
 *
 * Provides a centralized authentication state management system that:
 * 1. Coordinates between auth store, router, and HTTP client
 * 2. Handles session expiration globally
 * 3. Manages WebSocket authentication lifecycle
 * 4. Prevents auth-related race conditions
 *
 * This module should be imported BEFORE the router and stores.
 */

import { getSocketManager } from "@/lib/socket";
import { useAuthStore } from "@/stores/auth";

// Singleton state
let isInitialized = false;
let sessionExpiredCallback: (() => void) | null = null;
const pendingAuthRequests = new Set<string>();

/**
 * Initialize the authentication context
 * Called once during app bootstrap
 */
export async function initializeAuthContext(): Promise<void> {
  if (isInitialized) return;
  isInitialized = true;

  // Setup session expiration handler
  setupResponseInterceptor();
}

/**
 * Setup global response interceptor to handle auth errors
 */
function setupResponseInterceptor(): void {
  // We need to hook into the axios instance AFTER it's created
  // This is done by importing the module after request.ts is loaded
  import("@/utils/request").then(({ axiosInstance }) => {
    // Add our auth error handler as a response interceptor
    // This runs AFTER the request.ts response interceptor handles 401/403
    axiosInstance.interceptors.response.use(
      (response) => response,
      async (error) => {
        const config = error.config;
        if (!config) return Promise.reject(error);

        const status = error.response?.status;
        const url = config.url || "";

        // Track this request as no longer pending
        pendingAuthRequests.delete(url);

        // Handle 401/403 - session expired or invalid
        if (status === 401 || status === 403) {
          const authStore = useAuthStore();

          // Only trigger session expired if user was authenticated
          if (authStore.isAuthenticated) {
            console.warn(`[AuthContext] Session expired: ${url}`);

            // Clear user state
            authStore.clearUser();

            // Call session expired callback if set
            if (sessionExpiredCallback) {
              sessionExpiredCallback();
            }
          }
        }

        return Promise.reject(error);
      },
    );
  });
}

/**
 * Set callback for session expiration
 * Called when a 401/403 is received for an authenticated user
 */
export function onSessionExpired(callback: () => void): void {
  sessionExpiredCallback = callback;
}

/**
 * Check if user is currently authenticated
 * Shortcut for authStore.isAuthenticated
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
 * Use this when you need to verify the current auth state
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
 * Mark a URL as currently being requested for auth purposes
 * Used to prevent duplicate auth requests
 */
export function markAuthRequest(url: string): boolean {
  if (pendingAuthRequests.has(url)) {
    return false; // Already pending
  }
  pendingAuthRequests.add(url);
  return true; // New request
}

/**
 * Clear a URL from pending auth requests
 */
export function unmarkAuthRequest(url: string): void {
  pendingAuthRequests.delete(url);
}

/**
 * Check if there are pending auth requests
 */
export function hasPendingAuthRequests(): boolean {
  return pendingAuthRequests.size > 0;
}

/**
 * Setup WebSocket connection based on auth state
 * Call this after auth is initialized
 */
export function setupWebSocketAuth(): void {
  const authStore = useAuthStore();
  const socketManager = getSocketManager();

  // Watch for auth state changes
  let previousAuth = authStore.isAuthenticated;

  // Use a simple polling or store watch
  // The notification store handles this, but we provide centralized control
  setInterval(() => {
    const currentAuth = authStore.isAuthenticated;
    if (currentAuth !== previousAuth) {
      previousAuth = currentAuth;
      if (currentAuth) {
        socketManager.connect();
      } else {
        socketManager.disconnect();
      }
    }
  }, 1000);
}

export default {
  initializeAuthContext,
  onSessionExpired,
  isAuthenticated,
  getCurrentUser,
  refreshUserState,
  markAuthRequest,
  unmarkAuthRequest,
  hasPendingAuthRequests,
  setupWebSocketAuth,
};
