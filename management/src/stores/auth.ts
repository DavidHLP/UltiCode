import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type LoginCredentials, type User } from '@/api/auth'
import { setCsrfToken, clearCsrfToken } from '@/utils/csrf'

// API response wrapper type
interface ApiResponse<T> {
  code: number
  data: T
  message: string
  traceId?: string
}

/**
 * Check if auth cookies exist (httpOnly, so we check for presence only).
 * Returns true only if an access_token cookie is present.
 */
function hasAuthCookie(): boolean {
  return document.cookie
    .split(";")
    .some((c) => c.trim().startsWith("access_token="));
}

type SessionExpiredCallback = () => void

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const permissions = ref<Set<string>>(new Set())
  const isInitialized = ref(false)
  let _sessionExpiredCallback: SessionExpiredCallback | null = null

  const isAuthenticated = computed(() => !!user.value)
  const userRole = computed(() => user.value?.role)
  const userName = computed(() => user.value?.name || user.value?.username)

  async function login(credentials: LoginCredentials) {
    try {
      const loginResponse = await authApi.login(credentials)
      // Store CSRF token for subsequent state-changing requests
      if (loginResponse.csrfToken) {
        setCsrfToken(loginResponse.csrfToken)
      }
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
    }
  }

  async function loadPermissions() {
    if (!user.value) return

    try {
      const response = await authApi.getPermissions()
      // Response is wrapped in {code, data, message, traceId} structure
      const perms = Array.isArray(response) ? response : (response as ApiResponse<string[]>).data
      permissions.value = new Set(perms || [])
    } catch (error) {
      console.error('Failed to load permissions:', error)
    }
  }

  async function fetchUser() {
    try {
      // Backend returns: { code: 0, data: { user: {...}, csrfToken: "..." } }
      // After request.ts unwrapping, we get: { user: {...}, csrfToken: "..." }
      const response = await authApi.getCurrentUser()

      // Debug: Log the response to see what we're getting
      if (import.meta.env.DEV) {
        console.log('[Auth] fetchUser response:', response)
        console.log('[Auth] response.user:', response.user)
        console.log('[Auth] response.user.role:', response.user?.role)
      }

      user.value = response.user
      // Store CSRF token if returned (handles page refresh case)
      if (response.csrfToken) {
        setCsrfToken(response.csrfToken)
      }
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

    isInitialized.value = true

    // Always attempt to fetch user - hasAuthCookie() checks cookie presence
    // If no cookie, /auth/me will return 401 and we handle it gracefully
    if (hasAuthCookie()) {
      await fetchUser()
    }
  }

  function setupSessionExpiredCallback(callback: SessionExpiredCallback) {
    _sessionExpiredCallback = callback
  }

  function clearUser() {
    user.value = null
    permissions.value.clear()
    clearCsrfToken()
    if (_sessionExpiredCallback) {
      _sessionExpiredCallback()
    }
  }

  function hasPermission(action: string, resource: string): boolean {
    if (permissions.value.has('*:*')) return true
    if (permissions.value.has(`${action}:${resource}`)) return true
    if (permissions.value.has(`${action}:*`)) return true
    return false
  }

  function hasRole(role: string): boolean {
    const userRole = user.value?.role?.toUpperCase()
    const requiredRole = role.toUpperCase()
    const hasRoleMatch = userRole === requiredRole

    // Debug logging (dev only)
    if (import.meta.env.DEV) {
      console.log('[Auth] hasRole check:', {
        userRole: user.value?.role,
        normalizedUserRole: userRole,
        requiredRole: role,
        normalizedRequiredRole: requiredRole,
        hasRoleMatch,
      })
    }

    return hasRoleMatch
  }

  function hasAnyRole(roles: string[]): boolean {
    const userRole = user.value?.role?.toUpperCase()
    if (!userRole) {
      if (import.meta.env.DEV) {
        console.log('[Auth] hasAnyRole check: No user role found')
      }
      return false
    }

    const normalizedRoles = roles.map((r) => r.toUpperCase())
    const hasRole = normalizedRoles.includes(userRole)

    // Debug logging (dev only)
    if (import.meta.env.DEV) {
      console.log('[Auth] hasAnyRole check:', {
        userRole: user.value?.role,
        normalizedUserRole: userRole,
        requiredRoles: roles,
        normalizedRequiredRoles: normalizedRoles,
        hasRole,
      })
    }

    return hasRole
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
    setupSessionExpiredCallback,
    clearUser,
    hasPermission,
    hasRole,
    hasAnyRole,
  }
})
