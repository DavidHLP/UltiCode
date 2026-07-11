import { defineStore } from "pinia";
import type {
  NotificationItem,
  NotificationQuery,
} from "@/types/notification";
import { useAuthStore } from "./auth";
import { useNotificationFeed } from "@/composables/useNotificationFeed";
import { useRealtimeChannel } from "@/composables/useRealtimeChannel";

/**
 * Notification Store
 *
 * State container + selectors. Server-state side effects live in
 * `useNotificationFeed`. Realtime side effects live in
 * `useRealtimeChannel`. The store composes them, keeps the same
 * external action signatures, and exposes the WebSocket lifecycle
 * reducer through `setupRealtimeListeners` and the realtime-connected
 * flag through `realtimeConnected`.
 */
export const useNotificationStore = defineStore("notification", () => {
  const feed = useNotificationFeed();
  const authStore = useAuthStore();

  const { realtimeConnected, setupRealtimeListeners: setupChannelListeners } =
    useRealtimeChannel({
      isAuthenticated: () => authStore.isAuthenticated,
      onItem: (item: NotificationItem) => {
        // Prepend the realtime event into the feed and bump counters
        // exactly as the legacy `handleNewNotification` reducer did.
        feed.notifications.value = [item, ...feed.notifications.value];
        feed.total.value += 1;
        feed.unreadCount.value += 1;
      },
      onSignedOut: () => {
        feed.unreadCount.value = 0;
      },
    });

  /**
   * Public, idempotent listener setup. Kept on the store so view code
   * and tests continue to call `store.setupRealtimeListeners()` with no
   * argument; the signed-out reset is now configured once on the
   * realtime channel at construction time.
   */
  function setupRealtimeListeners(): void {
    setupChannelListeners();
  }

  async function initialize(): Promise<void> {
    setupRealtimeListeners();
    if (authStore.isAuthenticated) {
      await feed.loadUnreadCount();
    }
  }

  async function loadNotifications(params: NotificationQuery = {}) {
    return feed.loadNotifications(params);
  }

  async function loadUnreadCount(): Promise<number> {
    if (!authStore.isAuthenticated) {
      feed.unreadCount.value = 0;
      return 0;
    }
    return feed.loadUnreadCount();
  }

  // Re-exports of feed mutators — identical signatures to the legacy
  // store so view code and tests keep working unchanged.
  const markAsRead = feed.markAsRead;
  const markAllRead = feed.markAllRead;
  const clearAll = feed.clearAll;
  const removeNotification = feed.removeNotification;
  const clearError = feed.clearError;

  return {
    // state
    notifications: feed.notifications,
    total: feed.total,
    page: feed.page,
    limit: feed.limit,
    totalPages: feed.totalPages,
    unreadCount: feed.unreadCount,
    loading: feed.loading,
    realtimeConnected,
    error: feed.error,
    // actions
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
