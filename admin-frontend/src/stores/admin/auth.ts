import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type LoginCredentials, type User } from '@/api/auth'

export const useAuthStore = defineStore('adminAuth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('admin_token'))
  const permissions = ref<Set<string>>(new Set())

  const isAuthenticated = computed(() => !!token.value && !!user.value)
  const userRole = computed(() => user.value?.role)
  const userName = computed(() => user.value?.name || user.value?.username)

  async function login(credentials: LoginCredentials) {
    try {
      const response = await authApi.login(credentials)
      token.value = response.access_token
      // Login response returns partial user data, create a full User object
      user.value = {
        ...response.user,
        email: '',
        avatar: undefined,
        is_active: true,
        is_banned: false,
        joined_at: new Date().toISOString(),
      } as User

      localStorage.setItem('admin_token', response.access_token)
      localStorage.setItem('admin_user', JSON.stringify(user.value))

      await loadPermissions()
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
      token.value = null
      user.value = null
      permissions.value.clear()
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_user')
    }
  }

  async function loadPermissions() {
    // For now, set permissions based on role
    // In production, fetch from API
    if (!user.value) return

    const role = user.value.role
    const perms = new Set<string>()

    if (role === 'SUPER_ADMIN') {
      // All permissions
      perms.add('*:*')
    } else if (role === 'ADMIN') {
      // Most permissions except manage_permissions
      perms.add('READ:USER')
      perms.add('CREATE:USER')
      perms.add('UPDATE:USER')
      perms.add('DELETE:USER')
      perms.add('MODERATE:USER')
      perms.add('READ:PROBLEM')
      perms.add('CREATE:PROBLEM')
      perms.add('UPDATE:PROBLEM')
      perms.add('DELETE:PROBLEM')
      perms.add('PUBLISH:PROBLEM')
      perms.add('READ:CONTEST')
      perms.add('CREATE:CONTEST')
      perms.add('UPDATE:CONTEST')
      perms.add('DELETE:CONTEST')
      perms.add('READ:SOLUTION')
      perms.add('MODERATE:SOLUTION')
      perms.add('READ:FORUM_POST')
      perms.add('MODERATE:FORUM_POST')
      perms.add('READ:FORUM_COMMENT')
      perms.add('MODERATE:FORUM_COMMENT')
      perms.add('READ:SYSTEM')
    } else if (role === 'MODERATOR') {
      perms.add('READ:USER')
      perms.add('READ:PROBLEM')
      perms.add('READ:CONTEST')
      perms.add('READ:SOLUTION')
      perms.add('MODERATE:SOLUTION')
      perms.add('UPDATE:SOLUTION')
      perms.add('DELETE:SOLUTION')
      perms.add('READ:FORUM_POST')
      perms.add('MODERATE:FORUM_POST')
      perms.add('UPDATE:FORUM_POST')
      perms.add('DELETE:FORUM_POST')
      perms.add('READ:FORUM_COMMENT')
      perms.add('MODERATE:FORUM_COMMENT')
      perms.add('UPDATE:FORUM_COMMENT')
      perms.add('DELETE:FORUM_COMMENT')
      perms.add('READ:SYSTEM')
    }

    permissions.value = perms
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

  // Initialize from localStorage
  function initialize() {
    const savedToken = localStorage.getItem('admin_token')
    const savedUser = localStorage.getItem('admin_user')

    if (savedToken && savedUser) {
      token.value = savedToken
      try {
        user.value = JSON.parse(savedUser)
        loadPermissions()
      } catch (error) {
        console.error('Failed to parse saved user:', error)
        logout()
      }
    }
  }

  return {
    user,
    token,
    permissions,
    isAuthenticated,
    userRole,
    userName,
    login,
    logout,
    loadPermissions,
    hasPermission,
    hasRole,
    hasAnyRole,
    initialize,
  }
})
