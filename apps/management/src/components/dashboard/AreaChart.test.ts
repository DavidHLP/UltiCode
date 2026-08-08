import { describe, expect, it } from 'vitest'
import {
  filterChartDataByPeriod,
  formatChartDateTick,
  type DatedChartPoint,
  type TimePeriod,
} from './areaChartData'

/**
 * Build a continuous daily series anchored at UTC midnight so the
 * production `toUtcDay` bucketing is stable across CI timezones.
 *
 * `Date.UTC` is 0-indexed for the month argument: month 5 = June.
 */
function createDailyData(
  days: number,
  anchor = new Date(Date.UTC(2026, 5, 4)),
): Array<DatedChartPoint & { users: number }> {
  const day = 24 * 60 * 60 * 1000
  return Array.from({ length: days }, (_, index) => ({
    date: new Date(anchor.getTime() + index * day),
    users: index + 1,
  }))
}

describe('AreaChart time period filtering', () => {
  it.each([
    ['7d', 7],
    ['30d', 30],
    ['90d', 90],
    ['all', 40],
  ] satisfies Array<[TimePeriod, number]>)(
    'shows the expected data when selecting %s',
    (period, expectedCount) => {
      const result = filterChartDataByPeriod(createDailyData(40), period)

      expect(result).toHaveLength(expectedCount)
      // Last day should equal the anchor + 39 days (UTC midnight).
      const expectedLast = new Date(Date.UTC(2026, 5, 4 + 39))
      expect(result.at(-1)?.date.getTime()).toBe(expectedLast.getTime())
    },
  )

  it('fills missing days with zero-valued rows so the line stays continuous', () => {
    // Sparse series with 3 active days; the 7-day window ending on the
    // latest active day (Jun 18) covers Jun 12..18.
    const sparse: Array<DatedChartPoint & { users: number }> = [
      { date: new Date(Date.UTC(2026, 5, 10)), users: 2 },
      { date: new Date(Date.UTC(2026, 5, 12)), users: 5 },
      { date: new Date(Date.UTC(2026, 5, 18)), users: 1 },
    ]

    const result = filterChartDataByPeriod(sparse, '7d')

    // 7-day window ending on the latest populated day (Jun 18) →
    // Jun 12..18, all 7 days must be present.
    expect(result).toHaveLength(7)
    expect(result[0]?.date.toISOString()).toBe('2026-06-12T00:00:00.000Z')
    expect(result.at(-1)?.date.toISOString()).toBe('2026-06-18T00:00:00.000Z')
    // Jun 12 is a real data point → keeps its value.
    expect(result[0]?.users).toBe(5)
    // Jun 13..17 are filled rows → zero values.
    expect(result[1]?.users).toBe(0)
    expect(result[2]?.users).toBe(0)
    expect(result[3]?.users).toBe(0)
    expect(result[4]?.users).toBe(0)
    expect(result[5]?.users).toBe(0)
    // Jun 18 is a real data point → keeps its value.
    expect(result[6]?.users).toBe(1)
  })

  it('returns continuous daily series for the all-time period', () => {
    // Sparse data spanning 8 days
    const sparse: Array<DatedChartPoint & { users: number }> = [
      { date: new Date(Date.UTC(2026, 5, 1)), users: 3 },
      { date: new Date(Date.UTC(2026, 5, 4)), users: 7 },
      { date: new Date(Date.UTC(2026, 5, 8)), users: 2 },
    ]

    const result = filterChartDataByPeriod(sparse, 'all')

    expect(result).toHaveLength(8) // Jun 1..8 inclusive
    expect(result[0]?.date.toISOString()).toBe('2026-06-01T00:00:00.000Z')
    expect(result.at(-1)?.date.toISOString()).toBe('2026-06-08T00:00:00.000Z')
    // Gaps are zero-filled.
    expect(result[1]?.users).toBe(0)
    expect(result[2]?.users).toBe(0)
    expect(result[3]?.users).toBe(7)
  })

  it('returns an empty array when the data is empty', () => {
    expect(filterChartDataByPeriod([], '7d')).toEqual([])
  })
})

describe('AreaChart date localization', () => {
  const date = new Date(2026, 4, 31)

  it('formats chart dates in Chinese', () => {
    expect(formatChartDateTick(date, 'zh-CN')).toBe('5月31日')
  })

  it('formats chart dates in English', () => {
    expect(formatChartDateTick(date, 'en-US')).toBe('May 31')
  })
})
