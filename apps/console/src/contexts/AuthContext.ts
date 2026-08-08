/**
 * Authentication Context
 *
 * Provides centralized session expiration handling and WebSocket auth lifecycle.
 * 401/403 error handling is done in request.ts interceptor — this module
 * only manages the session expired callback.
 */

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
