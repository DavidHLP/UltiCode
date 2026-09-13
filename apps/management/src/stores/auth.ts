import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  checkAnyRole,
  checkPermission,
  checkRole,
  createSessionAuthStore,
  csrfManager,
  hasCsrfCookie,
} from '@ulticode/auth-core'
import { authApi, type LoginCredentials, type User } from '@/api/auth'

/**
 * Management's boolean auth view over the shared session policy. The API
 * keeps its legacy login result and lazy ensureUser surface; transport and
 * lifecycle state are owned by auth-core.
 */
export const useAuthStore = defineStore('auth', () => {
  const session = createSessionAuthStore<User>({
    fetchCurrentUser: () => authApi.getCurrentUser(),
    login: async (credentials) => {
      return authApi.login(credentials as LoginCredentials)
    },
    logout: () => authApi.logout(),
    loadPermissions: () => authApi.getPermissions(),
    hasSessionCookie: hasCsrfCookie,
    refreshCsrf: (response) => csrfManager.refreshFromResponse(response),
    clearCsrf: () => csrfManager.clearToken(),
  })

  const isInitialized = ref(false)
  const isAuthenticated = computed(() => !!session.user.value)
  const userRole = computed(() => session.user.value?.role)
  const userName = computed(() => session.user.value?.name || session.user.value?.username)

  async function fetchUser(): Promise<User | null> {
    const user = await session.fetchUser()
    if (user) {
      await session.loadPermissions()
    } else {
      session.permissions.value.clear()
    }
    return user
  }

  async function ensureUser(): Promise<User | null> {
    if (session.user.value) return session.user.value
    return fetchUser()
  }

  async function login(credentials: LoginCredentials): Promise<boolean> {
    try {
      await session.login(credentials)
      await session.loadPermissions()
      return true
    } catch (error) {
      console.error('Login failed:', error)
      return false
    }
  }

  async function initialize(): Promise<void> {
    if (isInitialized.value) return
    try {
      await session.initialize()
      if (session.user.value) await session.loadPermissions()
    } finally {
      isInitialized.value = true
    }
  }

  function clearUser(): void {
    session.clearUser()
  }

  function hasPermission(action: string, resource: string): boolean {
    return checkPermission(session.permissions.value, action, resource)
  }

  function hasRole(role: string): boolean {
    return checkRole(session.user.value?.role, role)
  }

  function hasAnyRole(roles: string[]): boolean {
    return checkAnyRole(session.user.value?.role, roles)
  }

  return {
    user: session.user,
    permissions: session.permissions,
    isInitialized,
    isAuthenticated,
    userRole,
    userName,
    login,
    logout: session.logout,
    loadPermissions: session.loadPermissions,
    fetchUser,
    ensureUser,
    initialize,
    clearUser,
    hasPermission,
    hasRole,
    hasAnyRole,
  }
})

export type { LoginCredentials, User }
