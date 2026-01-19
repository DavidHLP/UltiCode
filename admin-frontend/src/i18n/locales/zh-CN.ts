export default {
  // 通用操作和标签
  common: {
    // 操作
    save: '保存',
    cancel: '取消',
    delete: '删除',
    edit: '编辑',
    add: '添加',
    create: '创建',
    update: '更新',
    view: '查看',
    remove: '移除',
    confirm: '确认',
    submit: '提交',
    back: '返回',
    next: '下一步',
    previous: '上一步',
    close: '关闭',
    open: '打开',
    copy: '复制',
    download: '下载',
    upload: '上传',

    // 状态
    loading: '加载中...',
    noData: '暂无数据',
    never: '从未',
    yes: '是',
    no: '否',
    all: '全部',
    none: '无',
    any: '任意',

    // 标签
    actions: '操作',
    status: '状态',
    details: '详情',
    search: '搜索',
    filter: '筛选',
    sort: '排序',
    export: '导出',
    refresh: '刷新',
    retry: '重试',
    select: '选择',
    clear: '清除',
    name: '名称',
    title: '标题',
    description: '描述',
    type: '类型',
    created: '创建时间',
    updated: '更新时间',
    id: 'ID',

    // 时间
    today: '今天',
    yesterday: '昨天',
    thisWeek: '本周',
    lastWeek: '上周',

    // Additional labels
    reportedBy: '举报人',
    reportedAt: '举报时间',
    submissions: '提交数',
    solutions: '题解数',
    page: '页',
    saving: '保存中...',
    premium: '高级',
    unpublished: '未发布',
  },

  // 导航
  nav: {
    dashboard: '仪表板',
    users: '用户管理',
    problems: '题目管理',
    contests: '比赛管理',
    forum: '论坛管理',
    settings: '系统设置',
    problemLists: '题目列表',
    tags: '标签管理',
    solutions: '题解管理',
    comments: '评论管理',
    notifications: '通知管理',
    auditLogs: '审计日志',
    moderation: '内容审核',
    getHelp: '获取帮助',
    search: '搜索',
  },

  // 内容审核
  moderation: {
    title: '审核队列',
    description: '查看和管理被标记的题目',
    filterStatus: '按状态筛选',
    allStatuses: '全部状态',
    statusPending: '待处理',
    statusReviewed: '已审核',
    statusResolved: '已解决',
    statusDismissed: '已驳回',
    noFlagged: '无被标记的题目',
    noFlaggedDescription: '目前没有需要审核的被标记题目。',
    flagReason: '标记原因',
    moderationNotes: '审核备注',
    moderate: '审核',
    moderateTitle: '审核题目',
    status: '状态',
    notes: '备注',
    notesPlaceholder: '添加审核备注（可选）...',
    success: '题目审核成功',
    error: '审核题目失败',
    loadError: '加载被标记题目失败',
    reasonPrompt: '请输入标记此题目的原因：',
    flagSuccess: '题目标记成功',
    unflagSuccess: '题目取消标记成功',
    flag: '标记',
    unflag: '取消标记',
  },

  // 仪表板
  dashboard: {
    title: '仪表板',
    welcome: '欢迎回来',
    loading: '加载仪表板...',

    // 统计数据
    stats: {
      totalUsers: '总用户数',
      activeToday: '今日活跃',
      activeThisWeek: '本周活跃',
      totalProblems: '总题目数',
      published: '已发布',
      unpublished: '未发布',
      activeContests: '活跃比赛',
      upcoming: '即将开始',
      finished: '已结束',
      flaggedContent: '被标记内容',
      actionNeeded: '需要处理',
      allClear: '一切正常',
      pendingModeration: '待审核',
    },

    // 图表
    chart: {
      userRegistrationTrend: '用户注册趋势',
      dailyRegistrations: '过去30天的每日用户注册量',
    },

    // 最近活动
    recentActivity: {
      title: '最近活动',
      description: '平台上的最新管理员操作',
      noActivity: '暂无最近活动',
      target: '目标',
    },

    // 时间之前
    timeAgo: {
      justNow: '刚刚',
      minuteAgo: '{count} 分钟前',
      minuteAgo_plural: '{count} 分钟前',
      hourAgo: '{count} 小时前',
      hourAgo_plural: '{count} 小时前',
      dayAgo: '{count} 天前',
      dayAgo_plural: '{count} 天前',
    },
  },

  // 用户
  users: {
    title: '用户',
    listTitle: '用户管理',
    addUser: '添加用户',
    searchPlaceholder: '搜索用户...',
    selected: '已选择 {count} 位用户',
    selected_one: '已选择 {count} 位用户',
    clearSelection: '清除选择',

    // 筛选器
    filters: {
      allRoles: '所有角色',
      allStatus: '所有状态',
      role: {
        USER: '普通用户',
        MODERATOR: '版主',
        ADMIN: '管理员',
        SUPER_ADMIN: '超级管理员',
      },
      status: {
        active: '活跃',
        inactive: '未激活',
        banned: '已封禁',
      },
    },

    // 表格列
    columns: {
      user: '用户',
      role: '角色',
      joined: '加入时间',
      lastLogin: '最后登录',
    },

    // 批量操作
    bulkActions: {
      bulkBan: '批量封禁',
      bulkUnban: '批量解封',
      bulkDelete: '批量删除',
    },

    // 对话框
    deleteConfirm: '确定要删除 {count} 位用户吗？此操作不可撤销。',
    deleteConfirm_one: '确定要删除 {count} 位用户吗？此操作不可撤销。',
    banReasonPrompt: '输入批量封禁原因：',

    // 状态徽章
    status: {
      banned: '已封禁',
      active: '活跃',
      inactive: '未激活',
    },

    // 提示消息
    toast: {
      unbanFailed: '解封用户失败',
      bulkBanFailed: '批量封禁失败',
      bulkUnbanFailed: '批量解封失败',
      bulkDeleteFailed: '批量删除失败',
    },

    // 表单标签
    form: {
      username: '用户名',
      email: '邮箱',
      displayName: '显示名称',
      role: '角色',
      password: '密码',
      confirmPassword: '确认密码',
      isActive: '活跃状态',
      isBanned: '封禁状态',
      banReason: '封禁原因',
      banReasonPlaceholder: '违反条款...',
      banExpiresAt: '封禁到期时间',
      avatar: '头像 URL',
    },

    // 操作
    actions: {
      viewDetails: '查看详情',
      editProfile: '编辑资料',
      resetPassword: '重置密码',
      resetPasswordAction: '重置密码',
      resetting: '重置中...',
      resetPasswordDescription: '为 {username} 设置新密码。',
      banUser: '封禁用户',
      banUserDescription: '请提供封禁 {username} 的原因。',
      confirmBan: '确认封禁',
      banning: '封禁中...',
      cancel: '取消',
      thisUser: '该用户',
      unbanUser: '解封用户',
      deleteUser: '删除用户',
    },
  },

  // 题目
  problems: {
    title: '题目',
    listTitle: '题目管理',
    addProblem: '添加题目',
    searchPlaceholder: '搜索题目...',

    // 创建
    create: {
      title: '创建题目',
      description: '为平台创建新题目',
    },

    // 筛选器
    filters: {
      difficulty: '难度',
      allLevels: '全部难度',
      status: '状态',
      allStatus: '全部状态',
      visibility: '可见性',
      any: '任意',
      published: '已发布',
      unpublished: '草稿',
    },

    // 难度级别
    difficulty: {
      EASY: '简单',
      MEDIUM: '中等',
      HARD: '困难',
    },

    // 状态
    status: {
      todo: '未开始',
      attempted: '已尝试',
      solved: '已解决',
    },

    // 发布状态
    published: {
      published: '已发布',
      draft: '草稿',
      deleted: '已删除',
    },

    // 表格列
    columns: {
      id: 'ID',
      problem: '题目',
      difficulty: '难度',
      status: '状态',
      published: '发布状态',
      flagged: '已标记',
      submissions: '提交数',
      tags: '标签',
      created: '创建时间',
    },

    // 标签页
    tabs: {
      description: '题目描述',
      code: '代码模板',
      testCases: '测试用例',
      overview: '概览',
      audit: '审计日志',
    },

    // 操作
    actions: {
      view: '查看',
      edit: '编辑',
      publish: '发布',
      unpublish: '取消发布',
      delete: '删除',
      viewDescription: '题目描述',
      viewCode: '代码模板',
      viewCases: '测试用例',
    },

    // 提示消息
    toast: {
      publishSuccess: '题目发布成功',
      publishFailed: '题目发布失败',
      unpublishSuccess: '题目已取消发布',
      unpublishFailed: '取消发布失败',
    },

    // 代码模板
    code: {
      template: '代码模板',
      language: '编程语言',
      addTemplate: '添加模板',
    },

    // 测试用例
    cases: {
      testCases: '测试用例',
      addCase: '添加用例',
      input: '输入',
      output: '输出',
      explanation: '说明',
      sample: '示例',
      isSample: '是示例用例',
      isHidden: '隐藏',
    },

    // 编辑视图
    edit: {
      description: '题目描述',
      code: '代码模板',
      testCases: '测试用例',
    },

    // 查看视图
    view: {
      errorLoading: '加载题目失败',
      notFound: '题目不存在',
      backToProblems: '返回题目列表',
    },

    // 显示组件
    display: {
      metadata: '元数据',
      id: 'ID',
      created: '创建时间',
      updated: '更新时间',
      published: '发布时间',
      tags: '标签',
      hints: '提示',
    },

    // 代码表单
    codeForm: {
      addLanguages: '添加编程语言',
      quickAdd: '快速添加',
      customLanguagePlaceholder: '输入自定义语言...',
      add: '添加',
      languagesConfigured: '已配置 {count} 种语言',
      allLanguages: '所有语言（无过滤）',
      selectedLanguages: '仅显示选定语言的题目',
      configuration: '配置',
      languages: '语言',
      saving: '保存中...',
      saveChanges: '保存更改',
      noLanguages: '未添加语言',
      noLanguagesDescription: '添加编程语言以配置起始代码模板',
      starterCodeTemplate: '起始代码模板',
      lines: '行',
      selectLanguage: '选择一种语言查看其起始代码',
    },

    // 代码显示
    codeDisplay: {
      noCode: '未配置代码',
      noCodeDescription: '此题目尚未配置起始代码',
      copy: '复制',
      copied: '已复制！',
      lines: '行',
      languagesConfigured: '已配置 {count} 种语言',
      selectLanguage: '选择一种语言查看其起始代码',
      noCodeForLanguage: '{language} 未配置起始代码',
    },

    // 测试用例表单
    casesForm: {
      testCasesSection: '测试用例',
      constraintsAndHints: '限制条件与提示',
      constraints: '限制条件',
      hints: '提示',
      tags: '标签',
      configurationSummary: '配置概要',
      noConstraints: '未添加限制条件',
      noHints: '未添加提示',
      noTags: '未添加标签',
      constraintPlaceholder: '例如：1 <= nums.length <= 10^4',
      add: '添加',
      addHint: '添加提示...',
      addTag: '添加标签...',
      saving: '保存中...',
      saveChanges: '保存更改',
      summary: {
        testCases: '测试用例',
        constraints: '限制条件',
        hints: '提示',
        tags: '标签',
      },
      validation: {
        examplesRequired: '至少需要一个示例',
        inputRequired: '输入不能为空',
        outputRequired: '输出不能为空',
      },
    },

    // 测试用例显示
    casesDisplay: {
      noCases: '未添加测试用例',
      noCasesDescription: '添加示例输入和输出以帮助用户理解题目',
      examples: '示例',
      input: '输入',
      output: '输出',
      explanation: '说明',
    },

    // 描述表单
    descriptionForm: {
      problemDescription: '题目描述',
      problemDescriptionSubtitle: '题目的基本信息和内容',
      titlePlaceholder: '例如：两数之和',
      slugPlaceholder: '例如：two-sum',
      summaryPlaceholder: '列表中显示的简短描述...',
      contentPlaceholder: '使用 Markdown 编写完整的题目描述...',
      publishing: '发布设置',
      premium: '高级会员',
      premiumDescription: '仅限高级用户访问',
      published: '已发布',
      publishedDescription: '对所有用户可见',
      saving: '保存中...',
      updateDescription: '更新描述',
      saveDescription: '保存描述',
      validation: {
        slugRequired: 'URL 标识不能为空',
        slugInvalid: 'URL 标识只能包含小写字母、数字和连字符',
        titleRequired: '题目标题不能为空',
      },
    },

    // 测试用例编辑器
    testCasesEditor: {
      addExample: '添加示例',
      example: '示例 {number}',
      input: '输入',
      output: '输出',
      explanationOptional: '说明（可选）',
      inputPlaceholder: '输入测试用例输入...',
      outputPlaceholder: '输入预期输出...',
      explanationPlaceholder: '此示例的说明...',
      noCases: '暂无测试用例。点击"添加示例"创建一个。',
    },

    // Markdown 编辑器
    markdownEditor: {
      placeholder: '在此输入 Markdown...',
      bold: '粗体 (Ctrl+B)',
      italic: '斜体 (Ctrl+I)',
      inlineCode: '行内代码',
      codeBlock: '代码块',
      insertLink: '插入链接',
      insertImage: '插入图片',
      toggleFullscreen: '切换全屏 (Esc)',
    },

    // 题目表单（主创建/编辑表单）
    form: {
      // 卡片标题
      details: {
        title: '题目详情',
        description: '题目的基本信息',
      },
      testCases: {
        title: '测试用例',
        description: '定义示例输入和输出',
      },
      additionalInfo: {
        title: '附加信息',
        description: '添加限制条件、提示和其他元数据',
      },
      // 标签
      title: '题目标题',
      titlePlaceholder: '输入题目标题',
      slug: 'URL 标识',
      slugPlaceholder: 'problem-slug',
      summary: '简介',
      summaryPlaceholder: '题目的简要描述',
      fullContent: '完整内容',
      contentPlaceholder: '使用 Markdown 编写详细的题目描述',
      difficulty: '难度',
      status: '状态',
      constraints: {
        title: '限制条件',
        placeholder: '添加限制条件...',
      },
      hints: {
        title: '提示',
        placeholder: '添加提示...',
      },
      languages: '编程语言',
      all: '全部',
      tags: '标签',
      isPremium: '高级',
      isPremiumDescription: '仅限高级用户访问',
      isPublished: '已发布',
      isPublishedDescription: '对所有用户可见',
      taxonomy: '分类',
      // 操作
      add: '添加',
      createProblem: '创建题目',
      updateProblem: '更新题目',
      saving: '保存中...',
      // 验证
      validation: {
        slugRequired: 'URL 标识不能为空',
        slugInvalid: 'URL 标识只能包含小写字母、数字和连字符',
        titleRequired: '题目标题不能为空',
        examplesRequired: '至少需要一个测试用例',
        inputRequired: '输入不能为空',
        outputRequired: '输出不能为空',
      },
    },

    // 对话框
    dialog: {
      delete: {
        title: '删除题目',
        description: '确定要删除"{title}"吗？此操作不可撤销。',
        thisProblem: '此题目',
        confirm: '删除题目',
      },
    },

    // 批量操作
    bulk: {
      selected: '已选择 {count} 个',
      noSelection: '请至少选择一个题目',
      action: '批量操作',
      publish: '发布',
      unpublish: '取消发布',
      delete: '删除',
      restore: '恢复',
      publishTitle: '发布 {count} 个题目',
      publishDescription: '确定要发布 {count} 个题目吗？',
      unpublishTitle: '取消发布 {count} 个题目',
      unpublishDescription: '确定要取消发布 {count} 个题目吗？',
      deleteTitle: '删除 {count} 个题目',
      deleteDescription: '确定要删除 {count} 个题目吗？此操作不可撤销。',
      restoreTitle: '恢复 {count} 个题目',
      restoreDescription: '确定要恢复 {count} 个题目吗？',
      confirmPublish: '发布 {count} 个题目',
      confirmUnpublish: '取消发布 {count} 个题目',
      confirmDelete: '删除 {count} 个题目',
      confirmRestore: '恢复 {count} 个题目',
      warning: '警告',
      warningDescription: '此操作将影响选中的题目，无法撤销。',
      success: '成功{action} {count} 个题目',
      failed: '未能{action} {count} 个题目',
      partial: '部分完成：{success} 个成功，{failed} 个失败',
    },

    // 批量编辑
    bulkEdit: {
      title: '批量编辑',
      description: '编辑 {count} 个题目',
      category: '类别',
      categoryPlaceholder: '例如：数组、字符串、树',
      categoryHint: '留空以保持当前类别',
      difficulty: '难度',
      difficultyPlaceholder: '选择难度',
      tags: '标签',
      tagsPlaceholder: '添加标签（按回车）',
      addTag: '添加',
      tagsHint: '留空以保持当前标签',
      premium: '高级',
      premiumHint: '切换所有选中题目的高级状态',
      noChanges: '没有要应用的更改',
      edit: '批量编辑',
      editing: '编辑中...',
      success: '成功编辑 {count} 个题目',
      failure: '编辑题目失败',
      partial: '部分完成：{success} 个成功，{failed} 个失败',
      error: '批量编辑题目失败',
    },

    // 排序
    sort: {
      title: '排序方式',
      default: '默认',
      titleAsc: '标题（A-Z）',
      titleDesc: '标题（Z-A）',
      difficultyAsc: '难度（从易到难）',
      difficultyDesc: '难度（从难到易）',
      createdAsc: '创建时间（从旧到新）',
      createdDesc: '创建时间（从新到旧）',
      updatedAsc: '更新时间（从旧到新）',
      updatedDesc: '更新时间（从新到旧）',
      submissionsAsc: '提交数（从低到高）',
      submissionsDesc: '提交数（从高到低）',
    },

    // 导出
    export: {
      title: '导出',
      json: '导出为 JSON',
      csv: '导出为 CSV',
      success: '题目导出成功',
      error: '导出题目失败',
    },

    // 导入
    import: {
      title: '导入',
      description: '从 JSON 或 CSV 文件导入题目',
      dropFile: '拖放文件到此处，或点击浏览',
      browse: '浏览',
      supportedFormats: '支持的格式：JSON、CSV',
      conflictStrategy: '冲突策略',
      conflictStrategyDescription: '如何处理已存在的 slug',
      strategies: {
        skip: '跳过已存在',
        update: '更新已存在',
        createNew: '创建新的（带后缀）',
      },
      strategyDescriptions: {
        skip: '跳过已存在的题目',
        update: '用新数据更新已存在的题目',
        createNew: '创建新题目，在 slug 后添加后缀',
      },
      import: '导入',
      cancel: '取消',
      close: '关闭',
      clear: '清除',
      success: '导入完成',
      error: '导入失败',
      partialSuccess: '部分完成：{success}/{total} 已导入',
      someErrors: '部分题目无法导入。查看下方详情。',
      results: '导入结果',
      total: '总数',
      created: '已创建',
      updated: '已更新',
      skipped: '已跳过',
      failed: '失败',
      imported: '已导入',
      errors: '错误',
      noFile: '未选择文件',
      invalidFile: '无效的文件格式',
      importing: '导入中...',
      importProgress: '导入中... {progress}%',
    },

    // 版本历史
    versionHistory: {
      title: '版本历史',
      noVersions: '暂无版本历史',
      viewVersion: '查看版本',
      restoreVersion: '恢复此版本',
      restoreTitle: '恢复版本',
      restoreDescription: '确定要恢复此版本吗？当前版本将保存为新版本。',
      restoreSuccess: '版本恢复成功',
      restoreError: '恢复版本失败',
      changes: '更改',
      noChanges: '无更改',
      added: '已添加',
      removed: '已删除',
      changed: '已更改',
      compareWith: '与...比较',
      currentVersion: '当前版本',
      version: '版本',
      performedBy: '操作者',
      at: '于',
      action: {
        CREATE: '创建',
        UPDATE: '更新',
        DELETE: '删除',
        RESTORE: '恢复',
      },
    },

    // 内容审核
    moderation: {
      title: '审核队列',
      description: '查看和管理被标记的题目',
      filterStatus: '按状态筛选',
      allStatuses: '全部状态',
      statusPending: '待处理',
      statusReviewed: '已审核',
      statusResolved: '已解决',
      statusDismissed: '已驳回',
      noFlagged: '无被标记的题目',
      noFlaggedDescription: '目前没有需要审核的被标记题目。',
      flagReason: '标记原因',
      moderationNotes: '审核备注',
      moderate: '审核',
      moderateTitle: '审核题目',
      status: '状态',
      notes: '备注',
      notesPlaceholder: '添加审核备注（可选）...',
      success: '题目审核成功',
      error: '审核题目失败',
      loadError: '加载被标记题目失败',
      reasonPrompt: '请输入标记此题目的原因：',
      flagSuccess: '题目标记成功',
      unflagSuccess: '题目取消标记成功',
      flag: '标记',
      unflag: '取消标记',
    },
  },

  // 比赛
  contests: {
    title: '比赛',
    listTitle: '比赛管理',
    addContest: '创建比赛',
    createContest: '创建比赛',
    searchPlaceholder: '搜索比赛...',
    selected: '已选择 {count} 个比赛',
    clearSelection: '清除选择',

    // 筛选器
    filters: {
      allStatus: '全部状态',
      allTypes: '全部类型',
      upcoming: '即将开始',
      running: '进行中',
      finished: '已结束',
      public: '公开',
      private: '私有',
      virtual: '虚拟',
    },

    // 类型
    type: {
      PUBLIC: '公开',
      PRIVATE: '私有',
      VIRTUAL: '虚拟',
      weekly: '周赛',
      biweekly: '双周赛',
    },

    // 表格列
    columns: {
      contest: '比赛',
      type: '类型',
      status: '状态',
      schedule: '时间安排',
      participants: '参赛人数',
      actions: '操作',
    },

    // 操作
    actions: {
      viewDetails: '查看详情',
      startContest: '开始比赛',
      endContest: '结束比赛',
      bulkDelete: '批量删除',
      delete: '删除',
    },

    // 状态徽章
    status: {
      upcoming: '即将开始',
      running: '进行中',
      finished: '已结束',
    },

    // 向导
    wizard: {
      basics: '基本信息',
      schedule: '时间安排',
      problems: '题目选择',
      review: '确认信息',
      previous: '上一步',
      next: '下一步',
      submit: '创建比赛',
      createContest: '创建比赛',
    },

    // 基本信息步骤
    basics: {
      title: '标题',
      titlePlaceholder: '第101周赛',
      titleDescription: '比赛的显示名称。',
      slug: '标识符',
      slugPlaceholder: 'weekly-contest-101',
      slugDescription: '比赛的唯一URL标识符。',
      type: '类型',
      typePlaceholder: '选择类型',
      typeDescription: '公开比赛对所有用户可见。私有比赛需要邀请才能参加。',
      description: '描述',
      descriptionPlaceholder: '比赛详情和规则说明...',
    },

    // 时间安排步骤
    scheduleStep: {
      startTime: '开始时间',
      startTimeDescription: '比赛开始的时间。',
      duration: '时长（分钟）',
      durationDescription: '比赛的持续时间，单位为分钟。',
      publishImmediately: '立即发布',
      publishImmediatelyDescription: '如果启用，比赛将立即显示在即将开始的比赛列表中。',
      notSet: '未设置',
      minutes: '{minutes} 分钟',
    },

    // 题目选择步骤
    problemsStep: {
      contestProblems: '比赛题目',
      addProblem: '添加题目',
      index: '序号',
      title: '标题',
      difficulty: '难度',
      score: '分值',
      noProblemsSelected: '尚未选择题目。请添加比赛题目。',
    },

    // 确认步骤
    reviewStep: {
      basicInfo: '基本信息',
      schedule: '时间安排',
      startTime: '开始时间',
      duration: '时长',
      visibility: '可见性',
      problemsCount: '题目 ({count})',
      noProblemsSelected: '尚未选择题目。',
      published: '已发布',
      draft: '草稿',
    },

    // 题目选择器
    problemPicker: {
      title: '选择题目',
      description: '搜索并选择要添加到比赛的题目。',
      searchPlaceholder: '按标题或标识符搜索题目...',
      problems: '题目',
      noProblemsFound: '未找到题目。',
      noProblems: '无题目',
    },

    // 详情视图
    detail: {
      overview: '概览',
      problems: '题目',
      participants: '参赛者',
      rankings: '排名',
      details: '详情',
      statsAndSchedule: '统计与时间安排',
      description: '描述',
      noDescription: '未提供描述。',
      slug: '标识符',
      visibility: '可见性',
      published: '已发布',
      hidden: '隐藏',
      startTime: '开始时间',
      duration: '时长',
      contestProblems: '比赛题目',
      addProblem: '添加题目',
      idx: '序号',
      problem: '题目',
      difficulty: '难度',
      score: '分值',
      noProblemsAdded: '尚未添加题目。',
      user: '用户',
      joinedAt: '加入时间',
      noParticipantsYet: '尚无参赛者。',
      rank: '排名',
      penalty: '罚时',
      noRankingsYet: '暂无排名数据。',
      contestNotFound: '未找到比赛。',
      backToList: '返回列表',
      start: '开始',
      end: '结束',
    },

    // 详情抽屉
    drawer: {
      title: '比赛详情',
      subtitle: '查看比赛信息和统计数据。',
      fullView: '完整视图',
      loadingDetails: '加载比赛详情中...',
      contestNotFound: '未找到比赛',
      statistics: '统计信息',
      schedule: '时间安排',
      start: '开始时间',
      duration: '时长（分钟）',
      description: '描述',
      problemsCount: '题目 ({count})',
      moreProblems: '+ {count} 个题目',
      pts: '分',
    },

    // 删除对话框
    delete: {
      title: '删除比赛',
      description: '确定要删除 <strong>{title}</strong> 吗？此操作无法撤销。',
      thisContest: '此比赛',
      confirm: '删除比赛',
      deleting: '删除中...',
      cancel: '取消',
    },

    // 提示消息
    toast: {
      startedSuccessfully: '比赛已开始',
      failedToStart: '启动比赛失败',
      endedSuccessfully: '比赛已结束',
      failedToEnd: '结束比赛失败',
      deletedSuccessfully: '比赛已删除',
      failedToDelete: '删除比赛失败',
      createdSuccessfully: '比赛已创建',
      failedToCreate: '创建比赛失败',
      problemAdded: '题目已添加到比赛',
      failedToAddProblem: '添加题目失败',
      problemRemoved: '题目已移除',
      failedToRemoveProblem: '移除题目失败',
      bulkDeleteSuccess: '已删除 {count} 个比赛',
      bulkDeleteFailed: '删除部分比赛失败',
    },

    // 确认提示
    confirmation: {
      startNow: '确定要立即开始此比赛吗？',
      endNow: '确定要结束此比赛吗？',
      deleteThis: '确定要删除此比赛吗？此操作无法撤销。',
      bulkDelete: '确定要删除 {count} 个比赛吗？此操作不可撤销。',
      removeProblem: '要从比赛中移除此题目吗？',
    },
  },

  // 通知
  notifications: {
    title: '通知',
    listTitle: '通知管理',
    addNotification: '添加通知',
    searchPlaceholder: '搜索通知...',
    newNotification: '新建通知',
    allTypes: '所有类型',
    sentAt: '发送时间',
    sentBy: '发送者',
    refresh: '刷新',

    // 表格列
    columns: {
      title: '标题',
      type: '类型',
      priority: '优先级',
      recipients: '接收者',
      sendAt: '发送时间',
      status: '状态',
    },

    // 操作
    actions: {
      view: '查看',
      edit: '编辑',
      delete: '删除',
      send: '立即发送',
    },

    // 表单
    form: {
      title: '标题',
      content: '内容',
      type: '类型',
      priority: '优先级',
      sendAt: '发送时间',
      sendToAll: '发送给所有用户',
      targetUsers: '目标用户',
    },

    // 类型
    type: {
      info: '信息',
      warning: '警告',
      success: '成功',
      error: '错误',
    },

    // 优先级
    priority: {
      low: '低',
      normal: '普通',
      high: '高',
      urgent: '紧急',
    },

    // 状态
    status: {
      draft: '草稿',
      scheduled: '已计划',
      sent: '已发送',
    },
  },

  // 审计日志
  audit: {
    title: '审计日志',
    listTitle: '审计日志历史',
    searchPlaceholder: '搜索日志...',

    // 表格列
    columns: {
      action: '操作',
      performer: '操作者',
      target: '目标',
      entityType: '实体类型',
      ip: 'IP 地址',
      createdAt: '时间',
      details: '详情',
    },

    // 筛选器
    filters: {
      allActions: '全部操作',
      allUsers: '全部用户',
      dateRange: '日期范围',
    },

    // 操作
    actions: {
      viewDetails: '查看详情',
      exportLogs: '导出日志',
      create: '创建',
      update: '更新',
      delete: '删除',
      publish: '发布',
      moderate: '审核',
    },

    // 审计日志查看器
    filterAction: '按操作筛选',
    allActions: '全部操作',
    noLogs: '无审计日志',
    noLogsDescription: '没有可显示的审计日志。',
    systemAction: '系统操作',
    oldValues: '旧值',
    newValues: '新值',
    ipAddress: 'IP 地址',
    userAgent: '用户代理',
  },

  // 审计报告
  auditReport: {
    title: '审计报告',
    description: '查看审计统计信息并生成报告',
    filters: '筛选器',
    startDate: '开始日期',
    endDate: '结束日期',
    performer: '操作者',
    performerPlaceholder: '按操作者ID筛选...',
    applyFilters: '应用筛选',
    export: '导出报告',
    totalActions: '总操作数',
    allTime: '全部时间',
    uniqueEntities: '唯一实体',
    entityTypes: '实体类型',
    activePerformers: '活跃操作者',
    users: '用户',
    topPerformers: '活跃操作者排行',
    actionsByEntity: '按实体分类的操作',
    actions: '操作',
  },

  // 设置
  settings: {
    title: '设置',
    description: '管理系统全局配置和偏好。',

    // 提示消息
    toast: {
      loadFailed: '加载设置失败',
      saveFailed: '保存设置失败',
      clearCacheFailed: '清除缓存失败',
    },

    // 通用设置
    generalSettings: {
      category: '通用',
      siteName: '站点名称',
      siteDescription: '站点描述',
      siteUrl: '站点 URL',
      logo: 'Logo URL',
      favicon: '网站图标 URL',
      language: '默认语言',
      timezone: '时区',
    },

    // 安全设置
    securitySettings: {
      category: '安全',
      passwordMinLength: '最小密码长度',
      passwordRequireUppercase: '需要大写字母',
      passwordRequireLowercase: '需要小写字母',
      passwordRequireNumbers: '需要数字',
      passwordRequireSpecialChars: '需要特殊字符',
      sessionTimeout: '会话超时（分钟）',
      maxLoginAttempts: '最大登录尝试次数',
      lockoutDuration: '锁定时长（分钟）',
    },

    // 邮件设置
    emailSettings: {
      category: '邮件',
      smtpHost: 'SMTP 主机',
      smtpPort: 'SMTP 端口',
      smtpSecure: '使用 SSL/TLS',
      smtpUser: 'SMTP 用户名',
      smtpFrom: '发件邮箱',
      smtpFromName: '发件人名称',
      testEmail: '发送测试邮件',
    },

    // 基本设置
    general: {
      title: '基本设置',
      description: '站点基本信息。',
    },
    siteName: '站点名称',
    siteDescription: '站点描述',

    // 用户注册
    userRegistration: {
      title: '用户注册',
      description: '控制用户注册方式。',
      enableRegistrations: '启用注册',
      enableRegistrationsDescription: '允许新用户创建账号。',
      requireEmailVerification: '需要邮箱验证',
      requireEmailVerificationDescription: '用户必须验证邮箱后才能登录。',
    },

    // 系统状态
    systemStatus: {
      title: '系统状态',
      description: '控制站点可用性。',
      maintenanceMode: '维护模式',
      maintenanceModeDescription: '启用后仅管理员可访问站点。',
    },

    // 操作
    actions: {
      title: '操作',
      clearCache: '清除系统缓存',
      saveChanges: '保存更改',
      saving: '保存中...',
    },
  },

  // 题目列表
  problemLists: {
    title: '题目列表',
    addList: '添加列表',
    searchPlaceholder: '搜索列表...',
    createList: '创建题目列表',
    editList: '编辑列表',
    backToLists: '返回列表',
    errorLoading: '加载列表出错',
    generalInfo: '基本信息',
    problems: '题目',

    // 表格列
    columns: {
      name: '名称',
      featured: '精选',
      visibility: '可见性',
      problems: '题目数',
      order: '排序',
      createdAt: '创建时间',
    },

    // 筛选
    filters: {
      type: '类型',
      allTypes: '全部类型',
      featured: '精选',
      standard: '标准',
      visibility: '可见性',
      allVisibility: '全部可见性',
      public: '公开',
      private: '私有',
    },

    // 操作
    actions: {
      view: '查看',
      edit: '编辑',
      delete: '删除',
    },

    // 表单
    form: {
      name: '列表名称',
      namePlaceholder: '例如：动态编程前100题',
      description: '描述',
      descriptionPlaceholder: '描述此列表的内容...',
      isPublic: '公开',
      isPublicDescription: '使此列表对所有用户可见',
      isFeatured: '精选',
      isFeaturedDescription: '在主页上显示此列表',
      bannerTag: '横幅标签',
      bannerTagPlaceholder: '例如：热门',
      bannerTagDescription: '在横幅卡片上显示的小标签',
      bannerTheme: '横幅主题',
      bannerThemePlaceholder: '选择主题',
      sortOrder: '排序顺序',
      sortOrderDescription: '在精选列表中的顺序（越小越靠前）',
      saving: '保存中...',
      saveChanges: '保存更改',
      validation: {
        nameRequired: '列表名称为必填项',
      },
    },

    // 主题
    themes: {
      blue: '蓝色',
      green: '绿色',
      purple: '紫色',
      orange: '橙色',
      red: '红色',
    },

    // 题目管理器
    problemsManager: {
      title: '题目',
      addProblem: '添加题目',
      saveChanges: '保存更改',
      saving: '保存中...',
      order: '顺序',
      problem: '题目',
      difficulty: '难度',
      noProblems: '此列表中没有题目。',
      addProblems: '添加题目',
      removeProblems: '移除题目',
      selectedProblems: '已选题目',
      availableProblems: '可选题目',
      reorder: '重新排序',
    },

    // 删除对话框
    delete: {
      title: '删除题目列表',
      description: '确定要删除 <strong>{name}</strong> 吗？此操作无法撤销。',
      thisList: '此列表',
      confirm: '删除列表',
      deleting: '删除中...',
      cancel: '取消',
    },

    // 提示消息
    toast: {
      createdSuccess: '列表创建成功',
      updatedSuccess: '列表更新成功',
      deletedSuccess: '题目列表删除成功',
      createFailed: '保存列表失败',
      deleteFailed: '删除题目列表失败',
      problemsUpdated: '题目更新成功',
      problemsUpdateFailed: '更新题目失败',
    },
  },

  // 标签
  tags: {
    title: '标签',
    addTag: '添加标签',
    searchPlaceholder: '搜索标签...',
    mergeTags: '合并标签',
    selected: '已选择 {count} 个标签',
    selected_one: '已选择 {count} 个标签',
    clearSelection: '清除选择',
    bulkDelete: '批量删除',
    createTag: '创建标签',
    tagType: '标签类型',
    problemTags: '题目标签',
    forumTags: '论坛标签',
    retry: '重试',

    // 表格列
    columns: {
      name: '名称',
      label: '显示标签',
      color: '颜色',
      problems: '题目数',
      createdAt: '创建时间',
      tag: '标签',
      usage: '使用量',
      description: '描述',
      actions: '操作',
    },

    // 操作
    actions: {
      edit: '编辑',
      delete: '删除',
      merge: '合并到...',
      mergeInto: '合并到...',
      noActionsAvailable: '无可用操作',
    },

    // 表单
    form: {
      name: '标签名称',
      label: '显示标签',
      color: '颜色',
      description: '描述',
      slug: '标识符（可选）',
      slugPlaceholder: 'dynamic-programming',
      colorHex: '颜色（十六进制）',
      colorPlaceholder: '#3b82f6',
      namePlaceholder: '动态规划',
      descriptionPlaceholder: '标签描述...',
      editTitle: '编辑标签',
      createTitle: '创建标签',
      editDescription: '在此处更改标签。',
      createDescription: '添加新标签到系统。',
      saveChanges: '保存更改',
      createTag: '创建标签',
      nameRequired: '名称为必填项',
      nameTooLong: '名称过长',
    },

    // 删除对话框
    delete: {
      title: '删除标签',
      description:
        '确定要删除标签 <span class="font-medium text-foreground">"{name}"</span> 吗？此操作无法撤销。',
      confirm: '删除标签',
    },

    // 合并对话框
    merge: {
      title: '合并标签',
      description:
        '将 <span class="font-medium text-foreground">"{source}"</span> 合并到另一个标签。所有关联将移动到目标标签，源标签将被删除。',
      targetTag: '目标标签',
      targetTagPlaceholder: '选择要合并到的标签',
      confirm: '合并标签',
      sourceTag: '源标签（将被删除）',
      targetTagLabel: '目标标签（将被保留）',
      mergeConfirm: '确定合并标签吗？所有标记为 "{source}" 的题目将被重新标记为 "{target}"。',
    },

    // 消息提示
    toast: {
      createdSuccessfully: '标签创建成功',
      updatedSuccessfully: '标签更新成功',
      deletedSuccessfully: '标签删除成功',
      mergedSuccessfully: '标签合并成功',
      failedToCreate: '创建标签失败',
      failedToUpdate: '更新标签失败',
      failedToDelete: '删除标签失败',
      failedToMerge: '合并标签失败',
      bulkDeleteSuccess: '已删除 {count} 个标签',
      bulkDeleteFailed: '部分标签删除失败',
      bulkDeleteConfirm: '确定要删除 {count} 个标签吗？此操作不可撤销。',
      bulkDeleteConfirm_one: '确定要删除 {count} 个标签吗？此操作不可撤销。',
    },
  },

  // 题解
  solutions: {
    title: '题解',
    listTitle: '题解管理',
    searchPlaceholder: '搜索题解...',

    // 筛选
    filters: {
      flagStatus: '标记状态',
      visibility: '可见性',
      all: '全部',
      flagged: '已标记',
      clean: '正常',
      published: '已发布',
      unpublished: '未发布',
    },

    // 表格列
    columns: {
      id: 'ID',
      solution: '题解',
      author: '作者',
      status: '状态',
      views: '浏览',
      created: '创建时间',
      actions: '操作',
    },

    // 状态
    status: {
      deleted: '已删除',
      flagged: '已标记',
      published: '已发布',
      unpublished: '未发布',
    },

    // 操作
    actions: {
      viewDetails: '查看详情',
      unflag: '取消标记',
      flag: '标记',
      delete: '删除',
    },

    // 标签页
    tabs: {
      description: '描述',
      code: '代码',
    },

    // 详情视图
    detail: {
      solutionFor: '{problem} 的题解',
      noDescriptionContent: '未提供描述内容。',
      summary: '摘要',
      metadata: '元数据',
      author: '作者',
      problemDifficulty: '题目难度',
      views: '浏览量',
      language: '语言',
      created: '创建时间',
      updated: '更新时间',
      tags: '标签',
      flaggedReason: '标记原因',
      at: '于',
      sourceCode: '源代码',
      lines: '行',
      copied: '已复制',
      copy: '复制',
      noCodeContent: '无代码内容。',
    },

    // 删除对话框
    delete: {
      title: '删除题解',
      description:
        '确定要删除题解 <span class="font-medium text-foreground">"{title}"</span> 吗？此操作无法撤销。',
      cancel: '取消',
      confirm: '删除题解',
      deleting: '删除中...',
    },

    // 标记对话框
    flag: {
      title: '标记题解',
      description:
        '标记题解 <span class="font-medium text-foreground">"{title}"</span> 将其标记为审核，并根据设置可能会从公共视图中隐藏。',
      reasonLabel: '标记原因',
      reasonPlaceholder: '请说明此题解违反社区准则的原因...',
      cancel: '取消',
      confirm: '标记题解',
      flagging: '标记中...',
    },

    // 提示消息
    toast: {
      unflaggedSuccessfully: '题解已成功取消标记',
      failedToUnflag: '取消标记题解失败',
      deletedSuccessfully: '题解已成功删除',
      failedToDelete: '删除题解失败',
      flaggedSuccessfully: '题解已成功标记',
      failedToFlag: '标记题解失败',
      reasonRequired: '请提供标记原因',
    },

    // 错误状态
    error: {
      loadingSolution: '题解加载错误',
      solutionNotFound: '未找到题解',
      notFoundDescription: '题解不存在或您没有权限查看。',
      backToSolutions: '返回题解列表',
      back: '返回',
      retry: '重试',
    },
  },

  // 论坛
  forum: {
    title: '论坛',
    postsTitle: '论坛帖子',
    searchPlaceholder: '搜索帖子...',

    // 筛选
    filters: {
      community: '社区',
      allCommunities: '所有社区',
      flagStatus: '标记状态',
      pinned: '置顶',
      locked: '锁定',
      all: '全部',
      flagged: '已标记',
      clean: '正常',
      unpinned: '未置顶',
      unlocked: '未锁定',
    },

    // 表格列
    columns: {
      title: '标题',
      stats: '统计',
      status: '状态',
      created: '创建时间',
      actions: '操作',
    },

    // 状态
    status: {
      deleted: '已删除',
      flagged: '已标记',
      active: '活跃',
      pinned: '已置顶',
      locked: '已锁定',
    },

    // 操作
    actions: {
      viewDetails: '查看详情',
      pin: '置顶',
      unpin: '取消置顶',
      lock: '锁定',
      unlock: '解锁',
      delete: '删除',
    },

    // 标签页
    tabs: {
      overview: '概览',
      comments: '评论',
      audit: '审核记录',
    },

    // 详情视图
    detail: {
      inCommunity: '于 {community}',
      content: '内容',
      noContentAvailable: '无可用内容',
      views: '浏览',
      comments: '评论',
      upvotes: '赞同',
      downvotes: '反对',
      timeline: '时间线',
      created: '创建时间',
      updated: '更新时间',
      flagInformation: '标记信息',
      reason: '原因：',
      flaggedOn: '标记于：',
      deletionInformation: '删除信息',
      deletedOn: '删除于：',
      identifiers: '标识符',
      postId: '帖子 ID：',
      authorId: '作者 ID：',
      communityId: '社区 ID：',
    },

    // 抽屉
    drawer: {
      title: '帖子详情',
      description: '查看论坛帖信息和内容。',
      authorCommunity: '作者与社区',
      unknownCommunity: '未知社区',
      statistics: '统计信息',
      contentPreview: '内容预览',
      postNotFound: '未找到帖子',
    },

    // 概览显示
    overview: {
      author: '作者',
      unknown: '未知',
    },

    // 评论标签页
    comments: {
      postComments: '帖子评论',
      noCommentsFound: '此帖子没有评论',
    },

    // 审核标签页
    audit: {
      noAuditHistory: '无审核记录',
      performed: '执行了',
      ip: 'IP：',
      from: '从：',
      to: '到：',
    },

    // 审核操作标签
    auditActions: {
      PIN_FORUM_POST: '已置顶',
      UNPIN_FORUM_POST: '已取消置顶',
      LOCK_FORUM_POST: '已锁定',
      UNLOCK_FORUM_POST: '已解锁',
      DELETE_FORUM_POST: '已删除',
      FLAG_FORUM_POST: '已标记',
      UNFLAG_FORUM_POST: '已取消标记',
      BULK_DELETE_FORUM: '批量删除',
      BULK_PIN_FORUM: '批量置顶',
    },

    // 删除对话框
    delete: {
      title: '删除帖子',
      description: '确定要删除此帖子吗？此操作无法撤销。',
      cancel: '取消',
      confirm: '删除帖子',
      deleting: '删除中...',
    },

    // 标记对话框
    flag: {
      title: '标记帖子',
      description: '请提供标记此帖子进行审核的原因。',
      reasonLabel: '原因',
      reasonPlaceholder: '输入标记此帖子的原因...',
      cancel: '取消',
      confirm: '标记帖子',
      flagging: '标记中...',
    },

    // 提示消息
    toast: {
      unpinnedSuccessfully: '帖子已取消置顶',
      pinnedSuccessfully: '帖子已置顶',
      failedToUpdatePin: '更新置顶状态失败',
      unlockedSuccessfully: '帖子已解锁',
      lockedSuccessfully: '帖子已锁定',
      failedToUpdateLock: '更新锁定状态失败',
      unflaggedSuccessfully: '帖子已成功取消标记',
      failedToUnflag: '取消标记帖子失败',
      deletedSuccessfully: '帖子已成功删除',
      failedToDelete: '删除帖子失败',
      flaggedSuccessfully: '帖子已成功标记',
      failedToFlag: '标记帖子失败',
      reasonRequired: '请提供标记原因',
    },

    // 错误状态
    error: {
      loadingPost: '帖子加载错误',
      postNotFound: '未找到帖子',
      notFoundDescription: '帖子不存在或您没有权限查看。',
      backToForumPosts: '返回论坛帖子',
      back: '返回',
      retry: '重试',
    },
  },

  // 评论
  comments: {
    title: '评论',
    listTitle: '评论管理',
    searchPlaceholder: '搜索评论...',

    // 筛选
    filters: {
      type: '类型',
      allTypes: '所有类型',
      flagStatus: '标记状态',
      all: '全部',
      flagged: '已标记',
      clean: '正常',
    },

    // 类型
    type: {
      forum: '论坛',
      solution: '题解',
    },

    // 表格列
    columns: {
      comment: '评论',
      author: '作者',
      type: '类型',
      status: '状态',
      created: '创建时间',
      actions: '操作',
    },

    // 状态
    status: {
      deleted: '已删除',
      flagged: '已标记',
      active: '活跃',
    },

    // 操作
    actions: {
      unflag: '取消标记',
      flag: '标记',
      delete: '删除',
    },

    // 删除对话框
    delete: {
      title: '删除评论',
      description: '确定要删除此评论吗？此操作无法撤销。',
      cancel: '取消',
      confirm: '删除评论',
      deleting: '删除中...',
    },

    // 标记对话框
    flag: {
      title: '标记评论',
      description: '标记此评论将其标记为审核，并根据设置可能会从公共视图中隐藏。',
      reasonLabel: '标记原因',
      reasonPlaceholder: '请说明此评论违反社区准则的原因...',
      cancel: '取消',
      confirm: '标记评论',
      flagging: '标记中...',
    },

    // 提示消息
    toast: {
      unflaggedSuccessfully: '评论已成功取消标记',
      failedToUnflag: '取消标记评论失败',
      deletedSuccessfully: '评论已成功删除',
      failedToDelete: '删除评论失败',
      flaggedSuccessfully: '评论已成功标记',
      failedToFlag: '标记评论失败',
      reasonRequired: '请提供标记原因',
    },
  },

  // 认证
  auth: {
    // 登录
    login: {
      title: '登录',
      subtitle: '输入您的凭据以访问管理面板',
      username: '用户名',
      usernamePlaceholder: 'admin',
      password: '密码',
      passwordPlaceholder: '••••••••',
      submit: '登录',
      submitting: '登录中...',
      invalidCredentials: '用户名或密码无效',
      loginFailed: '登录失败。请重试。',
      demoAccounts: '演示账号',
      demoAccountsTitle: '使用以下凭据：',
      demoAdmin: '• admin / admin123 (超级管理员)',
      demoModerator: '• moderator / mod123 (版主)',
      rememberMe: '记住我',
      forgotPassword: '忘记密码？',
      noAccount: '还没有账号？',
      signup: '注册',
    },

    // 注册
    signup: {
      title: '创建您的账号',
      subtitle: '填写以下表单以创建您的账号',
      fullName: '全名',
      fullNamePlaceholder: '张三',
      email: '邮箱',
      emailPlaceholder: 'm@example.com',
      emailDescription: '我们将使用此联系方式与您联系。我们不会与任何人分享您的邮箱。',
      password: '密码',
      passwordDescription: '密码至少需要 8 个字符。',
      confirmPassword: '确认密码',
      confirmPasswordDescription: '请确认您的密码。',
      submit: '创建账号',
      orContinueWith: '或继续使用',
      github: '使用 GitHub 注册',
      alreadyHaveAccount: '已有账号？',
      signIn: '登录',
      agreeToTerms: '我同意服务条款',
    },

    // 登出
    logout: {
      confirm: '确定要退出登录吗？',
    },
  },

  // 验证消息
  validation: {
    required: '{field} 是必填项',
    minLength: '{field} 至少需要 {min} 个字符',
    maxLength: '{field} 最多 {max} 个字符',
    email: '邮箱地址无效',
    passwordMatch: '密码不匹配',
    url: 'URL 无效',
    number: '必须是数字',
    positive: '必须是正数',
    integer: '必须是整数',
    range: '必须在 {min} 和 {max} 之间',
    unique: '该值已被占用',
  },

  // 提示消息
  toast: {
    success: '成功',
    error: '错误',
    warning: '警告',
    info: '信息',
    loadFailed: '加载数据失败',
    loadSuccess: '数据加载成功',
    saveSuccess: '保存成功',
    saveFailed: '保存失败',
    deleteSuccess: '删除成功',
    deleteFailed: '删除失败',
    updateSuccess: '更新成功',
    updateFailed: '更新失败',
    createSuccess: '创建成功',
    createFailed: '创建失败',
  },

  // 错误消息
  errors: {
    validation: {
      title: '验证错误',
      default: '提交的数据无效',
      suggestion: '请检查输入后重试',
    },
    unauthorized: {
      title: '未授权',
      message: '需要登录才能执行此操作',
      suggestion: '请登录后重试',
    },
    forbidden: {
      title: '访问被拒绝',
      message: '您没有执行此操作的权限',
      suggestion: '如果您认为这是一个错误，请联系管理员',
    },
    notFound: {
      title: '未找到',
      message: '资源未找到',
      suggestion: '资源可能已被删除或移动',
    },
    serverError: {
      title: '服务器错误',
      message: '服务器遇到错误',
      suggestion: '请稍后重试或联系支持',
    },
    network: {
      title: '网络错误',
      message: '无法连接到服务器',
      suggestion: '请检查您的网络连接',
    },
  },

  // 分页
  pagination: {
    rowsPerPage: '每页行数',
    of: '共',
    page: '页',
    goTo: '跳转到',
    first: '首页',
    last: '末页',
    showing: '显示',
    to: '至',
    of_total: '共',
    results: '条结果',
  },

  // 数据表格
  table: {
    emptyState: '暂无数据',
    emptyTitle: '未找到结果',
    emptyDescription: '我们找不到您要查找的内容。尝试调整您的筛选器或搜索查询。',
    searchPlaceholder: '搜索...',
    filterPlaceholder: '筛选...',
    clearFilters: '清除筛选',
    showColumns: '显示列',
    hideColumns: '隐藏列',
    resetColumns: '重置列',
    customizeColumns: '自定义列',
    columns: '列',
    selectAll: '全选',
    deselectAll: '取消全选',
    selected: '已选 {count} 项',
    selected_one: '已选 {count} 项',
    rowsPerPage: '每页行数',
    of: '/',
    page: '第',
    rowsSelected: '行被选中',
    goToFirstPage: '转到第一页',
    goToPreviousPage: '转到上一页',
    goToNextPage: '转到下一页',
    goToLastPage: '转到最后一页',
  },

  // 对话框标签
  dialog: {
    close: '关闭',
    confirm: '确认',
    cancel: '取消',
    delete: '删除',
    save: '保存',
    submit: '提交',
  },

  // 空状态
  empty: {
    title: '暂无数据',
    description: '没有可显示的项目',
    action: '创建第一个项目',
  },
} as const
