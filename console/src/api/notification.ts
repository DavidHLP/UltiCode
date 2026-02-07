import { apiGet, apiPatch, apiPost, apiDelete } from "@/utils/request";
import type {
  NotificationItem,
  NotificationListResult,
  NotificationPreferences,
  NotificationQuery,
} from "@/types/notification";

function buildQuery(params?: NotificationQuery): string {
  if (!params) return "";
  const query = new URLSearchParams();
  if (params.page) query.set("page", String(params.page));
  if (params.limit) query.set("limit", String(params.limit));
  if (params.unreadOnly !== undefined) {
    query.set("unreadOnly", String(params.unreadOnly));
  }
  if (params.category) query.set("category", params.category);
  if (params.type) query.set("type", params.type);
  const value = query.toString();
  return value ? `?${value}` : "";
}

export async function fetchNotifications(
  params?: NotificationQuery,
): Promise<NotificationListResult> {
  return apiGet<NotificationListResult>(`/notifications${buildQuery(params)}`);
}

export async function fetchUnreadCount(): Promise<{ count: number }> {
  return apiGet<{ count: number }>("/notifications/unread-count");
}

export async function updateNotificationRead(
  id: string,
  isRead: boolean,
): Promise<NotificationItem> {
  return apiPatch<NotificationItem>(`/notifications/${id}`, { isRead });
}

export async function markAllNotificationsRead(): Promise<{ updated: number }> {
  return apiPost<{ updated: number }>("/notifications/mark-all-read");
}

export async function clearNotifications(): Promise<{ deleted: number }> {
  return apiDelete<{ deleted: number }>("/notifications/clear");
}

export async function deleteNotification(
  id: string,
): Promise<{ success: boolean }> {
  return apiDelete<{ success: boolean }>(`/notifications/${id}`);
}

export async function fetchNotificationPreferences(): Promise<NotificationPreferences> {
  return apiGet<NotificationPreferences>("/notifications/preferences");
}

export async function updateNotificationPreferences(
  payload: Partial<NotificationPreferences>,
): Promise<NotificationPreferences> {
  return apiPatch<NotificationPreferences>(
    "/notifications/preferences",
    payload,
  );
}
