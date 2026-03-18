export default {
  title: '题目管理',
  createTitle: '创建题目',
  detailTitle: '题目详情',
  editTitle: '编辑题目',
  createProblem: '创建题目',
  importProblem: '导入题目',
  exportProblem: '导出题目',
  searchPlaceholder: '搜索题目...',

  // 列定义
  columns: {
    id: 'ID',
    title: '标题',
    difficulty: '难度',
    status: '状态',
    tags: '标签',
    acceptance: '通过率',
    submissions: '提交数',
    createdAt: '创建时间',
    updatedAt: '更新时间',
    isFlagged: '标记状态',
  },

  // 难度
  difficulty: {
    all: '全部难度',
    EASY: '简单',
    MEDIUM: '中等',
    HARD: '困难',
  },

  // 状态
  status: {
    all: '全部状态',
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档',
  },

  // 操作
  actions: {
    view: '查看',
    edit: '编辑',
    delete: '删除',
    duplicate: '复制',
    publish: '发布',
    archive: '归档',
    restore: '恢复',
    viewSubmissions: '查看提交',
    viewSolutions: '查看题解',
    flag: '标记',
    unflag: '取消标记',
    viewFlagInfo: '查看标记信息',
  },

  // 表单
  form: {
    title: '标题',
    titlePlaceholder: '请输入题目标题',
    slug: 'Slug',
    slugPlaceholder: 'two-sum',
    description: '题目描述',
    descriptionPlaceholder: '请输入题目描述...',
    difficulty: '难度',
    tags: '标签',
    tagsPlaceholder: '选择标签...',
    timeLimit: '时间限制 (ms)',
    memoryLimit: '内存限制 (MB)',
    score: '分数',
    isPremium: '高级题目',
    isPublished: '发布状态',
    hints: '提示',
    solutionTemplate: '代码模板',
    starterCode: '起始代码',
    testCases: '测试用例',
    examples: '示例',
    constraints: '约束条件',
  },

  // 编辑标签页
  tabs: {
    description: '描述',
    code: '代码',
    cases: '测试用例',
    settings: '设置',
    versions: '版本历史',
    audit: '审计日志',
  },

  // Toast 消息
  toast: {
    createSuccess: '题目创建成功',
    createFailed: '创建题目失败',
    updateSuccess: '题目更新成功',
    updateFailed: '更新题目失败',
    deleteSuccess: '题目删除成功',
    deleteFailed: '删除题目失败',
    publishSuccess: '题目发布成功',
    publishFailed: '发布题目失败',
    archiveSuccess: '题目归档成功',
    archiveFailed: '归档题目失败',
    restoreSuccess: '题目恢复成功',
    restoreFailed: '恢复题目失败',
    importSuccess: '题目导入成功',
    importFailed: '导入题目失败',
    exportSuccess: '题目导出成功',
    exportFailed: '导出题目失败',
    flagSuccess: '题目标记成功',
    flagFailed: '标记题目失败',
    unflagSuccess: '取消标记成功',
    unflagFailed: '取消标记失败',
    loadFailed: '加载题目失败',
    versionLoadFailed: '加载版本历史失败',
  },

  // 对话框
  dialogs: {
    deleteTitle: '确认删除',
    deleteDescription: '确定要删除题目 "{title}" 吗？此操作不可撤销。',
    publishTitle: '确认发布',
    publishDescription: '确定要发布题目 "{title}" 吗？',
    archiveTitle: '确认归档',
    archiveDescription: '确定要归档题目 "{title}" 吗？',
    flagTitle: '标记题目',
    flagDescription: '标记 "{title}" 供审核。请提供标记原因。',
  },

  // 标记信息
  flagInfo: {
    title: '标记信息',
    flaggedBy: '标记人',
    flaggedAt: '标记时间',
    reason: '标记原因',
    status: '审核状态',
    notes: '审核备注',
    noFlagInfo: '该题目未被标记',
  },

  // 版本历史
  versionHistory: {
    title: '版本历史',
    noHistory: '暂无版本历史',
    version: '版本',
    author: '作者',
    changes: '更改内容',
    restore: '恢复到此版本',
    viewDiff: '查看差异',
  },

  // 空状态
  empty: {
    title: '暂无题目',
    description: '点击上方按钮创建第一个题目',
  },

  clearSelection: '清除选择',
  bulkDeleteConfirm: '确定要删除选中的 {count} 个题目吗？',
} as const
