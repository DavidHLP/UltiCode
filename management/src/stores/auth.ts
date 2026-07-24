import { defineStore } from 'pinia'
import { authApi, type LoginCredentials, type User } from '@/api/auth'
import { createAuthStore } from '@/shared/auth-core/src'

/**
 * Management auth store.
 *
 * <p>After architecture-review candidate #4, the duplicated
 * login/logout/fetchUser/loadPermissions/initialize/clearUser/hasPermission/
 * hasRole/hasAnyRole chain and the CSRF persistence contract live once in
 * {@link createAuthStore} (shared/auth-core). This store is a thin Pinia
 * wrapper that binds the factory to the management backend adapter and
 * re-exports the same surface existing call sites depend on.
 *
 * <p>After architecture-review candidate #3 follow-up, this store
 * re-exposes {@code ensureUser} so the auth-navigation adapter in
 * {@code router/index.ts} can satisfy the seam's lazy-loader contract
 * without falling back to {@code fetchUser} (unconditional refetch).
 *
 * <p>The CSRF contract documented here previously (anonymous-only
 * exemption, "must logout before re-login") still holds — it is enforced
 * by the server's CsrfValidationFilter and the shared
 * {@code csrfManager.refreshFromResponse} calls inside the factory.
 */
export const useAuthStore = defineStore('auth', () => {
  const internals = createAuthStore(authApi)

  return {
    user: internals.user,
    permissions: internals.permissions,
    isInitialized: internals.isInitialized,
    isAuthenticated: internals.isAuthenticated,
    userRole: internals.userRole,
    userName: internals.userName,
    login: internals.login,
    logout: internals.logout,
    loadPermissions: internals.loadPermissions,
    fetchUser: internals.fetchUser,
    ensureUser: internals.ensureUser,
    initialize: internals.initialize,
    clearUser: internals.clearUser,
    hasPermission: internals.hasPermission,
    hasRole: internals.hasRole,
    hasAnyRole: internals.hasAnyRole,
  }
})

export type { LoginCredentials, User }
