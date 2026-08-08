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

function startOfUtcDay(timestamp: number): Date {
  return new Date(timestamp)
}

/**
 * Build a daily lookup of `value` keyed by UTC-day timestamp. Extra
 * fields on the input point are preserved so the chart can still read
 * series keys like `users` / `count` on each emitted row.
 */
function bucketByUtcDay<T extends DatedChartPoint>(data: T[]): Map<number, T> {
  const buckets = new Map<number, T>()
  for (const point of data) {
    const ts = toUtcDay(new Date(point.date))
    if (Number.isFinite(ts)) {
      buckets.set(ts, point)
    }
  }
  return buckets
}

/**
 * Expand a sparse daily series into a continuous daily series for the
 * requested window. Missing days are filled with a zero row so the chart
 * has a data point per day — without this, the x-axis tick algorithm
 * collapses onto the single populated day and produces overlapping
 * labels like "6月18日6月18日6月18日".
 *
 * The fill row is a shallow clone of an existing point when available
 * (preserves any extra metadata the chart expects), otherwise a minimal
 * `{ date }` object is created.
 */
function fillDailyGaps<T extends DatedChartPoint>(
  buckets: Map<number, T>,
  startTimestamp: number,
  endTimestamp: number,
): T[] {
  if (endTimestamp < startTimestamp) return []
  const result: T[] = []
  const anyPoint = buckets.values().next().value as T | undefined
  for (let ts = startTimestamp; ts <= endTimestamp; ts += DAY_IN_MS) {
    const existing = buckets.get(ts)
    if (existing) {
      result.push(existing)
    } else {
      const fill = { date: startOfUtcDay(ts) } as T
      if (anyPoint) {
        // Mirror the series keys so zero-rows are uniform with the rest.
        for (const key of Object.keys(anyPoint)) {
          if (key === 'date') continue
          ;(fill as Record<string, unknown>)[key] = 0
        }
      }
      result.push(fill)
    }
  }
  return result
}

export function filterChartDataByPeriod<T extends DatedChartPoint>(
  data: T[],
  period: TimePeriod,
): T[] {
  if (data.length === 0) return data

  const buckets = bucketByUtcDay(data)
  if (buckets.size === 0) return data

  const latestTimestamp = Math.max(...buckets.keys())
  const earliestTimestamp = Math.min(...buckets.keys())

  if (period === 'all') {
    return fillDailyGaps(buckets, earliestTimestamp, latestTimestamp)
  }

  const days = DAYS_BY_PERIOD[period]
  const startTimestamp = latestTimestamp - (days - 1) * DAY_IN_MS
  return fillDailyGaps(buckets, startTimestamp, latestTimestamp)
}

export function formatChartDateTick(date: Date | number, locale: string): string {
  return new Date(date).toLocaleDateString(locale, {
    month: 'short',
    day: 'numeric',
  })
}
