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
    types: {
      user_registered: '新用户注册',
      problem_created: '题目创建',
      problem_updated: '题目更新',
      contest_started: '比赛开始',
      contest_ended: '比赛结束',
      submission_accepted: '提交通过',
      flag_created: '内容标记',
    },
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
} as const
