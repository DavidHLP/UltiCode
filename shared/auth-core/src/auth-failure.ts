/**
 * Decoupled auth-failure hook.
 *
 * console/management register their handlers (clear store + toast + redirect)
 * at startup. The 401 interceptor in axiosCsrfInterceptor invokes this
 * when refresh itself fails.
 *
 * Decoupling is necessary: `shared/auth-core` cannot import Pinia stores
 * or vue-router, which live in the consuming app.
 */
export type AuthFailureReason = 'refresh-failed';
export type AuthFailureHandler = (reason: AuthFailureReason, error: unknown) => void;

let handler: AuthFailureHandler | null = null;

export function setOnAuthFailure(h: AuthFailureHandler | null): void {
  handler = h;
}

export function clearOnAuthFailure(): void {
  handler = null;
}

export function triggerAuthFailure(reason: AuthFailureReason, error: unknown): void {
  if (handler) {
    try {
      handler(reason, error);
    } catch (e) {
      // Never let a faulty handler break the interceptor chain.
      // eslint-disable-next-line no-console
      console.error('onAuthFailure handler threw:', e);
    }
  }
}
