/**
 * Single owner for the "session has expired" side-effect sequence in management.
 *
 * Both the propagated-401 strategy in `utils/request.ts` (clear-and-run) and
 * the refresh-failure bootstrap wiring in `main.ts` (`setOnAuthFailure`) funnel
 * through this helper, so there is exactly one place that clears the auth store
 * and redirects to login. `shared/auth-core` owns the cross-app trigger dedupe;
 * the management-side composition root owns the actual side effects.
 *
 * Mirrors `console/src/auth/runSessionExpired.ts`; management has no equivalent
 * of Console's AuthContext session-expired callback, so this clears the user
 * and redirects to the login route directly.
 */
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

export function runSessionExpired(): void {
  const authStore = useAuthStore()
  if (authStore.isAuthenticated) {
    authStore.clearUser()
  }
  if (router.currentRoute.value.name !== 'login') {
    router.push('/login')
  }
}
