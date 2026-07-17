import { describe, it, expect } from 'vitest'
import {
  coerceDate,
  formatRelativeTime,
  formatTime24,
  formatWeekdayShortDate,
  formatPenaltyTime,
  getDurationMinutes,
  formatDate,
} from '../index'

describe('formatRelativeTime', () => {
  it('honors the provided locale instead of a hard-coded en-US fallback', () => {
    const twoHoursAgo = new Date(Date.now() - 2 * 60 * 60 * 1000)
    const en = formatRelativeTime(twoHoursAgo, 'en-US')
    const zh = formatRelativeTime(twoHoursAgo, 'zh-CN')
    // Regression for the shared package bug: locales must diverge.
    expect(en).not.toBe(zh)
    expect(en.toLowerCase()).toContain('hour')
  })

  it('returns a dash for null or invalid input', () => {
    expect(formatRelativeTime(null, 'en-US')).toBe('-')
    expect(formatRelativeTime('not-a-date', 'en-US')).toBe('-')
  })
})

describe('formatTime24', () => {
  it('renders a 24-hour clock without an AM/PM marker', () => {
    const value = formatTime24(new Date('2026-07-17T14:30:00Z'), 'en-US')
    expect(value).not.toMatch(/[AP]M/i)
    expect(value).toMatch(/\d{2}:\d{2}/)
  })

  it('returns a dash for null or invalid input', () => {
    expect(formatTime24(undefined, 'en-US')).toBe('-')
    expect(formatTime24('nope', 'en-US')).toBe('-')
  })
})

describe('formatWeekdayShortDate', () => {
  it('includes a weekday token for a valid date', () => {
    const value = formatWeekdayShortDate(new Date('2026-07-17T12:00:00Z'), 'en-US')
    expect(value).not.toBe('-')
    expect(value.length).toBeGreaterThan(0)
  })

  it('returns a dash for invalid input', () => {
    expect(formatWeekdayShortDate('', 'en-US')).toBe('-')
  })
})

describe('formatPenaltyTime', () => {
  it('formats hours as H:MM:SS and sub-hour as M:SS', () => {
    expect(formatPenaltyTime(3661)).toBe('1:01:01')
    expect(formatPenaltyTime(75)).toBe('1:15')
    expect(formatPenaltyTime(-10)).toBe('0:00')
  })
})

describe('getDurationMinutes', () => {
  it('returns whole minutes between two timestamps', () => {
    expect(
      getDurationMinutes('2026-07-17T10:00:00Z', '2026-07-17T11:30:00Z'),
    ).toBe(90)
  })

  it('returns 0 when either bound is missing or invalid', () => {
    expect(getDurationMinutes(undefined, '2026-07-17T11:30:00Z')).toBe(0)
    expect(getDurationMinutes('2026-07-17T10:00:00Z', null)).toBe(0)
    expect(getDurationMinutes('bad', 'worse')).toBe(0)
  })
})

describe('formatDate guards', () => {
  it('returns a dash for null or invalid input', () => {
    expect(formatDate(null, 'en-US')).toBe('-')
    expect(formatDate('invalid', 'en-US')).toBe('-')
  })
})

describe('coerceDate', () => {
  it('parses a string and passes a Date through untouched', () => {
    const fromString = coerceDate('2026-07-17T12:00:00Z')
    expect(fromString).toBeInstanceOf(Date)
    expect(fromString!.toISOString()).toBe('2026-07-17T12:00:00.000Z')

    const input = new Date('2026-07-17T12:00:00Z')
    expect(coerceDate(input)).toBe(input)
  })

  it('returns null for missing or invalid input', () => {
    expect(coerceDate(null)).toBeNull()
    expect(coerceDate(undefined)).toBeNull()
    expect(coerceDate('')).toBeNull()
    expect(coerceDate('not-a-date')).toBeNull()
  })
})
