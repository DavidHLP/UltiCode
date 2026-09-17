import { defineStore } from 'pinia'
import {
  adminNotificationsApi,
  type CreateNotificationDto,
  type UpdateNotificationDto,
  type SystemAnnouncement,
  type AdminNotificationQueryParams,
} from '@/api/admin/notifications'
import { extractApiErrorMessage } from '@/utils/error'
import { createCollectionSlice } from '@/stores/createCollectionSlice'
export const useNotificationsStore = defineStore('admin-notifications', () => {
  const collection = createCollectionSlice<
    SystemAnnouncement,
    AdminNotificationQueryParams
  >({
    load: async (params = {}, signal) => {
      const queryParams: AdminNotificationQueryParams = {
        ...params,
        page: params.page ?? 1,
        limit: params.limit ?? 10,
      }
      const response = await adminNotificationsApi.getAll(queryParams, signal)
      return {
        items: response.items,
        total: response.total,
      }
    },
  })
  const announcements = collection.items
  const total = collection.total
  const isLoading = collection.isLoading
  const error = collection.error
  function fetchAnnouncements(params?: AdminNotificationQueryParams) {
    return collection.fetch(params, { rethrow: true })
  }

  async function createNotification(data: CreateNotificationDto) {
    isLoading.value = true
    error.value = null
    try {
      await adminNotificationsApi.create(data)
    } catch (e: unknown) {
      error.value = extractApiErrorMessage(e, 'Failed to create notification')
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function updateNotification(id: string, data: UpdateNotificationDto) {
    isLoading.value = true
    error.value = null
    try {
      await adminNotificationsApi.update(id, data)
    } catch (e: unknown) {
      error.value = extractApiErrorMessage(e, 'Failed to update notification')
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function deleteAnnouncement(id: string) {
    isLoading.value = true
    error.value = null
    try {
      await adminNotificationsApi.delete(id)
    } catch (e: unknown) {
      error.value = extractApiErrorMessage(e, 'Failed to delete announcement')
      throw e
    } finally {
      isLoading.value = false
    }
  }

  return {
    items: collection.items,
    fetch: collection.fetch,
    announcements,
    total,
    isLoading,
    error,
    fetchAnnouncements,
    createNotification,
    updateNotification,
    deleteAnnouncement,
  }
})
