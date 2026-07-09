import type { PageResult } from "@/shared/domain-types/src"
import { apiGet, apiPost, apiPut, apiDelete } from '@/utils/request'

// ==================== Types ====================

export type NotificationType =
  | 'COMMENT'
  | 'REPLY'
  | 'MENTION'
  | 'UPVOTE'
  | 'FOLLOW'
  | 'SYSTEM'
  | 'SUBMISSION'
  | 'CONTEST'
  | 'CONTEST_REMINDER'

export type NotificationCategory = 'COMMUNICATION' | 'MARKETING' | 'SECURITY' | 'SYSTEM' | 'CONTEST'

export type NotificationTarget = 'ALL' | 'USERS'

export const NOTIFICATION_TYPES: NotificationType[] = [
  'COMMENT',
  'REPLY',
  'MENTION',
  'UPVOTE',
  'FOLLOW',
  'SYSTEM',
  'SUBMISSION',
  'CONTEST',
  'CONTEST_REMINDER',
]

export const NOTIFICATION_CATEGORIES: NotificationCategory[] = [
  'COMMUNICATION',
  'MARKETING',
  'SECURITY',
  'SYSTEM',
  'CONTEST',
]

export const NOTIFICATION_TARGETS: NotificationTarget[] = ['ALL', 'USERS']

export interface SystemAnnouncement {
  id: string
  announcementId?: string
  title: string
  content: string
  type: NotificationType
  category?: NotificationCategory
  createdAt: string
  creator?: {
    id: string
    username: string
    avatar?: string
  }
}

export interface CreateNotificationDto {
  title: string
  content: string
  type: NotificationType
  category?: NotificationCategory
  target: NotificationTarget
  userIds?: string[]
}

export interface UpdateNotificationDto {
  title: string
  content: string
  type?: NotificationType
  category?: NotificationCategory
}

export interface AdminNotificationQueryParams {
  page?: number
  limit?: number
  keyword?: string
  type?: string
  category?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}


// ==================== API ====================

export const adminNotificationsApi = {
  getAll: (params?: AdminNotificationQueryParams) =>
    apiGet<PageResult<SystemAnnouncement>>('/admin/notifications', { params }),

  create: (data: CreateNotificationDto) =>
    apiPost<SystemAnnouncement>('/admin/notifications', data),

  update: (id: string, data: UpdateNotificationDto) =>
    apiPut<SystemAnnouncement>(`/admin/notifications/${id}`, data),

  delete: (id: string) => apiDelete<void>(`/admin/notifications/${id}`),
}
