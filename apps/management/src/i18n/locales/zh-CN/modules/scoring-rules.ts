export default {
  title: '计分规则',
  createRule: '创建规则',
  searchPlaceholder: '搜索规则...',
  showInactive: '显示已禁用',
  stats: {
    total: '总计',
    active: '已启用',
    defaults: '默认',
    inactive: '已禁用',
    scoringManagement: '计分管理',
  },
  loadError: '加载计分规则失败',
  emptyTitle: '未找到计分规则',
  emptyDescription: '创建新的计分规则以开始使用。',

  // 列
  columns: {
    name: '名称',
    baseScore: '基础分数',
    timeBonus: '时间奖励',
    wrongPenalty: '错误罚分',
    firstSolveBonus: '首杀奖励',
    actions: '操作',
  },

  // 标签
  badges: {
    default: '默认',
    inactive: '已禁用',
  },

  // 操作
  actions: {
    setDefault: '设为默认',
    edit: '编辑',
    delete: '删除',
    noActionsAvailable: '无可用操作',
  },

  // 表单
  form: {
    name: '名称',
    namePlaceholder: '请输入规则名称',
    nameRequired: '名称为必填项',
    nameTooLong: '名称不能超过100个字符',
    description: '描述',
    descriptionPlaceholder: '可选描述...',
    descriptionTooLong: '描述不能超过500个字符',
    baseScorePerProblem: '每题基础分',
    timeBonusPerMinute: '每分钟时间奖励',
    wrongAnswerPenalty: '错误答案罚分',
    timeLimitPenalty: '超时罚分',
    firstSolveBonus: '首杀奖励',
    fullScoreBonus: '满分奖励',
    isDefault: '设为默认',
    isDefaultDescription: '此规则将默认用于新比赛',
    mustBeNonNegative: '值必须为非负数',
    createTitle: '创建计分规则',
    createDescription: '为比赛定义新的计分规则。',
    editTitle: '编辑计分规则',
    editDescription: '修改计分规则参数。',
    createRule: '创建规则',
    saveChanges: '保存更改',
  },

  // 删除对话框
  delete: {
    title: '删除计分规则',
    description: '确定要删除计分规则 "{name}" 吗？此操作不可撤销。',
    thisRule: '此计分规则',
    confirm: '删除',
  },

  // Toast 消息
  toast: {
    createdSuccessfully: '计分规则创建成功',
    failedToCreate: '创建计分规则失败',
    updatedSuccessfully: '计分规则更新成功',
    failedToUpdate: '更新计分规则失败',
    deletedSuccessfully: '计分规则删除成功',
    failedToDelete: '删除计分规则失败',
    setDefaultSuccess: '默认计分规则更新成功',
    failedToSetDefault: '设置默认计分规则失败',
  },
} as const
