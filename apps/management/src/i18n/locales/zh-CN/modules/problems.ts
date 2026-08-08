export default {
  title: '题目管理',
  createTitle: '创建题目',
  detailTitle: '题目详情',
  editTitle: '编辑题目',
  createProblem: '创建题目',
  importProblem: '导入题目',
  exportProblem: '导出题目',
  searchPlaceholder: '搜索题目...',
  addProblem: '添加题目',

  // 列定义
  columns: {
    id: 'ID',
    title: '标题',
    problem: '题目',
    difficulty: '难度',
    status: '状态',
    tags: '标签',
    acceptance: '通过率',
    submissions: '提交数',
    createdAt: '创建时间',
    updatedAt: '更新时间',
    isFlagged: '标记状态',
    published: '发布状态',
    flagged: '标记',
  },

  // 难度
  difficulty: {
    all: '全部难度',
    EASY: '简单',
    MEDIUM: '中等',
    HARD: '困难',
    easy: '简单',
    medium: '中等',
    hard: '困难',
  },

  // 状态
  status: {
    all: '全部状态',
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档',
    TODO: '待做',
    ATTEMPTED: '已尝试',
    SOLVED: '已解决',
    todo: '待做',
    attempted: '已尝试',
    solved: '已解决',
  },

  // 发布状态
  published: {
    published: '已发布',
    draft: '草稿',
    deleted: '已删除',
  },

  // 徽章
  badges: {
    premium: '高级',
  },

  // 操作
  actions: {
    view: '查看',
    edit: '编辑',
    delete: '删除',
    duplicate: '复制',
    publish: '发布',
    unpublish: '取消发布',
    archive: '归档',
    restore: '恢复',
    viewSubmissions: '查看提交',
    viewSolutions: '查看题解',
    flag: '标记',
    unflag: '取消标记',
    viewFlagInfo: '查看标记信息',
    export: '导出',
  },

  // 统计
  stats: {
    problemManagement: '题目管理',
    total: '总计',
    published: '已发布',
    draft: '草稿',
    flagged: '被标记',
  },

  // 筛选器
  filters: {
    allDifficulty: '全部难度',
    allStatus: '全部状态',
    allPublished: '全部发布状态',
    published: '已发布',
    unpublished: '未发布',
  },

  // 排序
  sort: {
    title: '排序方式',
    default: '默认排序',
    titleAsc: '按标题升序',
    difficultyAsc: '按难度升序',
    createdDesc: '按创建时间降序',
    updatedDesc: '按更新时间降序',
    submissionsDesc: '按提交数降序',
  },

  // 导出
  export: {
    title: '导出',
    json: '导出为 JSON',
    csv: '导出为 CSV',
    success: '题目导出成功',
    failed: '导出题目失败',
  },

  // 导入
  import: {
    title: '导入',
    description: '从 JSON 或 CSV 文件导入题目',
    dropFile: '拖放文件到此处，或点击浏览',
    supportedFormats: '支持格式：JSON、CSV',
    browse: '浏览文件',
    clear: '清除',
    invalidFile: '文件格式无效，请上传 JSON 或 CSV 文件',
    conflictStrategy: '冲突处理策略',
    strategies: {
      skip: '跳过',
      update: '更新现有题目',
      createNew: '创建新题目',
    },
    strategyDescriptions: {
      skip: '跳过已存在的题目，不做任何更改',
      update: '更新现有题目的信息',
      create_new: '将冲突的题目作为新题目创建',
    },
    importing: '导入中...',
    import: '导入',
    created: '已创建',
    updated: '已更新',
    skipped: '已跳过',
    success: '题目导入成功',
    failed: '导入题目失败',
    partialSuccess: '成功导入 {success} / {total} 个题目',
    someErrors: '部分题目导入失败，请查看下方错误详情',
    error: '导入失败，请重试',
    errors: '错误详情',
  },

  // 批量操作
  bulk: {
    noSelection: '请选择要操作的题目',
    success: '成功{action}{count}个题目',
    failed: '{action}{count}个题目失败',
    partial: '成功{success}个，失败{failed}个',
    publish: '发布',
    unpublish: '取消发布',
    delete: '删除',
    restore: '恢复',
    action: '批量操作',
    publishTitle: '确认批量发布',
    publishDescription: '确定要发布选中的 {count} 个题目吗？',
    confirmPublish: '确认发布',
    unpublishTitle: '确认批量取消发布',
    unpublishDescription: '确定要取消发布选中的 {count} 个题目吗？',
    confirmUnpublish: '确认取消发布',
    deleteTitle: '确认批量删除',
    deleteDescription: '确定要删除选中的 {count} 个题目吗？此操作不可撤销。',
    confirmDelete: '确认删除',
    restoreTitle: '确认批量恢复',
    restoreDescription: '确定要恢复选中的 {count} 个题目吗？',
    confirmRestore: '确认恢复',
    warning: '注意',
    warningDescription: '此操作将影响选中的所有题目',
  },

  // 表单
  form: {
    title: '标题',
    titlePlaceholder: '请输入题目标题',
    slug: 'Slug',
    slugPlaceholder: 'two-sum',
    description: '题目描述',
    descriptionPlaceholder: '请输入题目描述...',
    summary: '摘要',
    summaryPlaceholder: '请输入题目摘要...',
    contentPlaceholder: '请输入题目内容...',
    fullContent: '完整内容',
    difficulty: '难度',
    tags: '标签',
    tagsPlaceholder: '选择标签...',
    addTagPlaceholder: '添加标签...',
    timeLimit: '时间限制 (ms)',
    memoryLimit: '内存限制 (MB)',
    score: '分数',
    isPremium: '高级题目',
    isPublished: '发布状态',
    hints: '提示',
    addHintPlaceholder: '添加提示...',
    addHint: '添加提示',
    noHints: '暂无提示',
    solutionTemplate: '代码模板',
    starterCode: '起始代码',
    testCases: '测试用例',
    examples: '示例',
    constraints: '约束条件',
    publishing: '发布设置',
    status: '状态',
    premium: '高级',
    premiumDescription: '仅限高级用户',
    published: '已发布',
    publishedDescription: '对所有用户可见',
    saving: '保存中...',
    updateProblem: '更新题目',
    createProblem: '创建题目',
    taxonomy: '分类',
    languages: '编程语言',
    all: '全部',
    addLanguagePlaceholder: '添加编程语言...',
    add: '添加',
    details: {
      title: '基本信息',
      description: '填写题目的基本信息',
    },
    testCasesSection: {
      title: '测试用例',
      description: '添加示例测试用例帮助用户理解题目',
    },
    additionalInfo: {
      title: '附加信息',
    },
    constraintsSection: {
      title: '约束条件',
      placeholder: '例如: 1 <= nums.length <= 10^4',
    },
    validation: {
      slugRequired: 'Slug 是必填项',
      slugInvalid: 'Slug 只能包含小写字母、数字和连字符',
      titleRequired: '标题是必填项',
      examplesRequired: '至少需要一个测试用例',
      inputRequired: '输入是必填项',
      outputRequired: '输出是必填项',
    },
  },

  // 预览
  preview: {
    untitled: '未命名',
  },

  // 描述表单
  descriptionForm: {
    basicInfo: '基本信息',
    problemDescription: '题目描述',
    problemDescriptionSubtitle: '填写题目的基本信息和描述',
    titlePlaceholder: '请输入题目标题',
    slugPlaceholder: '例如: two-sum',
    summaryPlaceholder: '请输入简短的题目摘要...',
    contentPlaceholder: '请输入完整的题目描述内容...',
    publishing: '发布设置',
    premium: '高级题目',
    premiumDescription: '仅限高级用户访问',
    published: '发布状态',
    publishedDescription: '对所有用户可见',
    saving: '保存中...',
    updateDescription: '更新描述',
    saveDescription: '保存描述',
    validation: {
      slugRequired: 'Slug 是必填项',
      slugInvalid: 'Slug 只能包含小写字母、数字和连字符',
      titleRequired: '标题是必填项',
    },
    examples: '示例',
    examplesSection: {
      title: '示例',
      add: '添加示例',
      empty: '暂无示例',
      input: '输入',
      output: '输出',
      explanation: '解释（可选）',
    },
    constraints: '约束条件',
    constraintsSection: {
      title: '约束条件',
      add: '添加约束',
      empty: '暂无约束条件',
      emptyDescription:
        '暂无约束条件。约束条件用于描述题目的限制和规则（例如：数组长度、数值范围）。',
      addNew: '添加新约束',
      placeholder: '例如: 1 <= nums.length <= 10^5',
    },
    hints: '提示',
    hintsSection: {
      title: '提示',
      add: '添加提示',
      empty: '暂无提示',
    },
    tags: '标签',
    tagsSection: {
      title: '标签',
    },
    languages: '编程语言',
    languagesDescription: '选择此题支持的编程语言。',
    noLanguagesSelected: '未选择语言',
    preview: {
      title: '实时预览',
    },
    section: {
      basicInfo: '基本信息',
      problemDescription: '题目描述',
      examples: '示例',
      constraints: '约束条件',
      hints: '提示',
      tags: '标签',
    },
  },

  // 代码表单
  codeForm: {
    addLanguages: '添加编程语言',
    quickAdd: '快速添加',
    customLanguagePlaceholder: '输入自定义语言...',
    add: '添加',
    lines: '行',
    starterCodeTemplate: '起始代码模板',
    noLanguages: '尚未添加语言',
    noLanguagesDescription: '点击上方的语言按钮快速添加，或输入自定义语言',
    configuration: '配置信息',
    languages: '语言数量',
    allLanguages: '支持所有语言',
    selectedLanguages: '已选择特定语言',
    saving: '保存中...',
    saveChanges: '保存更改',
  },

  // 测试用例表单
  casesForm: {
    testCasesSection: '测试用例',
    constraintsAndHints: '约束条件与提示',
    constraints: '约束条件',
    constraintPlaceholder: '例如: 1 <= n <= 10^5',
    add: '添加',
    noConstraints: '暂无约束条件',
    hints: '提示',
    addHint: '添加提示',
    noHints: '暂无提示',
    tags: '标签',
    addTag: '添加标签',
    noTags: '暂无标签',
    configurationSummary: '配置摘要',
    summary: {
      testCases: '测试用例',
      constraints: '约束条件',
      hints: '提示',
      tags: '标签',
    },
    saving: '保存中...',
    saveChanges: '保存更改',
    validation: {
      examplesRequired: '至少需要一个测试用例',
      inputRequired: '输入是必填项',
      outputRequired: '输出是必填项',
    },
  },

  // 编辑标签页
  tabs: {
    description: '描述',
    code: '代码',
    cases: '测试用例',
    testCases: '测试用例',
    settings: '设置',
    versions: '版本历史',
    audit: '审计日志',
  },

  // 创建页面
  create: {
    title: '创建题目',
  },

  // 编辑页面
  edit: {
    loading: '加载中...',
    descriptionSubtitle: '编辑题目描述信息',
    codeSubtitle: '配置支持的编程语言',
    testCasesSubtitle: '管理测试用例和约束条件',
    action: '操作',
    mode: '模式',
    newProblem: '新建题目',
    problemCreation: '创建题目',
    section: '部分',
    description: '题目描述',
    testCases: '测试用例',
    code: '评测代码',
    problemEditor: '题目编辑器',
    testCaseEditor: '测试用例编辑器',
    languageConfig: '语言配置',
  },

  // 查看页面
  view: {
    loading: '加载中...',
    notFound: '题目不存在',
    notFoundDescription: '找不到指定的题目',
    backToProblems: '返回题目列表',
  },

  // 显示组件
  display: {
    id: 'ID',
    created: '创建时间',
    updated: '更新时间',
    published: '发布时间',
    metadata: '元数据',
    tags: '标签',
    hints: '提示',
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
    unpublishSuccess: '取消发布成功',
    unpublishFailed: '取消发布失败',
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
  dialog: {
    deleteTitle: '确认删除',
    deleteDescription: '确定要删除题目 "{title}" 吗？此操作不可撤销。',
    delete: {
      title: '确认删除',
      description: '确定要删除题目 "{title}" 吗？此操作不可撤销。',
      confirm: '确认删除',
      thisProblem: '此题目',
    },
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
    noReason: '未提供原因',
    reportedBy: '举报人',
    reportedAt: '举报时间',
    reviewedBy: '审核人',
    reviewedAt: '审核时间',
  },

  // 版本历史
  versionHistory: {
    // 已有键（保留）
    title: '版本历史',
    noHistory: '暂无版本历史',
    version: '版本',
    author: '作者',
    changes: '更改内容',
    restore: '恢复到此版本',
    restoreSuccess: '版本恢复成功',
    viewDiff: '查看差异',
    // 新增键
    description: '查看和管理题目的历史版本',
    compareWith: '对比版本 {version}',
    noVersions: '暂无版本记录',
    createInitial: '创建初始版本',
    by: '由',
    versionDetails: '版本详情',
    compareVersions: '版本对比',
    noChanges: '无变更',
    oldValue: '旧值',
    newValue: '新值',
    rollbackTitle: '回滚到版本 {version}',
    rollbackConfirm: '确定要回滚到版本 {version} 吗？此操作将创建一个新版本记录。',
    rollbackReasonPlaceholder: '请输入回滚原因（可选）',
    rollbackButton: '确认回滚',
    loadError: '加载版本历史失败',
    loadDetailError: '加载版本详情失败',
    compareError: '版本对比失败',
    rollbackError: '回滚失败',
    rollbackSuccess: '已成功回滚到版本 {version}',
    createInitialSuccess: '初始版本创建成功',
    alreadyHasVersions: '该题目已有版本记录',
    createInitialError: '创建初始版本失败',
    action: {
      CREATE: '创建',
      UPDATE: '更新',
      ROLLBACK: '回滚',
    },
  },

  // 空状态
  empty: {
    title: '暂无题目',
    description: '点击上方按钮创建第一个题目',
  },

  // 代码显示组件
  codeDisplay: {
    noCode: '暂无代码模板',
    noCodeDescription: '此题目尚未配置任何语言模板。编辑题目以添加不同编程语言的起始代码。',
    noCodeForLanguage: '未配置 {language} 的起始代码',
    lines: '行',
    copy: '复制',
    copied: '已复制',
    languagesConfigured: '已配置 {count} 种语言',
    selectLanguage: '选择语言查看代码',
  },

  // 描述显示组件
  descriptionDisplay: {
    example: '示例',
    input: '输入',
    expectedOutput: '预期输出',
    explanation: '解释',
    constraints: '约束条件',
    hints: '提示',
    codeCopied: '代码已复制',
  },

  // 测试用例显示组件
  casesDisplay: {
    examples: '测试用例',
    input: '输入',
    output: '输出',
    explanation: '解释',
    noCases: '暂无测试用例',
    noCasesDescription: '此题目尚未配置任何测试用例。编辑题目以添加示例测试用例。',
  },

  // 标签选择器
  tagsSelector: {
    selected: '已选择',
    selectedCount: '已选择 {count} 个',
    removeTag: '移除标签 {tag}',
    noTagsSelected: '尚未选择标签',
    searchPlaceholder: '搜索标签...',
    loading: '加载中...',
    available: '可用标签',
    totalCount: '共 {count} 个',
    noResults: '未找到匹配的标签',
    noTagsAvailable: '暂无可用标签',
  },

  clearSelection: '清除选择',
  bulkDeleteConfirm: '确定要删除选中的 {count} 个题目吗？',

  // Markdown 编辑器
  markdownEditor: {
    bold: '粗体',
    italic: '斜体',
    inlineCode: '行内代码',
    codeBlock: '代码块',
    insertLink: '插入链接',
    insertImage: '插入图片',
    toggleFullscreen: '切换全屏',
    placeholder: '请输入 Markdown 内容...',
  },

  // 测试用例编辑器
  testCasesEditor: {
    example: '示例 {number}',
    addExample: '添加示例',
    input: '输入',
    inputPlaceholder: '请输入测试输入...',
    output: '输出',
    outputPlaceholder: '请输入预期输出...',
    explanationOptional: '解释（可选）',
    explanationPlaceholder: '请输入解释...',
    noCases: '暂无测试用例',
  },

  // 批量编辑弹窗 (BulkEditDialog)
  bulkEdit: {
    title: '批量编辑题目',
    description: '对选中的 {count} 道题目应用更改。',
    difficulty: '难度',
    difficultyPlaceholder: '选择难度',
    premium: '高级',
    premiumHint: '将选中题目标记为高级内容',
    editing: '保存中...',
    edit: '应用更改',
    noChanges: '请至少选择一项要编辑的字段',
    success: '成功更新 {count} 道题目',
    failure: '更新选中题目失败',
    partial: '已更新 {success} 道题目,{failed} 道失败',
    error: '更新题目时发生错误',
  },
} as const
