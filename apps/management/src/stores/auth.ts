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
import { apiGet } from '@/utils/request'

async function withProfileAvatar(user: User): Promise<User> {
  try {
    const profile = await apiGet<{ avatar?: string | null }>('/users/me', {
      skipErrorHandler: true,
    })
    return profile?.avatar ? { ...user, avatar: profile.avatar } : user
  } catch {
    return user
  }
}

/**
 * Management's boolean auth view over the shared session policy. The API
 * keeps its legacy login result and lazy ensureUser surface; transport and
 * lifecycle state are owned by auth-core.
 */
export const useAuthStore = defineStore('auth', () => {
  const session = createSessionAuthStore<User>({
    fetchCurrentUser: async () => {
      const response = await authApi.getCurrentUser()
      if (response?.user) response.user = await withProfileAvatar(response.user)
      return response
    },
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
      // Optional profile 401s may refresh the session after the login lock is released.
      const user = session.user.value
      if (user) {
        const enriched = await withProfileAvatar(user)
        if (session.user.value === user) session.user.value = enriched
      }
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
