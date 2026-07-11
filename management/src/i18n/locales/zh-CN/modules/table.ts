const tableTranslations = {
  // 表格工具栏
  selectAll: '全选',
  customizeColumns: '自定义列',
  columns: '列',
  rowsPerPage: '每页行数',
  page: '页',
  of: '/',
  rowsSelected: '行已选择',
  goToFirstPage: '跳转到第一页',
  goToPreviousPage: '上一页',
  goToNextPage: '下一页',
  goToLastPage: '跳转到最后一页',

  // 多选相关
  selected: '{count} 项已选择',

  // 空状态
  emptyTitle: '没有数据',
  emptyDescription: '当前没有可显示的数据',

  // 列名映射 - C8 在 DataTable 边界把 snake_case 规范化为 camelCase
  // (resolveColumnName/toCamelCase),因此此处只保留 camelCase 键。
  // 新增列 id 必须匹配 /^[a-z][a-zA-Z0-9]*$/。
  columnNames: {
    username: '用户名',
    role: '角色',
    status: '状态',
    actions: '操作',
    id: 'ID',
    name: '名称',
    title: '标题',
    description: '描述',
    type: '类型',
    created: '创建时间',
    updated: '更新时间',
    email: '邮箱',
    submissions: '提交数',
    solutions: '题解数',
    difficulty: '难度',
    views: '浏览数',
    tags: '标签',
    category: '分类',
    visibility: '可见性',
    author: '作者',
    creator: '创建者',
    duration: '时长',
    participants: '参与者',

    // 审核列
    action: '操作',
    performer: '执行者',
    user: '目标用户',
    entity: '实体',
    priority: '优先级',
    reporter: '举报者',
    reason: '原因',
    appellant: '申诉人',
    response: '回复',

    // 论坛/评论列
    isFlagged: '已标记',
    content: '内容',
    stats: '统计',
    createdAt: '创建时间',

    // 题单列
    isFeatured: '推荐',
    isPublic: '公开',
    problemCount: '题目数',
    bannerOrder: '横幅排序',
    updatedAt: '更新时间',

    // 补充缺失的驼峰键
    lastLoginAt: '最后登录',
    joinedAt: '注册时间',
    submissionCount: '提交数',
    isPublished: '发布状态',
    assignedTo: '分配给',
    entityType: '实体类型',
    primaryCategory: '类别',
    resolution: '处理结果',
    reportCount: '举报数',
    authorName: '作者名称',
    addedAt: '添加时间',
    startTime: '开始时间',
    participantCount: '参与人数',
    contestType: '比赛类型',
    sortOrder: '排序',
    runtime: '运行时间',
    codeLength: '代码长度',
    language: '语言',
    problemTitle: '题目标题',
    memory: '内存',
    baseScorePerProblem: '基础分',
    timeBonusPerMinute: '时间奖励',
    wrongAnswerPenalty: '错误惩罚',
    firstSolveBonus: '首解奖励',
    entityId: '实体 ID',
    ipAddress: 'IP 地址',
    queueId: '队列 ID',
    reviewer: '审核人',
  },
} as const

export type TableColumnName = keyof typeof tableTranslations.columnNames
export default tableTranslations