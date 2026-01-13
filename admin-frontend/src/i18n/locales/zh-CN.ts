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
    getHelp: '获取帮助',
    search: '搜索',
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
      banExpiresAt: '封禁到期时间',
      avatar: '头像 URL',
    },

    // 操作
    actions: {
      viewDetails: '查看详情',
      editProfile: '编辑资料',
      resetPassword: '重置密码',
      banUser: '封禁用户',
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

    // 筛选器
    filters: {
      difficulty: '难度',
      allLevels: '全部难度',
      status: '状态',
      allStatus: '全部状态',
      visibility: '可见性',
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

    // 表单
    form: {
      title: '题目标题',
      slug: 'URL 标识',
      difficulty: '难度',
      timeLimit: '时间限制 (毫秒)',
      memoryLimit: '内存限制 (MB)',
      isPublic: '公开',
      isPublished: '已发布',
      description: '题目描述',
      inputFormat: '输入格式',
      outputFormat: '输出格式',
      constraints: '限制条件',
      hint: '提示',
      tags: '标签',
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
  },

  // 比赛
  contests: {
    title: '比赛',
    listTitle: '比赛管理',
    addContest: '添加比赛',
    searchPlaceholder: '搜索比赛...',

    // 筛选器
    filters: {
      allStatus: '全部状态',
      upcoming: '即将开始',
      running: '进行中',
      finished: '已结束',
    },

    // 表格列
    columns: {
      id: 'ID',
      name: '名称',
      startTime: '开始时间',
      duration: '时长',
      participants: '参赛人数',
      problems: '题目',
      status: '状态',
    },

    // 状态
    status: {
      upcoming: '即将开始',
      running: '进行中',
      finished: '已结束',
    },

    // 向导步骤
    wizard: {
      step1: '基本信息',
      step2: '选择题目',
      step3: '时间安排',
      step4: '确认信息',
      next: '下一步',
      previous: '上一步',
      submit: '创建比赛',
    },

    // 表单
    form: {
      name: '比赛名称',
      description: '描述',
      startTime: '开始时间',
      endTime: '结束时间',
      duration: '时长（分钟）',
      isPublic: '公开',
      rules: '规则说明',
    },

    // 题目选择器
    problemPicker: {
      addProblems: '添加题目',
      selectedProblems: '已选题目',
      availableProblems: '可选题目',
      remove: '移除',
      points: '分值',
    },

    // 确认
    review: {
      reviewContest: '确认比赛信息',
      contestSummary: '比赛概要',
      problemsSummary: '题目列表',
      scheduleSummary: '时间安排',
    },
  },

  // 题解
  solutions: {
    title: '题解',
    listTitle: '题解管理',
    searchPlaceholder: '搜索题解...',

    // 表格列
    columns: {
      problem: '题目',
      author: '作者',
      language: '语言',
      status: '状态',
      flags: '标记',
      createdAt: '创建时间',
    },

    // 操作
    actions: {
      view: '查看',
      flag: '标记',
      unflag: '取消标记',
      delete: '删除',
    },

    // 状态
    status: {
      flagged: '已标记',
      approved: '已通过',
      pending: '待审核',
    },

    // 标签页
    tabs: {
      code: '代码',
      description: '题解说明',
    },

    // 表单
    form: {
      flagReason: '标记原因',
      notes: '管理员备注',
    },
  },

  // 论坛
  forum: {
    title: '论坛',
    postsTitle: '论坛帖子',
    commentsTitle: '论坛评论',
    searchPlaceholder: '搜索帖子...',

    // 表格列
    columns: {
      title: '标题',
      author: '作者',
      category: '分类',
      replies: '回复数',
      views: '浏览数',
      status: '状态',
      createdAt: '创建时间',
    },

    // 操作
    actions: {
      view: '查看',
      flag: '标记',
      unflag: '取消标记',
      delete: '删除',
      lock: '锁定',
      unlock: '解锁',
      pin: '置顶',
      unpin: '取消置顶',
    },

    // 状态
    status: {
      flagged: '已标记',
      locked: '已锁定',
      pinned: '已置顶',
    },

    // 标签页
    tabs: {
      overview: '概览',
      comments: '评论',
      audit: '审计日志',
    },

    // 表单
    form: {
      flagReason: '标记原因',
      moderationNotes: '审核备注',
    },
  },

  // 评论
  comments: {
    title: '评论',
    listTitle: '评论管理',
    searchPlaceholder: '搜索评论...',

    // 表格列
    columns: {
      content: '内容',
      author: '作者',
      type: '类型',
      target: '目标',
      status: '状态',
      createdAt: '创建时间',
    },

    // 操作
    actions: {
      view: '查看',
      flag: '标记',
      unflag: '取消标记',
      delete: '删除',
    },

    // 类型
    type: {
      forumPost: '论坛帖子',
      forumComment: '论坛评论',
      solutionComment: '题解评论',
    },

    // 状态
    status: {
      flagged: '已标记',
      visible: '可见',
      hidden: '隐藏',
    },

    // 表单
    form: {
      flagReason: '标记原因',
    },
  },

  // 通知
  notifications: {
    title: '通知',
    listTitle: '通知管理',
    addNotification: '添加通知',
    searchPlaceholder: '搜索通知...',

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
      export: '导出日志',
    },
  },

  // 设置
  settings: {
    title: '设置',

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
  },

  // 题目列表
  problemLists: {
    title: '题目列表',
    addList: '添加列表',
    searchPlaceholder: '搜索列表...',

    // 表格列
    columns: {
      name: '名称',
      owner: '创建者',
      problems: '题目数',
      isPublic: '公开',
      createdAt: '创建时间',
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
      description: '描述',
      isPublic: '公开',
    },

    // 题目管理器
    problemsManager: {
      addProblems: '添加题目',
      removeProblems: '移除题目',
      selectedProblems: '已选题目',
      availableProblems: '可选题目',
      reorder: '重新排序',
    },
  },

  // 标签
  tags: {
    title: '标签',
    addTag: '添加标签',
    searchPlaceholder: '搜索标签...',
    mergeTags: '合并标签',

    // 表格列
    columns: {
      name: '名称',
      label: '显示标签',
      color: '颜色',
      problems: '题目数',
      createdAt: '创建时间',
    },

    // 操作
    actions: {
      edit: '编辑',
      delete: '删除',
      merge: '合并到...',
    },

    // 表单
    form: {
      name: '标签名称',
      label: '显示标签',
      color: '颜色',
      description: '描述',
    },

    // 合并对话框
    merge: {
      sourceTag: '源标签（将被删除）',
      targetTag: '目标标签（将被保留）',
      confirm: '确定合并标签吗？所有标记为 "{source}" 的题目将被重新标记为 "{target}"。',
    },
  },

  // 认证
  auth: {
    // 登录
    login: {
      title: '管理员登录',
      username: '用户名',
      password: '密码',
      submit: '登录',
      rememberMe: '记住我',
      forgotPassword: '忘记密码？',
      noAccount: '还没有账号？',
      signup: '注册',
    },

    // 注册
    signup: {
      title: '管理员注册',
      username: '用户名',
      email: '邮箱',
      password: '密码',
      confirmPassword: '确认密码',
      submit: '创建账号',
      hasAccount: '已有账号？',
      login: '登录',
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
    searchPlaceholder: '搜索...',
    filterPlaceholder: '筛选...',
    clearFilters: '清除筛选',
    showColumns: '显示列',
    hideColumns: '隐藏列',
    resetColumns: '重置列',
    selectAll: '全选',
    deselectAll: '取消全选',
    selected: '已选 {count} 项',
    selected_one: '已选 {count} 项',
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
