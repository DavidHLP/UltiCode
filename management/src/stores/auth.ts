import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type LoginCredentials, type User } from '@/api/auth'
import { csrfManager, clearCsrfToken } from '@/utils/csrf'
import { checkPermission, checkRole, checkAnyRole } from '@/shared/auth-core/src'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const permissions = ref<Set<string>>(new Set())
  const isInitialized = ref(false)

  const isAuthenticated = computed(() => !!user.value)
  const userRole = computed(() => user.value?.role)
  const userName = computed(() => user.value?.name || user.value?.username)

  /**
   * Login with username and password.
   *
   * See `console/src/stores/auth.ts` for the full CSRF contract — the
   * anonymous-only exemption and the "must logout first before re-login"
   * pitfall apply identically on this side. In short: the server's
   * CsrfValidationFilter bypasses CSRF for unauthenticated POSTs, so a
   * cold-start login works without an X-CSRF-Token header. Re-submitting
   * the login form while still logged in returns 403 "CSRF token is
   * required"; the UI must call `logout()` first or refresh the page.
   *
   * Persists csrfToken via `csrfManager.refreshFromResponse` so subsequent
   * state-changing requests can attach it.
   */
  async function login(credentials: LoginCredentials) {
    try {
      const loginResponse = await authApi.login(credentials)
      // Use csrfManager to handle CSRF token (survives page refresh)
      csrfManager.refreshFromResponse(loginResponse)
      // Login response returns partial user data, fetch full user data
      await fetchUser()
      return true
    } catch (error) {
      console.error('Login failed:', error)
      return false
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch (error) {
      console.error('Logout error:', error)
    } finally {
      user.value = null
      permissions.value.clear()
      clearCsrfToken()
      csrfManager.clearToken()
    }
  }

  async function loadPermissions() {
    if (!user.value) return

    try {
      // Response is already unwrapped by request.ts interceptor
      const response = await authApi.getPermissions()
      permissions.value = new Set(response || [])
    } catch (error) {
      console.error('Failed to load permissions:', error)
    }
  }

  async function fetchUser() {
    try {
      // Backend returns: { code: 0, data: { user: {...}, csrfToken: "..." } }
      // After request.ts unwrapping, we get: { user: {...}, csrfToken: "..." }
      const response = await authApi.getCurrentUser()

      user.value = response.user
      // Use csrfManager to handle CSRF token (survives page refresh)
      csrfManager.refreshFromResponse(response)
      await loadPermissions()
      return response.user
    } catch {
      // 401 is expected for unauthenticated users - no need to log
      user.value = null
      permissions.value.clear()
      return null
    }
  }

  async function initialize() {
    if (isInitialized.value) return

    // Always attempt to restore session from httpOnly cookies.
    // httpOnly cookies are NOT readable via document.cookie (browser security).
    // The server reads cookies from the request headers directly.
    // If no valid session exists, /auth/me returns 401 — handled gracefully.
    try {
      await fetchUser()
    } finally {
      isInitialized.value = true
    }
  }

  function clearUser() {
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
    initialize,
    clearUser,
    hasPermission,
    hasRole,
    hasAnyRole,
  }
})
