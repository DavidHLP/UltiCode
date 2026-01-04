import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  usersApi,
  type User,
  type UserQueryParams,
  type CreateUserDto,
  type UpdateUserDto,
} from '@/api/admin/users'

export const useUsersStore = defineStore('adminUsers', () => {
  const users = ref<User[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const currentUser = ref<User | null>(null)

  async function fetchUsers(params: UserQueryParams = {}) {
    loading.value = true
    error.value = null
    try {
      const response = await usersApi.getUsers(params)
      users.value = response.data
      total.value = response.total
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to fetch users'
      console.error('Failed to fetch users:', err)
    } finally {
      loading.value = false
    }
  }

  async function fetchUser(id: string) {
    loading.value = true
    error.value = null
    try {
      const user = await usersApi.getUser(id)
      currentUser.value = user
      return user
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to fetch user'
      console.error('Failed to fetch user:', err)
      return null
    } finally {
      loading.value = false
    }
  }

  async function createUser(data: CreateUserDto) {
    loading.value = true
    error.value = null
    try {
      const user = await usersApi.createUser(data)
      return user
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to create user'
      console.error('Failed to create user:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function updateUser(id: string, data: UpdateUserDto) {
    loading.value = true
    error.value = null
    try {
      const user = await usersApi.updateUser(id, data)
      // Update local list if present
      const index = users.value.findIndex((u) => u.id === id)
      if (index !== -1) {
        users.value[index] = user
      }
      return user
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to update user'
      console.error('Failed to update user:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function banUser(id: string, reason: string, until?: string) {
    loading.value = true
    error.value = null
    try {
      const user = await usersApi.banUser(id, { reason, until })
      const index = users.value.findIndex((u) => u.id === id)
      if (index !== -1) {
        users.value[index] = user
      }
      return user
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to ban user'
      console.error('Failed to ban user:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function unbanUser(id: string) {
    loading.value = true
    error.value = null
    try {
      const user = await usersApi.unbanUser(id)
      const index = users.value.findIndex((u) => u.id === id)
      if (index !== -1) {
        users.value[index] = user
      }
      return user
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to unban user'
      console.error('Failed to unban user:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  function clearError() {
    error.value = null
  }

  return {
    users,
    total,
    loading,
    error,
    currentUser,
    fetchUsers,
    fetchUser,
    createUser,
    updateUser,
    banUser,
    unbanUser,
    clearError,
  }
})
