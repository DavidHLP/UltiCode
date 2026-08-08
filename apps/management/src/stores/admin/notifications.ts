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
export const useNotificationsStore = defineStore('admin-notifications', () => {
  const announcements = ref<SystemAnnouncement[]>([])
  const total = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(10)
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function fetchAnnouncements(params?: AdminNotificationQueryParams) {
    isLoading.value = true
    error.value = null
    try {
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
      announcements.value = response.items
      total.value = response.total
      currentPage.value = response.page
      pageSize.value = response.pageSize
    } catch (e: unknown) {
      error.value = extractApiErrorMessage(e, 'Failed to fetch announcements')
      throw e
    } finally {
      isLoading.value = false
    }
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
