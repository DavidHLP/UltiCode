import { apiGet } from '../client'

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

export interface DashboardStats {
  users: {
    total: number
    active: number
    activeToday: number
    activeWeek: number
    activeMonth: number
    banned: number
    byRole: Record<string, number>
  }
  problems: {
    total: number
    published: number
    unpublished: number
    byDifficulty: Record<string, number>
    byStatus: Record<string, number>
  }
  contests: {
    total: number
    upcoming: number
    running: number
    finished: number
  }
  submissions: {
    total: number
    today: number
    week: number
    month: number
    acceptanceRate: number
  }
  solutions: {
    total: number
    published: number
    flagged: number
  }
  forum: {
    posts: number
    comments: number
    communities: number
    flaggedPosts: number
    flaggedComments: number
  }
  system: {
    uptime: number
    version: string
  }
}

export interface ChartDataPoint {
  [key: string]: string | number
}

export interface ChartStatsResponse {
  metric: ChartMetric
  period: ChartPeriod
  data: ChartDataPoint[]
  startDate: Date
  endDate: Date
}

export interface ChartQueryParams {
  period?: ChartPeriod
  metric?: ChartMetric
  days?: number
  startDate?: string
  endDate?: string
}

export const dashboardApi = {
  async getStats(): Promise<DashboardStats> {
    const response = await apiGet<DashboardStats>('/admin/dashboard/stats')
    return response
  },

  async getChartStats(params: ChartQueryParams = {}): Promise<ChartStatsResponse> {
    const response = await apiGet<ChartStatsResponse>('/admin/dashboard/charts', {
      params,
    })
    return response
  },
}
