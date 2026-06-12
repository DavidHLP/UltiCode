import { describe, expect, it } from 'vitest'
import { NOTIFICATION_CATEGORIES, NOTIFICATION_TYPES } from '@/api/admin/notifications'
import { getNotificationCategoryLabel, getNotificationTypeLabel } from './notificationLabels'

const translations: Record<string, string> = {
  'notifications.types.COMMENT': '评论',
  'notifications.types.REPLY': '回复',
  'notifications.types.MENTION': '提及',
  'notifications.types.UPVOTE': '点赞',
  'notifications.types.FOLLOW': '关注',
  'notifications.types.SYSTEM': '系统',
  'notifications.types.SUBMISSION': '提交',
  'notifications.types.CONTEST': '比赛',
  'notifications.types.CONTEST_REMINDER': '比赛提醒',
  'notifications.categories.COMMUNICATION': '通讯',
  'notifications.categories.MARKETING': '营销',
  'notifications.categories.SECURITY': '安全',
  'notifications.categories.SYSTEM': '系统',
  'notifications.categories.CONTEST': '比赛',
}

const translate = (key: string) => translations[key] ?? key

describe('notification labels', () => {
  it('localizes every notification type', () => {
    expect(NOTIFICATION_TYPES.map((type) => getNotificationTypeLabel(type, translate))).toEqual([
      '评论',
      '回复',
      '提及',
      '点赞',
      '关注',
      '系统',
      '提交',
      '比赛',
      '比赛提醒',
    ])
  })

  it('localizes every notification category', () => {
    expect(
      NOTIFICATION_CATEGORIES.map((category) => getNotificationCategoryLabel(category, translate)),
    ).toEqual(['通讯', '营销', '安全', '系统', '比赛'])
  })
})
