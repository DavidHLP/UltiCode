import { defineStore } from 'pinia'
import { ref, readonly, type DeepReadonly } from 'vue'
import {
  usersApi,
  type User,
  type UserQueryParams,
  type CreateUserDto,
  type UpdateUserDto,
} from '@/api/admin/users'
import { extractApiErrorMessage } from '@/utils/error'
import { createCollectionSlice } from '@/stores/createCollectionSlice'

export const useUsersStore = defineStore('adminUsers', () => {
  const collection = createCollectionSlice<User, UserQueryParams>({
    load: async (params = {}, signal) => {
      const pageResult = await usersApi.getUsers(params, signal)
      return { items: pageResult.items, total: pageResult.total }
    },
  })
  const users = collection.items
  const readonlyUsers: DeepReadonly<typeof users> = readonly(users)
  const total: DeepReadonly<typeof collection.total> = readonly(collection.total)
  const loading: DeepReadonly<typeof collection.isLoading> = readonly(collection.isLoading)
  const error: DeepReadonly<typeof collection.error> = readonly(collection.error)
  const fetchUsers = collection.fetch
  const operationLoading = ref(false)
  const operationError = ref<string | null>(null)
  const currentUser = ref<User | null>(null)

  async function fetchUser(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      const user = await usersApi.getUser(id)
      currentUser.value = user
      return user
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to fetch user')
      console.error('Failed to fetch user:', err)
      return null
    } finally {
      operationLoading.value = false
    }
  }

  async function createUser(data: CreateUserDto) {
    operationLoading.value = true
    operationError.value = null
    try {
      const user = await usersApi.createUser(data)
      return user
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to create user')
      console.error('Failed to create user:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function updateUser(id: string, data: UpdateUserDto) {
    operationLoading.value = true
    operationError.value = null
    try {
      const user = await usersApi.updateUser(id, data)
      collection.updateItems((current) =>
        current.map((currentUser) => (currentUser.id === id ? user : currentUser)),
      )
      return user
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to update user')
      console.error('Failed to update user:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function banUser(id: string, reason: string, until?: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      const user = await usersApi.banUser(id, { reason, until })
      collection.updateItems((current) =>
        current.map((currentUser) => (currentUser.id === id ? user : currentUser)),
      )
      return user
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to ban user')
      console.error('Failed to ban user:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function unbanUser(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      const user = await usersApi.unbanUser(id)
      collection.updateItems((current) =>
        current.map((currentUser) => (currentUser.id === id ? user : currentUser)),
      )
      return user
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to unban user')
      console.error('Failed to unban user:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function bulkBan(ids: string[], reason?: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      await usersApi.bulkBan(ids, reason)
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to bulk ban users')
      console.error('Failed to bulk ban users:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function bulkUnban(ids: string[]) {
    operationLoading.value = true
    operationError.value = null
    try {
      await usersApi.bulkUnban(ids)
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to bulk unban users')
      console.error('Failed to bulk unban users:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function bulkDelete(ids: string[]) {
    operationLoading.value = true
    operationError.value = null
    try {
      await usersApi.bulkDelete(ids)
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to bulk delete users')
      console.error('Failed to bulk delete users:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function resetPassword(id: string, password: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      await usersApi.resetPassword(id, password)
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to reset password')
      console.error('Failed to reset password:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  function clearError() {
    collection.clearError()
    operationError.value = null
  }

  return {
    items: readonlyUsers,
    isLoading: loading,
    fetch: collection.fetch,
    users: readonlyUsers,
    total,
    loading,
    error,
    operationLoading,
    operationError,
    currentUser,
    fetchUsers,
    fetchUser,
    createUser,
    updateUser,
    banUser,
    unbanUser,
    bulkBan,
    bulkUnban,
    bulkDelete,
    resetPassword,
    clearError,
  }
})
