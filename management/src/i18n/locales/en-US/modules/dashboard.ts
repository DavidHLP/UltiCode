export default {
  title: 'Dashboard',
  welcome: 'Welcome back',
  loading: 'Loading dashboard...',

  // Stats cards
  stats: {
    totalUsers: 'Total Users',
    activeToday: 'Active Today',
    activeThisWeek: 'Active This Week',
    totalProblems: 'Total Problems',
    published: 'Published',
    unpublished: 'Unpublished',
    activeContests: 'Active Contests',
    upcoming: 'Upcoming',
    finished: 'Finished',
    flaggedContent: 'Flagged Content',
    actionNeeded: 'Action Needed',
    totalSubmissions: 'Total Submissions',
    pending: 'Pending',
    success: 'Success',
    failed: 'Failed',
    revenue: 'Revenue',
    mrr: 'Monthly Recurring Revenue',
    growth: 'Growth',
  },

  // Quick actions
  quickActions: {
    title: 'Quick Actions',
    createProblem: 'Create Problem',
    createContest: 'Create Contest',
    createUser: 'Add User',
    viewReports: 'View Reports',
    moderateContent: 'Moderate Content',
    systemSettings: 'System Settings',
  },

  // Recent activity
  recentActivity: {
    title: 'Recent Activity',
    noActivity: 'No recent activity',
    viewAll: 'View All',
    types: {
      user_registered: 'New user registered',
      problem_created: 'Problem created',
      problem_updated: 'Problem updated',
      contest_started: 'Contest started',
      contest_ended: 'Contest ended',
      submission_accepted: 'Submission accepted',
      flag_created: 'Content flagged',
    },
  },

  // System status
  systemStatus: {
    title: 'System Status',
    healthy: 'Healthy',
    degraded: 'Degraded',
    down: 'Down',
    uptime: 'Uptime',
    cpu: 'CPU Usage',
    memory: 'Memory Usage',
    disk: 'Disk Usage',
    responseTime: 'Response Time',
    activeConnections: 'Active Connections',
  },

  // Charts
  charts: {
    userGrowth: 'User Growth',
    submissionTrend: 'Submission Trend',
    problemCompletion: 'Problem Completion',
    revenueOverview: 'Revenue Overview',
    period: {
      '7d': '7 Days',
      '30d': '30 Days',
      '90d': '90 Days',
      '1y': '1 Year',
    },
  },
} as const
