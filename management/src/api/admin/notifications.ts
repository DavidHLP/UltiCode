import { apiGet, apiPost, apiDelete } from '@/utils/request'

export enum NotificationType {
  COMMENT = 'COMMENT',
  REPLY = 'REPLY',
  MENTION = 'MENTION',
  UPVOTE = 'UPVOTE',
  FOLLOW = 'FOLLOW',
  SYSTEM = 'SYSTEM',
  SUBMISSION = 'SUBMISSION',
  CONTEST = 'CONTEST',
}

export enum NotificationCategory {
  COMMUNICATION = 'COMMUNICATION',
  MARKETING = 'MARKETING',
  SECURITY = 'SECURITY',
  SYSTEM = 'SYSTEM',
}

export enum NotificationTarget {
  ALL = 'ALL',
  USERS = 'USERS',
}

export interface CreateNotificationDto {
  title: string
  content: string
  type: NotificationType
  category?: NotificationCategory
  target: NotificationTarget
  userIds?: string[]
}

export interface SystemAnnouncement {
  id: string
  title: string
  content: string
  type: NotificationType
  createdAt: string
  creator: {
    id: string
    username: string
    avatar: string | null
  }
}

export const adminNotifications = {
  create: (data: CreateNotificationDto) => {
    return apiPost<SystemAnnouncement>('/admin/notifications', data)
  },

  getAll: () => {
    return apiGet<SystemAnnouncement[]>('/admin/notifications')
  },

  delete: (id: string) => {
    return apiDelete<{ message: string }>(`/admin/notifications/${id}`)
  },
}
