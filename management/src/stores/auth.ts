import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type LoginCredentials, type User } from '@/api/auth'
import { csrfManager, clearCsrfToken } from '@/utils/csrf'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const permissions = ref<Set<string>>(new Set())
  const isInitialized = ref(false)

  const isAuthenticated = computed(() => !!user.value)
  const userRole = computed(() => user.value?.role)
  const userName = computed(() => user.value?.name || user.value?.username)

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
    if (permissions.value.has('*:*')) return true
    if (permissions.value.has(`${action}:${resource}`)) return true
    if (permissions.value.has(`${action}:*`)) return true
    if (permissions.value.has(`*:${resource}`)) return true
    return false
  }

  function hasRole(role: string): boolean {
    const userRole = user.value?.role?.toUpperCase()
    const requiredRole = role.toUpperCase()
    return userRole === requiredRole
  }

  function hasAnyRole(roles: string[]): boolean {
    const userRole = user.value?.role?.toUpperCase()
    if (!userRole) {
      return false
    }

    const normalizedRoles = roles.map((r) => r.toUpperCase())
    return normalizedRoles.includes(userRole)
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
