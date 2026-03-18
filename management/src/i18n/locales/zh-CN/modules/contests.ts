export default {
  title: '比赛管理',
  detailTitle: '比赛详情',
  scoringRules: '评分规则',
  createContest: '创建比赛',
  searchPlaceholder: '搜索比赛...',

  // 列定义
  columns: {
    id: 'ID',
    title: '标题',
    type: '类型',
    status: '状态',
    startTime: '开始时间',
    endTime: '结束时间',
    participants: '参赛人数',
    problems: '题目数',
  },

  // 比赛类型
  type: {
    all: '全部类型',
    RATED: '积分赛',
    UNRATED: '练习赛',
    EDUCATIONAL: '教学赛',
    MONTHLY: '月赛',
    WEEKLY: '周赛',
  },

  // 比赛状态
  status: {
    all: '全部状态',
    UPCOMING: '即将开始',
    ONGOING: '进行中',
    FINISHED: '已结束',
    CANCELLED: '已取消',
  },

  // 操作
  actions: {
    view: '查看',
    edit: '编辑',
    delete: '删除',
    duplicate: '复制',
    cancel: '取消比赛',
    viewRankings: '查看排名',
    viewSubmissions: '查看提交',
    manageProblems: '管理题目',
    publish: '发布公告',
  },

  // 表单
  form: {
    title: '标题',
    titlePlaceholder: '请输入比赛标题',
    description: '描述',
    descriptionPlaceholder: '请输入比赛描述...',
    type: '类型',
    startTime: '开始时间',
    endTime: '结束时间',
    duration: '时长',
    isPublic: '公开比赛',
    isRated: '积分赛',
    maxParticipants: '最大参赛人数',
    password: '比赛密码',
    passwordPlaceholder: '留空则无需密码',
    rules: '比赛规则',
    scoringRules: '计分规则',
  },

  // 向导
  wizard: {
    steps: {
      basic: '基本信息',
      problems: '题目选择',
      schedule: '时间安排',
      review: '确认发布',
    },
    back: '上一步',
    next: '下一步',
    submit: '创建比赛',
    update: '更新比赛',
  },

  // Toast 消息
  toast: {
    createSuccess: '比赛创建成功',
    createFailed: '创建比赛失败',
    updateSuccess: '比赛更新成功',
    updateFailed: '更新比赛失败',
    deleteSuccess: '比赛删除成功',
    deleteFailed: '删除比赛失败',
    cancelSuccess: '比赛取消成功',
    cancelFailed: '取消比赛失败',
    publishSuccess: '公告发布成功',
    publishFailed: '发布公告失败',
  },

  // 对话框
  dialogs: {
    deleteTitle: '确认删除',
    deleteDescription: '确定要删除比赛 "{title}" 吗？此操作不可撤销。',
    cancelTitle: '确认取消',
    cancelDescription: '确定要取消比赛 "{title}" 吗？已报名的用户将收到通知。',
  },

  // 详情
  details: {
    overview: '概览',
    problems: '题目',
    rankings: '排名',
    submissions: '提交',
    announcements: '公告',
    participants: '参赛者',
    statistics: '统计',
  },

  // 计分规则
  scoring: {
    title: '计分规则',
    addRule: '添加规则',
    editRule: '编辑规则',
    deleteRule: '删除规则',
    type: '规则类型',
    value: '分值',
    description: '描述',
    types: {
      FIRST_BLOOD: '首杀奖励',
      TIME_BONUS: '时间奖励',
      DIFFICULTY_BONUS: '难度奖励',
      PENALTY: '罚时',
    },
  },
} as const
