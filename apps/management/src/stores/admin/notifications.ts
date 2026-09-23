import { defineStore } from 'pinia'
import { computed } from 'vue'
import {
  adminNotificationsApi,
  type CreateNotificationDto,
  type UpdateNotificationDto,
  type SystemAnnouncement,
  type AdminNotificationQueryParams,
} from '@/api/admin/notifications'
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
  const mutationLoading = collection.mutationLoading
  const isLoading = computed(() => collection.isLoading.value || mutationLoading.value)
  const error = computed(() => collection.error.value || collection.mutationError.value)
  function fetchAnnouncements(params?: AdminNotificationQueryParams) {
    return collection.fetch(params, { rethrow: true })
  }

  async function createNotification(data: CreateNotificationDto) {
    return collection.runMutation(async () => {
      await adminNotificationsApi.create(data)
    }, 'Failed to create notification')
  }

  async function updateNotification(id: string, data: UpdateNotificationDto) {
    return collection.runMutation(async () => {
      await adminNotificationsApi.update(id, data)
    }, 'Failed to update notification')
  }

  async function deleteAnnouncement(id: string) {
    return collection.runMutation(async () => {
      await adminNotificationsApi.delete(id)
    }, 'Failed to delete announcement')
  }

  return {
    items: collection.items,
    fetch: collection.fetch,
    cancel: collection.cancel,
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
