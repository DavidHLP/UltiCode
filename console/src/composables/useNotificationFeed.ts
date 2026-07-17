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

  /**
   * Run an async operation with the standard error handling: capture a
   * human-readable message into `error.value` (falling back to the
   * caller-supplied label) and rethrow so callers can still observe
   * the failure.
   */
  async function withError<T>(
    fallbackMessage: string,
    fn: () => Promise<T>,
  ): Promise<T> {
    try {
      return await fn();
    } catch (err) {
      error.value = err instanceof Error ? err.message : fallbackMessage;
      throw err;
    }
  }

  async function loadNotifications(params: NotificationQuery = {}) {
    loading.value = true;
    error.value = null;
    try {
      return await withError("Failed to load notifications", async () => {
        const result: NotificationListResult = await fetchNotifications(params);
        notifications.value = result.items;
        total.value = result.total;
        page.value = result.page;
        limit.value = result.limit;
        totalPages.value = result.totalPages;
        return result;
      });
    } finally {
      loading.value = false;
    }
  }

  /**
   * Load unread notification count.
   * `isAuthenticated` gate is enforced by the caller (the store reads
   * `useAuthStore().isAuthenticated` and short-circuits when false).
   * Silently swallows errors — unread count is non-critical and the
   * `withError` helper would surface a message consumers do not act on.
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
    return withError("Failed to update notification", async () => {
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
    });
  }

  async function markAllRead() {
    error.value = null;
    return withError("Failed to mark all as read", async () => {
      const result = await markAllNotificationsRead();
      notifications.value = notifications.value.map((item) => ({
        ...item,
        isRead: true,
        readAt: new Date().toISOString(),
      }));
      unreadCount.value = 0;
      return result;
    });
  }

  async function clearAll() {
    error.value = null;
    return withError("Failed to clear notifications", async () => {
      const result = await clearNotifications();
      notifications.value = [];
      total.value = 0;
      totalPages.value = 1;
      unreadCount.value = 0;
      return result;
    });
  }

  async function removeNotification(id: string) {
    error.value = null;
    return withError("Failed to delete notification", async () => {
      const existing = notifications.value.find((item) => item.id === id);
      await apiDeleteNotification(id);
      notifications.value = notifications.value.filter(
        (item) => item.id !== id,
      );
      total.value = Math.max(0, total.value - 1);
      if (existing && !existing.isRead) {
        unreadCount.value = Math.max(0, unreadCount.value - 1);
      }
    });
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

  /**
   * Clear the local notification cache without a server call. Used by
   * presentation adapters when the user is unauthenticated, so they do not
   * mutate feed state directly (e.g. via `$patch`) — the feed owns mutations.
   */
  function resetLocalState() {
    notifications.value = [];
    total.value = 0;
    page.value = 1;
    totalPages.value = 1;
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
    resetLocalState,
  };
}

export type NotificationFeed = ReturnType<typeof useNotificationFeed>;
