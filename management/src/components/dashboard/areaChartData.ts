export type TimePeriod = '7d' | '30d' | '90d' | 'all'

export interface DatedChartPoint {
  date: Date
}

const DAYS_BY_PERIOD: Record<Exclude<TimePeriod, 'all'>, number> = {
  '7d': 7,
  '30d': 30,
  '90d': 90,
}

const DAY_IN_MS = 24 * 60 * 60 * 1000

function toUtcDay(date: Date): number {
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())
}

export function filterChartDataByPeriod<T extends DatedChartPoint>(
  data: T[],
  period: TimePeriod,
): T[] {
  if (period === 'all' || data.length === 0) return data

  const datedPoints = data
    .map((point) => ({ point, timestamp: toUtcDay(new Date(point.date)) }))
    .filter(({ timestamp }) => Number.isFinite(timestamp))

  const latestDate = Math.max(...datedPoints.map(({ timestamp }) => timestamp))
  const startDate = latestDate - (DAYS_BY_PERIOD[period] - 1) * DAY_IN_MS

  return datedPoints
    .filter(({ timestamp }) => timestamp >= startDate && timestamp <= latestDate)
    .map(({ point }) => point)
}
