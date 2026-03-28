export default {
  title: 'Analytics',
  description: 'View platform performance metrics and user behavior insights',
  loadError: 'Failed to load report',
  noData: 'No data available',
  authRequired: 'Please login first',
  adminRequired: 'Admin privileges required',
  adminRequiredWithRole: 'Admin privileges required (Current role: {role})',
  permissionDenied: 'You do not have permission to view this page',
  refreshSession: 'Refresh Session',
  sessionRefreshed: 'Session refreshed successfully',
  sessionRefreshFailed: 'Failed to refresh session',

  // Navigation
  nav: {
    userActivity: 'User Activity',
    problemCompletion: 'Problem Completion',
    contestParticipation: 'Contest Participation',
    revenue: 'Revenue',
    performance: 'Performance',
  },

  // Periods
  periods: {
    '7days': 'Last 7 Days',
    '30days': 'Last 30 Days',
    '90days': 'Last 90 Days',
    '1year': 'Last Year',
  },

  // Status labels
  status: {
    good: 'Good',
    average: 'Average',
    needsWork: 'Needs Work',
    needsAttention: 'Needs Attention',
    excellent: 'Excellent',
    high: 'High',
    normal: 'Normal',
  },

  perContest: '/contest',

  // Heatmap legend
  heatmap: {
    less: 'Less',
    more: 'More',
  },

  // User Activity section
  userActivity: {
    dailyActiveUsers: 'Daily Active Users',
    retention1d: '1-Day Retention',
    retention7d: '7-Day Retention',
    retention30d: '30-Day Retention',
    activeUsersTrend: 'Active Users Trend',
    activeUsersTrendDesc: 'Daily active users over time',
    peakHours: 'Peak Activity Hours',
    peakHoursDesc: 'User activity heatmap by hour',
    topUsers: 'Top Active Users',
    topUsersDesc: 'Users with most logins',
    logins: '{count} logins',
    activeUsers: 'Active Users',
  },

  // Problem Completion section
  problemCompletion: {
    totalAttempts: 'Total Attempts',
    successfulAttempts: 'Successful Attempts',
    completionRate: 'Completion Rate',
    trendingProblems: 'Trending Problems',
    completed: 'completed',
    byDifficulty: 'By Difficulty',
    byDifficultyDesc: 'Completion rates by problem difficulty',
    hardestProblems: 'Hardest Problems',
    hardestProblemsDesc: 'Problems with lowest completion rates',
    topTags: 'Top Tags',
    topTagsDesc: 'Completion rates by problem tag',
  },

  // Contest Participation section
  contestParticipation: {
    totalContests: 'Total Contests',
    totalParticipants: 'Total Participants',
    avgParticipants: 'Avg. Participants',
    virtualParticipation: 'Virtual Participation',
    contests: 'contests',
    byType: 'By Type',
    byTypeDesc: 'Participation by contest type',
    topContests: 'Top Contests',
    topContestsDesc: 'Contests with most participants',
  },

  contestParticipants: 'participants',

  // Revenue section
  revenue: {
    mrr: 'Monthly Recurring Revenue',
    arr: 'Annual Recurring Revenue',
    subscribers: 'Subscribers',
    conversionRate: 'Conversion Rate',
    byPlan: 'By Plan',
    byPlanDesc: 'Revenue distribution by subscription plan',
    metrics: 'Key Metrics',
    arpu: 'Average Revenue Per User',
    churnRate: 'Churn Rate',
    totalRevenue: 'Total Revenue',
  },

  // Performance section
  performance: {
    uptime: 'Uptime',
    throughput: 'Throughput',
    errorRate: 'Error Rate',
    memoryUsage: 'Memory Usage',
    requests: 'requests',
    resourceUsage: 'Resource Usage',
    slowestEndpoints: 'Slowest Endpoints',
    slowestEndpointsDesc: 'Endpoints with highest average response time',
    cpu: 'CPU',
    memory: 'Memory',
    disk: 'Disk',
  },
} as const
