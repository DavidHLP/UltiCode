import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  adminNotifications,
  type CreateNotificationDto,
  type SystemAnnouncement,
} from '@/api/admin/notifications'

export const useNotificationsStore = defineStore('admin-notifications', () => {
  const announcements = ref<SystemAnnouncement[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function fetchAnnouncements() {
    isLoading.value = true
    error.value = null
    try {
      const response = await adminNotifications.getAll()
      announcements.value = response.data
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      error.value = err.response?.data?.message || 'Failed to fetch announcements'
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function createNotification(data: CreateNotificationDto) {
    isLoading.value = true
    error.value = null
    try {
      await adminNotifications.create(data)
      await fetchAnnouncements()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      error.value = err.response?.data?.message || 'Failed to create notification'
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function deleteAnnouncement(id: string) {
    isLoading.value = true
    try {
      await adminNotifications.delete(id)
      announcements.value = announcements.value.filter((a) => a.id !== id)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      error.value = err.response?.data?.message || 'Failed to delete announcement'
      throw e
    } finally {
      isLoading.value = false
    }
  }

  return {
    announcements,
    isLoading,
    error,
    fetchAnnouncements,
    createNotification,
    deleteAnnouncement,
  }
})
