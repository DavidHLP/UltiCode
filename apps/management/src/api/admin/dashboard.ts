import { apiGet } from '@/utils/request'

/**
 * Backend Result wrapper - all API responses are wrapped in this structure
 */
export interface Result<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export enum ChartPeriod {
  HOUR = 'hour',
  DAY = 'day',
  WEEK = 'week',
  MONTH = 'month',
  YEAR = 'year',
}

export enum ChartMetric {
  USERS = 'users',
  SUBMISSIONS = 'submissions',
  PROBLEMS = 'problems',
  CONTESTS = 'contests',
  SOLUTIONS = 'solutions',
  FORUM_POSTS = 'forum_posts',
}

/**
 * Dashboard statistics response.
 * All numeric values use 'number' type for statistics.
 */
export interface DashboardStats {
  users: UserStats
  problems: ProblemStats
  contests: ContestStats
  submissions: SubmissionStats
  solutions: SolutionStats
  forum: ForumStats
  system: SystemStats
}

export interface UserStats {
  total: number // Long from backend
  active: number
  activeToday: number // Backend uses camelCase
  activeWeek: number
  activeMonth: number
  banned: number
  byRole: Record<string, number>
}

export interface ProblemStats {
  total: number
  published: number
  unpublished: number
  byDifficulty: Record<string, number>
  byStatus: Record<string, number>
}

export interface ContestStats {
  total: number
  upcoming: number
  running: number
  finished: number
}

export interface SubmissionStats {
  total: number
  today: number // Backend uses 'today'
  week: number // Backend uses 'week'
  month: number // Backend uses 'month'
  acceptanceRate: number
}

export interface SolutionStats {
  total: number
  published: number
  flagged: number
}

export interface ForumStats {
  posts: number
  comments: number
  communities: number
  flaggedPosts: number
  flaggedComments: number
}

export interface SystemStats {
  uptime: number // seconds, Long from backend
  version: string
}

/**
 * Single data point for chart statistics.
 * Backend returns { date: string, count: number } format.
 */
export interface ChartDataPoint {
  date: string // Format: "2026-03-25" (depends on period)
  count: number // Long from backend
}

/**
 * Chart statistics response.
 * startDate and endDate are ISO datetime strings from backend LocalDateTime.
 */
export interface ChartStatsResponse {
  metric: ChartMetric
  period: ChartPeriod
  data: ChartDataPoint[]
  startDate: string // ISO datetime string
  endDate: string // ISO datetime string
}

export interface ChartQueryParams {
  period?: ChartPeriod
  metric?: ChartMetric
  days?: number
  startDate?: string
  endDate?: string
}

export const dashboardApi = {
  // Note: request.ts intercepts and unwraps Result<T>, returning just T
  async getStats(): Promise<DashboardStats> {
    return apiGet<DashboardStats>('/admin/dashboard/stats')
  },

  async getChartStats(params: ChartQueryParams = {}): Promise<ChartStatsResponse> {
    return apiGet<ChartStatsResponse>('/admin/dashboard/charts', { params })
  },
}
