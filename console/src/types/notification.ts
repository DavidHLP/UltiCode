export type NotificationCategory =
  | "communication"
  | "marketing"
  | "security"
  | "system";

export type NotificationType =
  | "comment"
  | "reply"
  | "mention"
  | "upvote"
  | "follow"
  | "system"
  | "submission"
  | "contest";

export interface NotificationItem {
  id: string;
  title: string;
  body: string;
  type: NotificationType;
  category: NotificationCategory;
  link?: string | null;
  metadata?: Record<string, unknown> | null;
  isRead: boolean;
  readAt?: string | null;
  createdAt: string;
}

export interface NotificationListResult {
  items: NotificationItem[];
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}

export interface NotificationQuery {
  page?: number;
  limit?: number;
  unreadOnly?: boolean;
  category?: string;
  type?: string;
}

export interface NotificationPreferences {
  communication: boolean;
  marketing: boolean;
  security: boolean;
  system: boolean;
}
