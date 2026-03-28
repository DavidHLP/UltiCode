export default {
  title: '数据分析',
  description: '查看平台运营数据和用户行为分析',
  loadError: '加载报告失败',
  noData: '暂无数据',
  authRequired: '请先登录',
  adminRequired: '需要管理员权限',
  adminRequiredWithRole: '需要管理员权限（当前角色：{role}）',
  permissionDenied: '您没有权限查看此页面',
  refreshSession: '刷新会话',
  sessionRefreshed: '会话刷新成功',
  sessionRefreshFailed: '会话刷新失败',

  // 导航
  nav: {
    userActivity: '用户活动',
    problemCompletion: '题目完成',
    contestParticipation: '比赛参与',
    revenue: '收入统计',
    performance: '系统性能',
  },

  // 时间周期
  periods: {
    '7days': '近7天',
    '30days': '近30天',
    '90days': '近90天',
    '1year': '近一年',
  },

  // 状态标签
  status: {
    good: '良好',
    average: '一般',
    needsWork: '需改进',
    needsAttention: '需关注',
    excellent: '优秀',
    high: '偏高',
    normal: '正常',
  },

  perContest: '/场比赛',

  // 热力图图例
  heatmap: {
    less: '少',
    more: '多',
  },

  // 用户活动
  userActivity: {
    dailyActiveUsers: '日活跃用户',
    retention1d: '次日留存率',
    retention7d: '7日留存率',
    retention30d: '30日留存率',
    activeUsersTrend: '活跃用户趋势',
    activeUsersTrendDesc: '每日活跃用户数变化',
    peakHours: '活跃时段分布',
    peakHoursDesc: '用户活跃时间热力图',
    topUsers: '最活跃用户',
    topUsersDesc: '登录次数最多的用户',
    logins: '{count} 次登录',
    activeUsers: '活跃用户',
  },

  // 题目完成
  problemCompletion: {
    totalAttempts: '总提交次数',
    successfulAttempts: '成功提交',
    completionRate: '完成率',
    trendingProblems: '热门题目',
    completed: '已完成',
    byDifficulty: '按难度统计',
    byDifficultyDesc: '各难度的完成率分布',
    hardestProblems: '最难题目',
    hardestProblemsDesc: '完成率最低的题目',
    topTags: '热门标签',
    topTagsDesc: '按标签分类的完成情况',
  },

  // 比赛参与
  contestParticipation: {
    totalContests: '比赛总数',
    totalParticipants: '参与人次',
    avgParticipants: '平均参与人数',
    virtualParticipation: '虚拟参赛',
    contests: '场比赛',
    byType: '按类型统计',
    byTypeDesc: '各类型比赛的参与情况',
    topContests: '最受欢迎比赛',
    topContestsDesc: '参与人数最多的比赛',
  },

  contestParticipants: '参赛者',

  // 收入统计
  revenue: {
    mrr: '月经常性收入',
    arr: '年经常性收入',
    subscribers: '订阅用户',
    conversionRate: '转化率',
    byPlan: '按套餐统计',
    byPlanDesc: '各套餐的收入分布',
    metrics: '关键指标',
    arpu: '每用户平均收入',
    churnRate: '流失率',
    totalRevenue: '总收入',
  },

  // 系统性能
  performance: {
    uptime: '系统运行时间',
    throughput: '吞吐量',
    errorRate: '错误率',
    memoryUsage: '内存使用率',
    requests: '次请求',
    resourceUsage: '资源使用情况',
    slowestEndpoints: '最慢接口',
    slowestEndpointsDesc: '平均响应时间最长的接口',
    cpu: 'CPU',
    memory: '内存',
    disk: '磁盘',
  },
} as const
