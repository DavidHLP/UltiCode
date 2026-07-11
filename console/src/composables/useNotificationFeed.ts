import { ref } from "vue";
import type {
  NotificationItem,
  NotificationListResult,
  NotificationQuery,
} from "@/types/notification";
import {
  fetchNotifications,
  fetchUnreadCount,
  updateNotificationRead,
  markAllNotificationsRead,
  clearNotifications,
  deleteNotification as apiDeleteNotification,
} from "@/api/notification";

/**
 * Notification feed composable.
 *
 * Owns server-state + pagination side effects for the notification list:
 * load, unread-count, mark-read, mark-all-read, clear-all, remove. Holds
 * only the reactive state that backs these flows. Realtime-push wiring
 * lives in `useRealtimeChannel`.
 */
export function useNotificationFeed() {
  const notifications = ref<NotificationItem[]>([]);
  const total = ref(0);
  const page = ref(1);
  const limit = ref(10);
  const totalPages = ref(1);
  const unreadCount = ref(0);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function loadNotifications(params: NotificationQuery = {}) {
    loading.value = true;
    error.value = null;
    try {
      const result: NotificationListResult = await fetchNotifications(params);
      notifications.value = result.items;
      total.value = result.total;
      page.value = result.page;
      limit.value = result.limit;
      totalPages.value = result.totalPages;
      return result;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to load notifications";
      throw err;
    } finally {
      loading.value = false;
    }
  }

  /**
   * Load unread notification count.
   * `isAuthenticated` gate is enforced by the caller (the store reads
   * `useAuthStore().isAuthenticated` and short-circuits when false).
   */
  async function loadUnreadCount(): Promise<number> {
    error.value = null;
    try {
      const result = await fetchUnreadCount();
      unreadCount.value = result.count;
      return result.count;
    } catch {
      // Silently handle - unread count is non-critical
      return 0;
    }
  }

  async function markAsRead(id: string, isRead: boolean = true) {
    error.value = null;
    try {
      const updated = await updateNotificationRead(id, isRead);
      const index = notifications.value.findIndex((item) => item.id === id);
      if (index !== -1) {
        notifications.value[index] = updated;
      }
      if (isRead) {
        unreadCount.value = Math.max(0, unreadCount.value - 1);
      } else {
        unreadCount.value += 1;
      }
      return updated;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to update notification";
      throw err;
    }
  }

  async function markAllRead() {
    error.value = null;
    try {
      const result = await markAllNotificationsRead();
      notifications.value = notifications.value.map((item) => ({
        ...item,
        isRead: true,
        readAt: new Date().toISOString(),
      }));
      unreadCount.value = 0;
      return result;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to mark all as read";
      throw err;
    }
  }

  async function clearAll() {
    error.value = null;
    try {
      const result = await clearNotifications();
      notifications.value = [];
      total.value = 0;
      totalPages.value = 1;
      unreadCount.value = 0;
      return result;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to clear notifications";
      throw err;
    }
  }

  async function removeNotification(id: string) {
    error.value = null;
    try {
      const existing = notifications.value.find((item) => item.id === id);
      await apiDeleteNotification(id);
      notifications.value = notifications.value.filter(
        (item) => item.id !== id,
      );
      total.value = Math.max(0, total.value - 1);
      if (existing && !existing.isRead) {
        unreadCount.value = Math.max(0, unreadCount.value - 1);
      }
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to delete notification";
      throw err;
    }
  }

  function clearError() {
    error.value = null;
  }

  /**
   * Reset the unread count to zero (called on logout by the store).
   */
  function resetUnreadCount() {
    unreadCount.value = 0;
  }

  return {
    // state
    notifications,
    total,
    page,
    limit,
    totalPages,
    unreadCount,
    loading,
    error,
    // actions
    loadNotifications,
    loadUnreadCount,
    markAsRead,
    markAllRead,
    clearAll,
    removeNotification,
    clearError,
    resetUnreadCount,
  };
}

export type NotificationFeed = ReturnType<typeof useNotificationFeed>;
