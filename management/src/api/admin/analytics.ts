import { apiGet } from '@/utils/request'

export enum AnalyticsReportType {
  USER_ACTIVITY = 'user_activity',
  PROBLEM_COMPLETION = 'problem_completion',
  CONTEST_PARTICIPATION = 'contest_participation',
  REVENUE = 'revenue',
  PERFORMANCE = 'performance',
}

export enum AnalyticsPeriod {
  DAY = 'day',
  WEEK = 'week',
  MONTH = 'month',
  QUARTER = 'quarter',
  YEAR = 'year',
}

export interface AnalyticsQueryParams {
  reportType: AnalyticsReportType
  period?: AnalyticsPeriod
  days?: number
  startDate?: string
  endDate?: string
}

export interface UserActivityReport {
  activeUsersDaily: Array<{ date: string; count: number }>
  activeUsersWeekly: Array<{ week: string; count: number }>
  averageSessionDuration: number
  peakActiveHours: Array<{ hour: number; count: number }>
  userRetention: {
    day1: number
    day7: number
    day30: number
  }
  topActiveUsers: Array<{
    userId: string
    username: string
    loginCount: number
    lastActive: string
  }>
}

export interface ProblemCompletionReport {
  totalAttempts: number
  successfulAttempts: number
  overallCompletionRate: number
  byDifficulty: Array<{
    difficulty: string
    total: number
    completed: number
    rate: number
  }>
  byTag: Array<{
    tagId: string
    label: string
    total: number
    completed: number
    rate: number
  }>
  trendingProblems: Array<{
    problemId: string
    title: string
    attempts: number
    completionRate: number
  }>
  hardestProblems: Array<{
    problemId: string
    title: string
    difficulty: string
    completionRate: number
  }>
}

export interface ContestParticipationReport {
  totalContests: number
  totalParticipants: number
  averageParticipantsPerContest: number
  participationTrend: Array<{
    date: string
    contests: number
    participants: number
  }>
  byType: Array<{
    type: string
    count: number
    avgParticipants: number
  }>
  topContests: Array<{
    contestId: string
    title: string
    participants: number
    completionRate: number
  }>
  virtualParticipation: {
    total: number
    averageCompletionRate: number
  }
}

export interface RevenueReport {
  totalRevenue: number
  mrr: number
  arr: number
  arpu: number
  subscriberCount: number
  churnRate: number
  revenueTrend: Array<{
    date: string
    revenue: number
    newSubscribers: number
    churned: number
  }>
  byPlan: Array<{
    plan: string
    subscribers: number
    revenue: number
  }>
  conversionRate: number
}

export interface PerformanceReport {
  systemUptime: number
  averageResponseTime: number
  errorRate: number
  throughput: number
  resourceUsage: {
    cpu: number
    memory: number
    disk: number
  }
  slowestEndpoints: Array<{
    endpoint: string
    averageTime: number
    requestCount: number
  }>
  errorBreakdown: Array<{
    errorType: string
    count: number
    percentage: number
  }>
  cacheHitRate: number
}

export type AnalyticsReport =
  | UserActivityReport
  | ProblemCompletionReport
  | ContestParticipationReport
  | RevenueReport
  | PerformanceReport

export const analyticsApi = {
  async getReport<T extends AnalyticsReport>(params: AnalyticsQueryParams): Promise<T> {
    const response = await apiGet<T>('/admin/analytics', { params })
    return response
  },

  async getUserActivity(params: {
    days?: number
    startDate?: string
    endDate?: string
  }): Promise<UserActivityReport> {
    const response = await apiGet<UserActivityReport>('/admin/analytics/user-activity', {
      params,
    })
    return response
  },

  async getProblemCompletion(params: {
    days?: number
    startDate?: string
    endDate?: string
  }): Promise<ProblemCompletionReport> {
    const response = await apiGet<ProblemCompletionReport>('/admin/analytics/problem-completion', {
      params,
    })
    return response
  },

  async getContestParticipation(params: {
    days?: number
    startDate?: string
    endDate?: string
  }): Promise<ContestParticipationReport> {
    const response = await apiGet<ContestParticipationReport>(
      '/admin/analytics/contest-participation',
      { params },
    )
    return response
  },

  async getRevenue(params: {
    days?: number
    startDate?: string
    endDate?: string
  }): Promise<RevenueReport> {
    const response = await apiGet<RevenueReport>('/admin/analytics/revenue', { params })
    return response
  },

  async getPerformance(): Promise<PerformanceReport> {
    const response = await apiGet<PerformanceReport>('/admin/analytics/performance')
    return response
  },
}
