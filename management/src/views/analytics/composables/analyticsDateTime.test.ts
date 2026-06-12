import { describe, expect, it } from 'vitest'
import { formatAnalyticsDate, formatAnalyticsTime } from './analyticsDateTime'

const date = new Date(2026, 5, 12, 18, 56)

describe('analytics date and time formatting', () => {
  it('formats the status clock in Chinese', () => {
    expect(formatAnalyticsDate(date, 'zh-CN')).toBe('6月12日周五')
    expect(formatAnalyticsTime(date, 'zh-CN')).toBe('18:56')
  })

  it('formats the status clock in English', () => {
    expect(formatAnalyticsDate(date, 'en-US')).toBe('Fri, Jun 12')
    expect(formatAnalyticsTime(date, 'en-US')).toBe('18:56')
  })
})
