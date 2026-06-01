export default {
  title: '比赛管理',
  detailTitle: '比赛详情',
  scoringRules: '评分规则',
  createContest: '创建比赛',
  searchPlaceholder: '搜索比赛...',
  clearSelection: '清除选择',

  // 列定义
  columns: {
    id: 'ID',
    contest: '比赛',
    title: '标题',
    type: '类型',
    status: '状态',
    schedule: '时间安排',
    startTime: '开始时间',
    endTime: '结束时间',
    participants: '参赛人数',
    problems: '题目数',
    actions: '操作',
  },

  // 比赛类型
  type: {
    all: '全部类型',
    biweekly: '双周赛',
    weekly: '周赛',
    RATED: '积分赛',
    UNRATED: '练习赛',
    EDUCATIONAL: '教学赛',
    MONTHLY: '月赛',
    WEEKLY: '周赛',
    IOI: 'IOI赛制',
    ICPC: 'ICPC赛制',
    CUSTOM: '自定义',
    PUBLIC: '公开',
    PRIVATE: '私有',
    VIRTUAL: '虚拟',
  },

  // 比赛状态
  status: {
    all: '全部状态',
    DRAFT: '草稿',
    UPCOMING: '即将开始',
    ONGOING: '进行中',
    RUNNING: '进行中',
    FINISHED: '已结束',
    CANCELLED: '已取消',
    draft: '草稿',
    published: '已发布',
    registering: '报名中',
    upcoming: '即将开始',
    ongoing: '进行中',
    running: '进行中',
    freezing: '冻结中',
    finished: '已结束',
    cancelled: '已取消',
    archived: '已归档',
  },

  // 筛选器
  filters: {
    allStatus: '全部状态',
    allTypes: '全部类型',
    status: {
      draft: '草稿',
      upcoming: '即将开始',
      running: '进行中',
      finished: '已结束',
      cancelled: '已取消',
    },
    type: {
      ICPC: 'ICPC赛制',
      IOI: 'IOI赛制',
      CUSTOM: '自定义',
      public: '公开',
      private: '私有',
      virtual: '虚拟',
    },
  },

  // 统计
  stats: {
    contestManagement: '比赛管理',
    total: '总计',
    running: '进行中',
    upcoming: '即将开始',
    finished: '已结束',
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
    bulkDelete: '批量删除',
    viewDetails: '查看详情',
    startContest: '开始比赛',
    endContest: '结束比赛',
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

  // 基本信息步骤
  basics: {
    title: '标题',
    titlePlaceholder: '请输入比赛标题',
    titleDescription: '比赛的描述性标题',
    slug: '标识符',
    slugPlaceholder: 'contest-slug',
    slugDescription: 'URL友好的标识符（小写，仅限连字符）',
    type: '类型',
    typePlaceholder: '选择比赛类型',
    typeDescription: '公开比赛对所有用户可见',
    description: '描述',
    descriptionPlaceholder: '请输入比赛描述...',
    types: {
      ICPC: 'ICPC赛制',
      IOI: 'IOI赛制',
      CUSTOM: '自定义',
    },
  },

  // 时间安排步骤
  scheduleStep: {
    startTime: '开始时间',
    startTimeDescription: '比赛开始的时间',
    duration: '时长（分钟）',
    durationDescription: '比赛持续的时长',
    publishImmediately: '立即发布',
    publishImmediatelyDescription: '立即使比赛对用户可见',
    minutes: '{minutes} 分钟',
    notSet: '未设置',
  },

  // 题目步骤
  problemsStep: {
    addProblem: '添加题目',
    title: '标题',
    difficulty: '难度',
    score: '分数',
    noProblemsSelected: '未选择题目。点击"添加题目"来添加题目。',
  },

  // 确认步骤
  reviewStep: {
    basicInfo: '基本信息',
    schedule: '时间安排',
    startTime: '开始时间',
    duration: '时长',
    visibility: '可见性',
    defaultScoringRule: '使用默认计分规则',
    problemsCount: '已选择 {count} 道题目',
    noProblemsSelected: '未选择题目',
  },

  // 计分规则
  scoringRule: {
    selectRule: '计分规则',
    selectPlaceholder: '选择计分规则',
    createNew: '创建新规则',
    selectDescription: '为比赛选择计分规则或创建新规则',
  },

  // 向导
  wizard: {
    createContest: '创建比赛',
    description: '按照以下步骤创建新比赛',
    steps: {
      basic: '基本信息',
      problems: '题目选择',
      schedule: '时间安排',
      review: '确认发布',
    },
    basics: '基本信息',
    scoring: '计分规则',
    schedule: '时间安排',
    problems: '题目',
    review: '确认',
    previous: '上一步',
    back: '上一步',
    next: '下一步',
    submit: '创建比赛',
    update: '更新比赛',
  },

  // Toast 消息
  toast: {
    createSuccess: '比赛创建成功',
    createFailed: '创建比赛失败',
    createdSuccessfully: '比赛创建成功',
    failedToCreate: '创建比赛失败',
    updateSuccess: '比赛更新成功',
    updateFailed: '更新比赛失败',
    deleteSuccess: '比赛删除成功',
    deleteFailed: '删除比赛失败',
    deletedSuccessfully: '比赛删除成功',
    failedToDelete: '删除比赛失败',
    cancelSuccess: '比赛取消成功',
    cancelFailed: '取消比赛失败',
    publishSuccess: '公告发布成功',
    publishFailed: '发布公告失败',
    startedSuccessfully: '比赛已开始',
    failedToStart: '开始比赛失败',
    endedSuccessfully: '比赛已结束',
    failedToEnd: '结束比赛失败',
    bulkDeleteSuccess: '已成功删除 {count} 场比赛',
    bulkDeleteFailed: '批量删除比赛失败',
    problemAdded: '题目添加成功',
    failedToAddProblem: '添加题目失败',
    problemRemoved: '题目移除成功',
    failedToRemoveProblem: '移除题目失败',
    invalidStartTime: '开始时间格式无效',
  },

  // 确认消息
  confirmation: {
    bulkDelete: '确定要删除 {count} 场比赛吗？',
    startNow: '确定要立即开始这场比赛吗？',
    endNow: '确定要立即结束这场比赛吗？',
    deleteThis: '确定要删除这场比赛吗？',
    removeProblem: '确定要从比赛中移除这道题目吗？',
  },

  // 删除对话框
  delete: {
    title: '删除比赛',
    description: '确定要删除比赛 "{title}" 吗？此操作不可撤销。',
    thisContest: '此比赛',
    confirm: '删除',
    cancel: '取消',
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

  // 详情视图
  detail: {
    overview: '概览',
    problems: '题目',
    participants: '参赛者',
    rankings: '排名',
    start: '开始',
    end: '结束',
    description: '描述',
    noDescription: '暂无描述',
    slug: '标识符',
    visibility: '可见性',
    startTime: '开始时间',
    duration: '时长',
    addProblem: '添加题目',
    problem: '题目',
    difficulty: '难度',
    score: '分数',
    noProblemsAdded: '暂未添加题目',
    user: '用户',
    joinedAt: '加入时间',
    noParticipantsYet: '暂无参赛者',
    rank: '排名',
    penalty: '罚时',
    noRankingsYet: '暂无排名数据',
    contestNotFound: '未找到比赛',
    backToList: '返回列表',
    hidden: '隐藏',
    statusPublished: '已发布',
    statusHidden: '已隐藏',
  },

  // 抽屉
  drawer: {
    title: '比赛详情',
    subtitle: '查看比赛信息',
    loadingDetails: '正在加载比赛详情...',
    contestNotFound: '未找到比赛',
    fullView: '完整视图',
    published: '已发布',
    problems: '题目',
    participants: '参赛者',
    start: '开始时间',
    duration: '时长',
    pts: '分',
  },

  // 题目选择器
  problemPicker: {
    title: '选择题目',
    description: '搜索并选择要添加到比赛的题目',
    searchPlaceholder: '搜索题目...',
    noProblemsFound: '未找到题目',
    problems: '题目列表',
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
