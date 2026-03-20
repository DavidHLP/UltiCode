export default {
  title: '订阅管理',
  subtitle: '查看和管理您的订阅计划',

  currentPlan: '当前计划',
  planDetails: '计划详情',
  statusLabel: '状态',
  startedAt: '开始时间',
  expiresAt: '到期时间',
  cancelledAt: '取消时间',
  noSubscription: '您当前使用的是免费计划',

  status: {
    ACTIVE: '生效中',
    CANCELLED: '已取消',
    EXPIRED: '已过期',
    PENDING: '待生效',
  },

  plans: {
    FREE: '免费版',
    PRO: '专业版',
    PREMIUM: '高级版',
  },

  features: {
    free: {
      title: '免费版功能',
      description: '基础功能，适合入门学习',
    },
    premium: {
      title: '高级版功能',
      description: '解锁全部功能，获得最佳体验',
    },
    premiumProblems: '访问所有高级题目',
    prioritySupport: '优先客服支持',
    advancedAnalytics: '高级数据分析',
    unlimitedContests: '无限比赛参与',
    freeProblems: '访问免费题目',
    communityForum: '社区论坛访问',
    basicAnalytics: '基础数据分析',
  },

  upgradePrompt: '升级到高级版以解锁全部功能，获得最佳平台体验。',
  manageSubscription: '如需管理订阅，请联系客服。',
} as const
