/**
 * Single owner for the "session has expired" side-effect sequence in console.
 *
 * Both the propagated 401 strategy in `utils/request.ts` and the
 * refresh-failure bootstrap wiring in `main.ts` funnel through this helper
 * so there is exactly one place that clears the auth store and notifies
 * the session-expired callback. `shared/auth-core` owns the dedupe and
 * the cross-app trigger (architecture-review candidate #1); the
 * console-side composition root owns the actual side effects.
 */
import { useAuthStore } from '@/stores/auth';
import { getSessionExpiredCallback } from '@/contexts/AuthContext';

export function runSessionExpired(): void {
  const authStore = useAuthStore();
  if (authStore.isAuthenticated) {
    authStore.clearUser();
  }
  getSessionExpiredCallback()?.();
}
