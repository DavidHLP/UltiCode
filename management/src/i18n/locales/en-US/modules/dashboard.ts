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
    allClear: 'All Clear',
    pendingModeration: 'Pending Moderation',
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

  // Chart (DashboardView uses this)
  chart: {
    userRegistrationTrend: 'User Registration Trend',
    dailyRegistrations: 'Daily new user registrations',
  },

  // Time period selector
  timePeriod: {
    last7Days: 'Last 7 Days',
    last30Days: 'Last 30 Days',
    last90Days: 'Last 90 Days',
    allTime: 'All Time',
  },

  // Time ago
  timeAgo: {
    justNow: 'Just now',
    minuteAgo: '{count} minute ago | {count} minutes ago',
    hourAgo: '{count} hour ago | {count} hours ago',
    dayAgo: '{count} day ago | {count} days ago',
  },

  // Timeline
  timeline: {
    title: 'Activity Timeline',
    description: 'Recent system activity records',
    activityTypes: {
      LOGIN: 'Login',
      CREATE: 'Create',
      UPDATE: 'Update',
      DELETE: 'Delete',
      PUBLISH: 'Publish',
      UNPUBLISH: 'Unpublish',
      FLAG: 'Flag',
      UNFLAG: 'Unflag',
      BAN: 'Ban',
      UNBAN: 'Unban',
      MODERATE: 'Moderate',
      MODERATE_APPROVE: 'Moderate Approve',
      MODERATE_REJECT: 'Moderate Reject',
      PIN: 'Pin',
      UNPIN: 'Unpin',
      LOCK: 'Lock',
      UNLOCK: 'Unlock',
      RESET_PASSWORD: 'Reset Password',
      UPDATE_USER: 'Update User',
      BAN_USER: 'Ban User',
      UNBAN_USER: 'Unban User',
      CREATE_FORUM_POST: 'Create Forum Post',
      UPDATE_FORUM_POST: 'Update Forum Post',
      DELETE_FORUM_POST: 'Delete Forum Post',
      PIN_POST: 'Pin Post',
      UNPIN_POST: 'Unpin Post',
      LOCK_POST: 'Lock Post',
      UNLOCK_POST: 'Unlock Post',
      FLAG_POST: 'Flag Post',
      UNFLAG_POST: 'Unflag Post',
    },
  },
} as const
