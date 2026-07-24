import { ref, computed, type ComputedRef, type Ref } from 'vue'
import {
  csrfManager,
  clearCsrfToken,
  checkPermission,
  checkRole,
  checkAnyRole,
  type User,
} from './index'

/**
 * Adapter the auth store factory calls to reach the backend. Each app
 * supplies its own (typically its `@/api/auth` module). All four methods
 * already return the unwrapped payload — the per-app `request.ts`
 * interceptor strips the `{ code, message, data }` envelope.
 */
export interface AuthStoreAdapter {
  /** POST /auth/login — returns the login envelope carrying csrfToken. */
  login(credentials: unknown): Promise<unknown>
  /** POST /auth/logout. */
  logout(): Promise<void>
  /** GET /auth/me — returns `{ user, csrfToken }`. */
  getCurrentUser(): Promise<{ user: User; csrfToken?: string }>
  /** GET /auth/permissions — returns the caller's permission strings. */
  getPermissions(): Promise<string[]>
}

/**
 * CSRF-bearing login response — anything with an optional `csrfToken`
 * field. `csrfManager.refreshFromResponse` reads it defensively.
 */
interface LoginResponseLike {
  csrfToken?: string
  [key: string]: unknown
}

/**
 * Internals returned by {@link createAuthStore}. Each app's Pinia
 * `defineStore` spreads these into its setup function (and may layer
 * app-specific computed selectors on top). The shape mirrors the
 * pre-factory management store so existing call sites compile unchanged.
 */
export interface AuthStoreInternals {
  user: Ref<User | null>
  permissions: Ref<Set<string>>
  isInitialized: Ref<boolean>
  isAuthenticated: ComputedRef<boolean>
  userRole: ComputedRef<string | undefined>
  userName: ComputedRef<string | undefined>
  login: (credentials: unknown) => Promise<boolean>
  logout: () => Promise<void>
  loadPermissions: () => Promise<void>
  fetchUser: () => Promise<User | null>
  /**
   * Lazy-load the user record if missing; no-op when the store already
   * has a user. Used by the auth-navigation guard to satisfy
   * {@code ensureUser} without an unconditional re-fetch.
   */
  ensureUser: () => Promise<User | null>
  initialize: () => Promise<void>
  clearUser: () => void
  hasPermission: (action: string, resource: string) => boolean
  hasRole: (role: string) => boolean
  hasAnyRole: (roles: string[]) => boolean
}

/**
 * Deep factory that owns the admin/console auth-store contract once.
 *
 * <p>Both apps previously duplicated this logic line-for-line: the
 * login&rarr;fetchUser&rarr;loadPermissions chain, CSRF token persistence
 * via {@link csrfManager}, the clear-on-logout/clearUser teardown, the
 * initialize-once guard, and the permission/role selectors. The
 * "documented seam leak" (management's store header deferred to console)
 * is closed by moving the shared body here and parameterising the
 * per-app backend adapter.
 *
 * <p>Console's richer status state machine (idle/loading/ready/error)
 * stays in its own {@code useAuthSession} composable; this factory
 * serves the simpler boolean-surface stores (management today, and any
 * future app that does not need the status machine).
 *
 * @param adapter per-app backend adapter
 * @returns the store internals (refs, computeds, actions)
 */
export function createAuthStore(adapter: AuthStoreAdapter): AuthStoreInternals {
  const user = ref<User | null>(null)
  const permissions = ref<Set<string>>(new Set())
  const isInitialized = ref(false)

  const isAuthenticated = computed(() => !!user.value)
  const userRole = computed(() => user.value?.role)
  const userName = computed(() => user.value?.name || user.value?.username)

  async function login(credentials: unknown): Promise<boolean> {
    try {
      const loginResponse = (await adapter.login(credentials)) as LoginResponseLike
      csrfManager.refreshFromResponse(loginResponse)
      await fetchUser()
      return true
    } catch (error) {
      console.error('Login failed:', error)
      return false
    }
  }

  async function logout(): Promise<void> {
    try {
      await adapter.logout()
    } catch (error) {
      console.error('Logout error:', error)
    } finally {
      user.value = null
      permissions.value.clear()
      clearCsrfToken()
      csrfManager.clearToken()
    }
  }

  async function loadPermissions(): Promise<void> {
    if (!user.value) return
    try {
      const response = await adapter.getPermissions()
      permissions.value = new Set(response || [])
    } catch (error) {
      console.error('Failed to load permissions:', error)
    }
  }

  async function fetchUser(): Promise<User | null> {
    try {
      const response = await adapter.getCurrentUser()
      user.value = response.user
      csrfManager.refreshFromResponse(response)
      await loadPermissions()
      return response.user
    } catch {
      // 401 is expected for unauthenticated users — no log spam.
      user.value = null
      permissions.value.clear()
      return null
    }
  }

  /**
   * Lazy user loader: only fetch if the store is empty. This is the
   * semantically-correct implementation of the {@code NavigationAuthAdapter.ensureUser}
   * contract that the auth-navigation guard expects. The previous
   * management adapter routed the seam's {@code ensureUser} call through
   * {@code fetchUser} (unconditional), which violated the contract and
   * caused redundant network round-trips on every protected navigation
   * for an already-authenticated user.
   */
  async function ensureUser(): Promise<User | null> {
    if (user.value) return user.value
    return await fetchUser()
  }

  async function initialize(): Promise<void> {
    if (isInitialized.value) return
    // Restore session from httpOnly cookies; /auth/me returns 401 when no
    // valid session exists, handled gracefully above.
    try {
      await fetchUser()
    } finally {
      isInitialized.value = true
    }
  }

  function clearUser(): void {
    user.value = null
    permissions.value.clear()
    clearCsrfToken()
    csrfManager.clearToken()
  }

  function hasPermission(action: string, resource: string): boolean {
    return checkPermission(permissions.value, action, resource)
  }

  function hasRole(role: string): boolean {
    return checkRole(user.value?.role, role)
  }

  function hasAnyRole(roles: string[]): boolean {
    return checkAnyRole(user.value?.role, roles)
  }

  return {
    user,
    permissions,
    isInitialized,
    isAuthenticated,
    userRole,
    userName,
    login,
    logout,
    loadPermissions,
    fetchUser,
    ensureUser,
    initialize,
    clearUser,
    hasPermission,
    hasRole,
    hasAnyRole,
  }
}
