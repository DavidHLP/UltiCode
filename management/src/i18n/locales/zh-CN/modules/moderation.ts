export default {
  title: '审核队列',
  description: '审核和管理被标记的内容',
  searchPlaceholder: '搜索...',

  // ========== 举报类别 (9种) ==========
  categories: {
    title: '类别',
    all: '全部类别',
    SPAM: '垃圾信息',
    HARASSMENT: '骚扰',
    HATE_SPEECH: '仇恨言论',
    VIOLENCE: '暴力内容',
    SEXUAL_CONTENT: '色情内容',
    MISINFORMATION: '虚假信息',
    WRONG_ANSWER: '错误答案',
    COPYRIGHT: '版权侵权',
    OTHER: '其他',
  },

  // 类别描述
  categoryDescriptions: {
    SPAM: '未经请求的推广内容或重复发布',
    HARASSMENT: '针对个人的骚扰或欺凌',
    HATE_SPEECH: '宣扬仇恨或歧视的内容',
    VIOLENCE: '描述或宣扬暴力的内容',
    SEXUAL_CONTENT: '性暗示或不当内容',
    MISINFORMATION: '虚假或误导性信息',
    WRONG_ANSWER: '解答包含错误的代码或逻辑',
    COPYRIGHT: '侵犯版权或抄袭',
    OTHER: '其他未在特定类别中涵盖的违规行为',
  },

  // ========== 审核状态 (5种) ==========
  status: {
    title: '状态',
    all: '全部状态',
    PENDING: '待处理',
    UNDER_REVIEW: '审核中',
    RESOLVED: '已解决',
    DISMISSED: '已驳回',
    APPEAL_PENDING: '申诉中',
  },

  // 状态描述
  statusDescriptions: {
    PENDING: '等待审核员审核',
    UNDER_REVIEW: '正在由审核员审核',
    RESOLVED: '问题已处理',
    DISMISSED: '举报无效',
    APPEAL_PENDING: '用户已提交申诉',
  },

  // ========== 审核操作 (11种) ==========
  actions: {
    title: '操作',
    DELETED: '删除内容',
    HIDDEN: '隐藏内容',
    RESTORED: '恢复内容',
    WARNED: '发出警告',
    TEMP_BANNED: '临时封禁',
    PERM_BANNED: '永久封禁',
    DISMISSED: '驳回举报',
    RESOLVED: '标记已解决',
    APPEAL_PENDING: '申诉中',
    APPEAL_APPROVED: '批准申诉',
    APPEAL_REJECTED: '拒绝申诉',
  },

  // 操作描述
  actionDescriptions: {
    DELETED: '永久删除被举报的内容',
    HIDDEN: '将内容从公开视图中隐藏',
    RESTORED: '恢复之前隐藏的内容',
    WARNED: '向用户发送警告',
    TEMP_BANNED: '临时封禁用户',
    PERM_BANNED: '永久封禁用户',
    DISMISSED: '将举报标记为无效',
    RESOLVED: '将问题标记为已解决',
    APPEAL_PENDING: '用户已提交申诉',
    APPEAL_APPROVED: '批准用户的申诉',
    APPEAL_REJECTED: '拒绝用户的申诉',
  },

  // ========== 实体类型 (5种) ==========
  entityTypes: {
    title: '实体类型',
    all: '全部类型',
    forum_post: '论坛帖子',
    forum_comment: '论坛评论',
    solution: '解题方案',
    solution_comment: '方案评论',
    problem: '题目',
  },

  // ========== 申诉状态 (4种) ==========
  appealStatus: {
    title: '申诉状态',
    all: '全部申诉',
    PENDING: '待处理',
    UNDER_REVIEW: '审核中',
    APPROVED: '已批准',
    REJECTED: '已拒绝',
  },

  // ========== 举报状态 (4种) ==========
  reportStatus: {
    title: '举报状态',
    all: '全部举报',
    PENDING: '待处理',
    REVIEWED: '已审核',
    RESOLVED: '已解决',
    DISMISSED: '已驳回',
  },

  // ========== 审核队列 ==========
  queue: {
    pageTitle: '内容审核',
    title: '审核队列',
    description: '审核和管理被举报的内容',
    emptyTitle: '队列为空',
    emptyDescription: '当前没有需要审核的内容。',
    claimItem: '认领项目',
    assignTo: '分配给',
    unassign: '取消分配',
    performAction: '执行操作',
    batchActions: '批量操作',
    selectedCount: '已选择 {count} 项',
    priority: '优先级',
    reportCount: '举报数',
    assignedTo: '分配给',
    unassigned: '未分配',
    claimedBy: '由 {name} 认领',
    viewDetails: '查看详情',
    viewEntity: '查看内容',
    viewReports: '查看举报 ({count})',
  },

  // ========== 统计仪表板 ==========
  stats: {
    title: '统计数据',
    overview: '概览',
    totalPending: '待处理',
    totalUnderReview: '审核中',
    totalResolved: '已解决',
    totalDismissed: '已驳回',
    totalAppealPending: '待申诉',
    avgResolutionTime: '平均处理时间',
    hours: '小时',
    byCategory: '按类别',
    byEntityType: '按实体类型',
    recentActivity: '最近活动',
    noData: '暂无数据',
  },

  // ========== 详情视图 ==========
  detail: {
    title: '审核详情',
    entityInfo: '实体信息',
    entityPreview: '内容预览',
    reportsTitle: '举报 ({count})',
    actionsTitle: '操作历史 ({count})',
    appealTitle: '申诉详情',
    noReports: '未找到举报',
    noActions: '暂无操作记录',
    reporter: '举报者',
    reportedAt: '举报时间',
    reason: '原因',
    evidence: '证据',
    performedBy: '操作者',
    performedAt: '操作时间',
    note: '备注',
    duration: '时长',
    days: '{count} 天',
    moreReports: '还有 {count} 个举报',
  },

  // ========== 操作面板 ==========
  actionPanel: {
    title: '执行操作',
    selectAction: '选择操作',
    addNote: '添加备注（可选）',
    notePlaceholder: '输入说明您决定的备注...',
    durationLabel: '封禁时长（天）',
    durationPlaceholder: '输入天数...',
    confirmAction: '确认操作',
    confirming: '处理中...',
    warning: '此操作将被记录，无法撤销。',
    days: '天',
  },

  // ========== 申诉视图 ==========
  appeals: {
    pageTitle: '申诉管理',
    title: '申诉管理',
    description: '审核用户对审核决定的申诉',
    emptyTitle: '暂无申诉',
    emptyDescription: '当前没有待处理的申诉。',
    appellant: '申诉人',
    reason: '申诉原因',
    evidence: '证据',
    submittedAt: '提交时间',
    reviewedBy: '审核人',
    reviewedAt: '审核时间',
    response: '回复',
    approveAppeal: '批准申诉',
    rejectAppeal: '拒绝申诉',
    reviewAppeal: '审核申诉',
    responsePlaceholder: '输入您对此申诉的回复...',
    decision: '决定',
    reviewDescription: '审核申诉并做出决定。',
  },

  // ========== 举报视图 ==========
  reports: {
    pageTitle: '举报管理',
    title: '举报管理',
    description: '查看所有内容举报',
    emptyTitle: '暂无举报',
    emptyDescription: '尚未收到任何举报。',
    reporter: '举报者',
    entity: '实体',
    entityType: '类型',
    category: '类别',
    reason: '原因',
    evidence: '证据',
    submittedAt: '提交时间',
    status: '状态',
    viewEntity: '查看实体',
    viewQueue: '在队列中查看',
    noQueueItem: '该举报没有关联的队列项目',
  },

  // ========== 筛选器 ==========
  filters: {
    title: '筛选器',
    clearAll: '清除全部',
    status: '状态',
    category: '类别',
    entityType: '实体类型',
    assignedTo: '分配给',
    minPriority: '最低优先级',
    dateRange: '日期范围',
    from: '从',
    to: '到',
    apply: '应用筛选',
    activeFilters: '已激活筛选',
  },

  // ========== 对话框 ==========
  dialogs: {
    confirmTitle: '确认操作',
    confirmMessage: '您确定要{action}吗？',
    confirmBatchTitle: '确认批量操作',
    confirmBatchMessage: '您确定要对 {count} 个项目{action}吗？',
    cancel: '取消',
    confirm: '确认',
    close: '关闭',
  },

  // ========== Toast 消息 ==========
  toast: {
    success: '操作成功完成',
    error: '发生错误',
    claimed: '项目认领成功',
    assigned: '项目分配成功',
    unassigned: '已取消分配',
    actionCompleted: '操作成功完成',
    batchCompleted: '批量操作完成',
    appealApproved: '申诉已批准',
    appealRejected: '申诉已拒绝',
    loadError: '加载数据失败',
    networkError: '网络错误，请重试。',
  },

  // ========== 空状态 ==========
  empty: {
    title: '无结果',
    description: '没有符合当前筛选条件的项目。',
    clearFilters: '清除筛选',
  },

  // ========== 列定义 ==========
  columns: {
    entity: '实体',
    entityType: '类型',
    title: '标题',
    category: '类别',
    status: '状态',
    priority: '优先级',
    reports: '举报数',
    assignedTo: '分配给',
    createdAt: '创建时间',
    updatedAt: '更新时间',
    actions: '操作',
    reporter: '举报者',
    reason: '原因',
    resolution: '操作内容',
    queueId: '队列ID',
    id: 'ID',
  },

  // ========== 终端风格 ==========
  terminal: {
    loading: '加载中...',
    selected: '已选择',
    total: '总计',
    pending: '待处理',
    reviewed: '已审核',
    resolved: '已解决',
    dismissed: '已驳回',
    underReview: '审核中',
  },

  // ========== 时间相关 ==========
  time: {
    justNow: '刚刚',
    minutesAgo: '{count} 分钟前',
    hoursAgo: '{count} 小时前',
    daysAgo: '{count} 天前',
  },

  // ========== 优先级 ==========
  priority: {
    critical: '紧急',
    high: '高',
    medium: '中',
    low: '低',
  },

  // ========== 旧版兼容 ==========
  filterStatus: '按状态筛选',
  allStatuses: '全部状态',
  statusPending: '待处理',
  statusReviewed: '已审核',
  statusResolved: '已解决',
  statusDismissed: '已驳回',
  noFlagged: '暂无待审核内容',
  noFlaggedDescription: '当前没有需要审核的内容。',
  flagDescription: '标记 "{title}" 供审核。请提供标记原因。',
  flagReason: '标记原因',
  moderationNotes: '审核备注',
  unknownReporter: '未知',
  moderate: '审核',
  flag: '标记',
  unflag: '取消标记',
  quickResolve: '快速解决',
  quickDismiss: '快速驳回',
  flagProblem: '标记题目',
  drawerTitle: '审核详情',
  moderationActions: '审核操作',
  success: '审核成功',
  error: '审核失败',
  loadError: '加载失败',
  selectAll: '全选',
  selectedCount: '已选择 {count} 项',
  batchResolve: '批量解决',
  batchDismiss: '批量驳回',
  notFound: '未找到审核项目',
  flagSuccess: '标记成功',
  flagError: '标记失败',
  unflagSuccess: '取消标记成功',
  unflagError: '取消标记失败',
} as const
