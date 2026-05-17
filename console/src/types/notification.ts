export type NotificationCategory =
  | "COMMUNICATION"
  | "MARKETING"
  | "SECURITY"
  | "SYSTEM"
  | "CONTEST";

export type NotificationType =
  | "COMMENT"
  | "REPLY"
  | "MENTION"
  | "UPVOTE"
  | "FOLLOW"
  | "SYSTEM"
  | "SUBMISSION"
  | "CONTEST"
  | "CONTEST_REMINDER";

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
  isRead?: boolean;
  category?: string;
  type?: string;
}

export interface NotificationPreferences {
  communication: boolean;
  marketing: boolean;
  security: boolean;
  system: boolean;
}
