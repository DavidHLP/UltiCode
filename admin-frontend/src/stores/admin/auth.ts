import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type LoginCredentials, type User } from '@/api/auth'

export const useAuthStore = defineStore('adminAuth', () => {
  const user = ref<User | null>(null)
  const permissions = ref<Set<string>>(new Set())
  const isInitialized = ref(false)

  const isAuthenticated = computed(() => !!user.value)
  const userRole = computed(() => user.value?.role)
  const userName = computed(() => user.value?.name || user.value?.username)

  async function login(credentials: LoginCredentials) {
    try {
      await authApi.login(credentials)
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
      await loadPermissions()
      return userData
    } catch (error) {
      console.error('Failed to fetch user:', error)
      user.value = null
      permissions.value.clear()
      return null
    }
  }

  async function initialize() {
    if (isInitialized.value) return

    isInitialized.value = true
    await fetchUser()
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
