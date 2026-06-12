import { describe, expect, it } from 'vitest'
import { filterChartDataByPeriod, type DatedChartPoint, type TimePeriod } from './areaChartData'

function createDailyData(days: number): Array<DatedChartPoint & { users: number }> {
  return Array.from({ length: days }, (_, index) => ({
    date: new Date(2026, 4, 4 + index),
    users: index + 1,
  }))
}

describe('AreaChart time period filtering', () => {
  it.each([
    ['7d', 7],
    ['30d', 30],
    ['90d', 40],
    ['all', 40],
  ] satisfies Array<[TimePeriod, number]>)(
    'shows the expected data when selecting %s',
    (period, expectedCount) => {
      const result = filterChartDataByPeriod(createDailyData(40), period)

      expect(result).toHaveLength(expectedCount)
      expect(result.at(-1)?.date).toEqual(new Date(2026, 5, 12))
    },
  )
})
