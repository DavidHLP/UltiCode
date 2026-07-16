/**
 * Decoupled auth-failure hook.
 *
 * console/management register their handlers (clear store + toast + redirect)
 * at startup. The 401 interceptor in axiosCsrfInterceptor invokes this
 * when refresh itself fails.
 *
 * Decoupling is necessary: `shared/auth-core` cannot import Pinia stores
 * or vue-router, which live in the consuming app.
 *
 * Concurrent same-reason triggers (a fan-in of 401s, or a burst of
 * refresh failures during sign-out) collapse to a single handler
 * invocation so a flood of failures produces one redirect, not N.
 */
export type AuthFailureReason = 'refresh-failed' | 'unauthorized-response';
export type AuthFailureHandler = (reason: AuthFailureReason, error: unknown) => void;

let handler: AuthFailureHandler | null = null;
const inFlight = new Set<AuthFailureReason>();

export function setOnAuthFailure(h: AuthFailureHandler | null): void {
  handler = h;
}

export function clearOnAuthFailure(): void {
  handler = null;
  inFlight.clear();
}

/**
 * True if a handler is currently registered. Used by tests and by
 * bootstrap code that wants to assert the runtime is wired before
 * triggering.
 */
export function hasOnAuthFailure(): boolean {
  return handler !== null;
}

export function triggerAuthFailure(reason: AuthFailureReason, error: unknown): void {
  if (inFlight.has(reason)) return;
  if (!handler) return;
  inFlight.add(reason);
  try {
    handler(reason, error);
  } catch (e) {
    // Never let a faulty handler break the interceptor chain.
    // eslint-disable-next-line no-console
    console.error('onAuthFailure handler threw:', e);
  } finally {
    inFlight.delete(reason);
  }
}
