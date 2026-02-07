import { defineStore } from "pinia";
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

export const useNotificationStore = defineStore("notification", () => {
  const notifications = ref<NotificationItem[]>([]);
  const total = ref(0);
  const page = ref(1);
  const limit = ref(10);
  const totalPages = ref(1);
  const unreadCount = ref(0);
  const loading = ref(false);

  async function loadNotifications(params: NotificationQuery = {}) {
    loading.value = true;
    try {
      const result: NotificationListResult = await fetchNotifications(params);
      notifications.value = result.items;
      total.value = result.total;
      page.value = result.page;
      limit.value = result.limit;
      totalPages.value = result.totalPages;
      return result;
    } finally {
      loading.value = false;
    }
  }

  async function loadUnreadCount() {
    const result = await fetchUnreadCount();
    unreadCount.value = result.count;
    return result.count;
  }

  async function markAsRead(id: string, isRead: boolean = true) {
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
  }

  async function markAllRead() {
    const result = await markAllNotificationsRead();
    notifications.value = notifications.value.map((item) => ({
      ...item,
      isRead: true,
      readAt: new Date().toISOString(),
    }));
    unreadCount.value = 0;
    return result;
  }

  async function clearAll() {
    const result = await clearNotifications();
    notifications.value = [];
    total.value = 0;
    totalPages.value = 1;
    unreadCount.value = 0;
    return result;
  }

  async function removeNotification(id: string) {
    const existing = notifications.value.find((item) => item.id === id);
    await apiDeleteNotification(id);
    notifications.value = notifications.value.filter((item) => item.id !== id);
    total.value = Math.max(0, total.value - 1);
    if (existing && !existing.isRead) {
      unreadCount.value = Math.max(0, unreadCount.value - 1);
    }
  }

  return {
    notifications,
    total,
    page,
    limit,
    totalPages,
    unreadCount,
    loading,
    loadNotifications,
    loadUnreadCount,
    markAsRead,
    markAllRead,
    clearAll,
    removeNotification,
  };
});
