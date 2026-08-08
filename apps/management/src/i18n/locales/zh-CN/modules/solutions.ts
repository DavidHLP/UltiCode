export default {
  title: '题解管理',
  detailTitle: '题解详情',
  searchPlaceholder: '搜索题解...',

  // ========== 列定义 ==========
  columns: {
    id: 'ID',
    title: '标题',
    solution: '题解',
    problem: '题目',
    author: '作者',
    status: '状态',
    votes: '点赞数',
    views: '浏览数',
    created: '创建时间',
    createdAt: '创建时间',
    updatedAt: '更新时间',
    actions: '操作',
  },

  // ========== 状态 ==========
  status: {
    all: '全部状态',
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档',
    deleted: '已删除',
    flagged: '已标记',
    published: '已发布',
    unpublished: '未发布',
  },

  // ========== 操作 ==========
  actions: {
    view: '查看',
    viewDetails: '查看详情',
    edit: '编辑',
    delete: '删除',
    approve: '批准',
    reject: '拒绝',
    flag: '标记',
    unflag: '取消标记',
  },

  // ========== 筛选器 ==========
  filters: {
    flagStatus: '标记状态',
    all: '全部',
    flagged: '已标记',
    clean: '未标记',
    visibility: '可见性',
    published: '已发布',
    unpublished: '未发布',
  },

  // ========== 标签页 ==========
  tabs: {
    description: '描述',
    code: '代码',
  },

  // ========== 详情 ==========
  detail: {
    noCodeContent: '无代码内容',
    solutionFor: '{problem} 的题解',
    noDescriptionContent: '无描述内容',
    author: '作者',
    problemDifficulty: '题目难度',
    views: '浏览数',
    language: '语言',
    created: '创建时间',
    updated: '更新时间',
    lines: '行',
    copy: '复制',
    copied: '已复制',
    sourceCode: '源代码',
    deletedAt: '删除时间',
    deletedBy: '删除者',
  },

  // ========== 错误状态 ==========
  error: {
    loadingSolution: '加载题解失败',
    back: '返回',
    retry: '重试',
    solutionNotFound: '题解不存在',
    notFoundDescription: '找不到指定的题解',
    backToSolutions: '返回题解列表',
  },

  // ========== 删除对话框 ==========
  delete: {
    title: '删除题解',
    description: '确定要删除此题解吗？此操作不可撤销。',
    confirm: '确认删除',
    cancel: '取消',
  },

  // ========== 审批状态 ==========
  approval: {
    approved: '已通过',
    rejected: '已拒绝',
    pending: '审核中',
  },

  // ========== 标记对话框 ==========
  flag: {
    title: '标记题解',
    description: '请提供标记原因。',
    confirm: '确认标记',
    cancel: '取消',
    reasonLabel: '标记原因',
    reasonPlaceholder: '请输入标记原因...',
  },

  // ========== Toast 消息 ==========
  toast: {
    loadFailed: '加载题解失败',
    deleteSuccess: '题解删除成功',
    deleteFailed: '删除题解失败',
    deletedSuccessfully: '题解删除成功',
    failedToDelete: '删除题解失败',
    flaggedSuccessfully: '题解标记成功',
    failedToFlag: '标记题解失败',
    unflaggedSuccessfully: '取消标记成功',
    failedToUnflag: '取消标记失败',
    reasonRequired: '请提供标记原因',
  },

  // ========== 终端统计 ==========
  terminal: {
    total: '总计',
    flagged: '已标记',
    published: '已发布',
    loading: '加载中...',
    solutionManagement: '题解管理',
  },

  // ========== 空状态 ==========
  empty: {
    title: '暂无题解',
    description: '点击上方按钮创建第一个题解',
  },
} as const
