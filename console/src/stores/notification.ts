import { defineStore } from "pinia";
import { ref, watch } from "vue";
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
import { useAuthStore } from "./auth";
import {
  getSocketManager,
  NotificationEvent,
  type NotificationPayload,
} from "@/lib/socket";

export const useNotificationStore = defineStore("notification", () => {
  const notifications = ref<NotificationItem[]>([]);
  const total = ref(0);
  const page = ref(1);
  const limit = ref(10);
  const totalPages = ref(1);
  const unreadCount = ref(0);
  const loading = ref(false);
  const realtimeConnected = ref(false);
  const error = ref<string | null>(null);
  const isSetup = ref(false);

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
   * Load unread notification count
   * Only call this when user is authenticated
   */
  async function loadUnreadCount(): Promise<number> {
    const authStore = useAuthStore();

    // Guard: Don't make API call if not authenticated
    if (!authStore.isAuthenticated) {
      unreadCount.value = 0;
      return 0;
    }

    error.value = null;
    try {
      const result = await fetchUnreadCount();
      unreadCount.value = result.count;
      return result.count;
    } catch {
      // Silently handle - unread count is non-critical and shouldn't trigger logout
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

  // Real-time notification handlers
  function handleNewNotification(payload: NotificationPayload) {
    const newItem: NotificationItem = {
      id: payload.id,
      title: payload.title,
      body: payload.content,
      type: (payload.type?.toUpperCase() ?? "SYSTEM") as NotificationItem["type"],
      category: "SYSTEM",
      link: payload.link || null,
      isRead: false,
      readAt: null,
      createdAt: payload.createdAt,
    };
    notifications.value = [newItem, ...notifications.value];
    total.value += 1;
    unreadCount.value += 1;
  }

  /**
   * Setup WebSocket listeners
   * Safe to call multiple times - will only setup once
   */
  function setupRealtimeListeners(): void {
    // Prevent duplicate setup
    if (isSetup.value) {
      return;
    }

    const authStore = useAuthStore();
    const socketManager = getSocketManager();

    // Connection status tracking
    socketManager.on("connection:status", (status: string) => {
      realtimeConnected.value = status === "connected";
    });

    // Listen for notifications
    socketManager.on(
      NotificationEvent.SYSTEM_ANNOUNCEMENT,
      handleNewNotification,
    );

    // Watch for auth changes - this is the key integration point
    watch(
      () => authStore.isAuthenticated,
      (isAuth) => {
        if (isAuth) {
          socketManager.connect();
        } else {
          socketManager.disconnect();
          // Reset unread count when logged out
          unreadCount.value = 0;
        }
      },
      { immediate: true },
    );

    isSetup.value = true;
  }

  /**
   * Initialize notification store
   * Call this explicitly after auth is ready
   */
  async function initialize(): Promise<void> {
    const authStore = useAuthStore();

    // Setup WebSocket listeners
    setupRealtimeListeners();

    // Only load unread count if authenticated
    if (authStore.isAuthenticated) {
      await loadUnreadCount();
    }
  }

  function clearError() {
    error.value = null;
  }

  return {
    notifications,
    total,
    page,
    limit,
    totalPages,
    unreadCount,
    loading,
    realtimeConnected,
    error,
    setupRealtimeListeners,
    initialize,
    loadNotifications,
    loadUnreadCount,
    markAsRead,
    markAllRead,
    clearAll,
    removeNotification,
    clearError,
  };
});
