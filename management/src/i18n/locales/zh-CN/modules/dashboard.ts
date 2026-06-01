export default {
  title: '仪表板',
  welcome: '欢迎回来',
  loading: '加载仪表板中...',

  // 统计卡片
  stats: {
    totalUsers: '总用户数',
    activeToday: '今日活跃',
    activeThisWeek: '本周活跃',
    totalProblems: '总题目数',
    published: '已发布',
    unpublished: '未发布',
    activeContests: '进行中的比赛',
    upcoming: '即将开始',
    finished: '已结束',
    flaggedContent: '待审核内容',
    actionNeeded: '需要处理',
    allClear: '全部正常',
    pendingModeration: '待审核',
    totalSubmissions: '总提交数',
    pending: '待处理',
    success: '成功',
    failed: '失败',
    revenue: '收入',
    mrr: '月经常性收入',
    growth: '增长',
  },

  // 快捷操作
  quickActions: {
    title: '快捷操作',
    createProblem: '创建题目',
    createContest: '创建比赛',
    createUser: '添加用户',
    viewReports: '查看报告',
    moderateContent: '审核内容',
    systemSettings: '系统设置',
  },

  // 最近活动
  recentActivity: {
    title: '最近活动',
    noActivity: '暂无最近活动',
    viewAll: '查看全部',
  },

  // 系统状态
  systemStatus: {
    title: '系统状态',
    healthy: '正常',
    degraded: '降级',
    down: '宕机',
    uptime: '运行时间',
    cpu: 'CPU 使用率',
    memory: '内存使用率',
    disk: '磁盘使用率',
    responseTime: '响应时间',
    activeConnections: '活跃连接',
  },

  // 图表
  charts: {
    userGrowth: '用户增长',
    submissionTrend: '提交趋势',
    problemCompletion: '题目完成率',
    revenueOverview: '收入概览',
    period: {
      '7d': '7 天',
      '30d': '30 天',
      '90d': '90 天',
      '1y': '1 年',
    },
  },

  // 图表（DashboardView 使用）
  chart: {
    userRegistrationTrend: '用户注册趋势',
    dailyRegistrations: '每日新注册用户',
  },

  // 时间段选择器
  timePeriod: {
    last7Days: '最近 7 天',
    last30Days: '最近 30 天',
    last90Days: '最近 90 天',
    allTime: '全部时间',
  },

  // 时间格式
  timeAgo: {
    justNow: '刚刚',
    minuteAgo: '{count} 分钟前',
    hourAgo: '{count} 小时前',
    dayAgo: '{count} 天前',
  },

  // 时间线
  timeline: {
    title: '活动时间线',
    description: '最近的系统活动记录',
    activityTypes: {
      LOGIN: '登录',
      CREATE: '创建',
      UPDATE: '更新',
      DELETE: '删除',
      PUBLISH: '发布',
      UNPUBLISH: '取消发布',
      FLAG: '标记',
      UNFLAG: '取消标记',
      BAN: '封禁',
      UNBAN: '解封',
      MODERATE: '审核',
      MODERATE_APPROVE: '审核通过',
      MODERATE_REJECT: '审核拒绝',
      PIN: '置顶',
      UNPIN: '取消置顶',
      LOCK: '锁定',
      UNLOCK: '解锁',
      RESET_PASSWORD: '重置密码',
      UPDATE_USER: '更新用户',
      BAN_USER: '封禁用户',
      UNBAN_USER: '解封用户',
      CREATE_FORUM_POST: '创建论坛帖子',
      UPDATE_FORUM_POST: '更新论坛帖子',
      DELETE_FORUM_POST: '删除论坛帖子',
      PIN_POST: '置顶帖子',
      UNPIN_POST: '取消置顶帖子',
      LOCK_POST: '锁定帖子',
      UNLOCK_POST: '解锁帖子',
      FLAG_POST: '标记帖子',
      UNFLAG_POST: '取消标记帖子',
    },
  },
} as const
