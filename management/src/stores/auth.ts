import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type LoginCredentials, type User } from '@/api/auth'
import { setCsrfToken, clearCsrfToken } from '@/utils/csrf'

// Key for storing auth state indicator in localStorage
// Since cookies are httpOnly, we need this to avoid unnecessary /auth/me calls
const AUTH_HAS_CREDENTIALS_KEY = 'auth_has_credentials'

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
      // Store CSRF token for subsequent state-changing requests
      if (loginResponse.csrf_token) {
        setCsrfToken(loginResponse.csrf_token)
      }
      // Mark that we have auth credentials (httpOnly cookies were set by backend)
      localStorage.setItem(AUTH_HAS_CREDENTIALS_KEY, 'true')
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
      // Clear auth credentials flag
      localStorage.removeItem(AUTH_HAS_CREDENTIALS_KEY)
    }
  }

  async function loadPermissions() {
    if (!user.value) return

    try {
      const perms = await authApi.getPermissions()
      permissions.value = new Set(perms)
    } catch (error) {
      console.error('Failed to load permissions:', error)
    }
  }

  async function fetchUser() {
    try {
      const userData = await authApi.getCurrentUser()
      user.value = userData
      // Store CSRF token if returned (handles page refresh case)
      if (userData.csrf_token) {
        setCsrfToken(userData.csrf_token)
      }
      await loadPermissions()
      return userData
    } catch {
      // 401 is expected for unauthenticated users - no need to log
      // Clear credentials flag since auth failed (cookie expired or invalid)
      localStorage.removeItem(AUTH_HAS_CREDENTIALS_KEY)
      user.value = null
      permissions.value.clear()
      return null
    }
  }

  async function initialize() {
    if (isInitialized.value) return

    isInitialized.value = true

    // Only attempt to fetch user if we have credentials flag set
    // This prevents unnecessary /auth/me calls that would trigger rate limiting
    const hasCredentials = localStorage.getItem(AUTH_HAS_CREDENTIALS_KEY) === 'true'
    if (hasCredentials) {
      await fetchUser()
    }
  }

  function hasPermission(action: string, resource: string): boolean {
    if (permissions.value.has('*:*')) return true
    if (permissions.value.has(`${action}:${resource}`)) return true
    if (permissions.value.has(`${action}:*`)) return true
    return false
  }

  function hasRole(role: string): boolean {
    return user.value?.role === role
  }

  function hasAnyRole(roles: string[]): boolean {
    return roles.includes(user.value?.role || '')
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
    hasPermission,
    hasRole,
    hasAnyRole,
  }
})
