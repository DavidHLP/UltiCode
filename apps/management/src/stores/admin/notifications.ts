import { defineStore } from 'pinia'
import { ref } from 'vue'
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
  const currentPage = ref(1)
  const pageSize = ref(10)
  const collection = createCollectionSlice<SystemAnnouncement, AdminNotificationQueryParams>({
    load: async (params = {}) => {
      const queryParams: AdminNotificationQueryParams = {
        page: params?.page ?? currentPage.value,
        limit: params?.limit ?? pageSize.value,
        keyword: params?.keyword,
        type: params?.type,
        category: params?.category,
        sortBy: params?.sortBy,
        sortOrder: params?.sortOrder,
      }
      const response = await adminNotificationsApi.getAll(queryParams)
      currentPage.value = response.page
      pageSize.value = response.pageSize
      return { items: response.items, total: response.total }
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
      await fetchAnnouncements()
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
      await fetchAnnouncements()
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
      await fetchAnnouncements()
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
    currentPage,
    pageSize,
    isLoading,
    error,
    fetchAnnouncements,
    createNotification,
    updateNotification,
    deleteAnnouncement,
  }
})
