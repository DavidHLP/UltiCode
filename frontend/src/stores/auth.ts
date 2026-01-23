import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiGet } from '@/utils/request'
import type { User } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const isLoading = ref(false)
  const isInitialized = ref(false)

  const isAuthenticated = computed(() => !!user.value)
  const userRole = computed(() => user.value?.role || '')
  const userName = computed(() => user.value?.name || user.value?.username || '')
  const userId = computed(() => user.value?.id || '')

  async function fetchUser(): Promise<User | null> {
    try {
      isLoading.value = true
      const userData = await apiGet<User>('/auth/me')
      user.value = userData
      return userData
    } catch (error) {
      console.error('Failed to fetch user:', error)
      user.value = null
      return null
    } finally {
      isLoading.value = false
    }
  }

  async function initialize(): Promise<void> {
    if (isInitialized.value) return

    isInitialized.value = true
    await fetchUser()
  }

  function clearUser(): void {
    user.value = null
  }

  return {
    user,
    isLoading,
    isInitialized,
    isAuthenticated,
    userRole,
    userName,
    userId,
    fetchUser,
    initialize,
    clearUser,
  }
})
