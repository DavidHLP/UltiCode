export default {
  title: '系统设置',
  description: '管理系统配置',

  // 标签页
  tabs: {
    general: '常规',
    email: '邮件',
    rateLimits: '频率限制',
    uploads: '上传',
    features: '功能开关',
    security: '安全',
    notifications: '通知',
    appearance: '外观',
    language: '语言',
    advanced: '高级',
  },

  // 常规设置
  general: {
    title: '站点设置',
    description: '配置基本站点信息',
  },

  siteName: '站点名称',
  siteDescription: '站点描述',
  siteUrl: '站点 URL',
  contactEmail: '联系邮箱',
  timezone: '时区',

  // 用户注册
  userRegistration: {
    title: '用户注册',
    description: '控制用户注册设置',
    enableRegistrations: '开放注册',
    enableRegistrationsDescription: '允许新用户在平台上注册',
    requireEmailVerification: '需要邮箱验证',
    requireEmailVerificationDescription: '用户必须验证邮箱后才能访问平台',
  },

  // 系统状态
  systemStatus: {
    title: '系统状态',
    description: '控制系统维护模式',
    maintenanceMode: '维护模式',
    maintenanceModeDescription: '将站点置于维护模式。仅管理员可访问。',
    maintenanceMessage: '维护消息',
  },

  // 邮件设置
  email: {
    title: '邮件配置',
    description: '配置 SMTP 设置以发送邮件',
    smtpHost: 'SMTP 主机',
    smtpPort: 'SMTP 端口',
    smtpUser: 'SMTP 用户名',
    smtpPassword: 'SMTP 密码',
    smtpFrom: '发件人邮箱',
    smtpFromName: '发件人名称',
    smtpSecure: '使用 TLS',
    smtpSecureDescription: '为 SMTP 连接启用 TLS 加密',
  },

  // 频率限制
  rateLimits: {
    title: '频率限制',
    description: '配置 API 频率限制设置',
    api: 'API 频率限制',
    apiDescription: '每分钟最大 API 请求数',
    submission: '提交频率限制',
    submissionDescription: '每分钟最大代码提交数',
    auth: '认证频率限制',
    authDescription: '每分钟最大认证尝试数',
    upload: '上传频率限制',
    uploadDescription: '每分钟最大文件上传数',
  },

  // 上传设置
  uploads: {
    title: '上传设置',
    description: '配置文件上传限制和允许类型',
    maxSize: '最大文件大小',
    maxSizeDescription: '上传文件的最大大小（如：10 MB）',
    maxFiles: '最大文件数',
    maxFilesDescription: '一次可上传的最大文件数量',
    allowedTypes: '允许的文件类型',
    allowedTypesDescription: '允许的文件扩展名，用逗号分隔',
  },

  // 功能开关
  features: {
    title: '功能开关',
    description: '启用或禁用平台功能',
    contest: '比赛',
    contestDescription: '启用比赛功能',
    forum: '论坛',
    forumDescription: '启用社区论坛',
    solutions: '题解',
    solutionsDescription: '启用题解分享',
    subscriptions: '订阅',
    subscriptionsDescription: '启用订阅套餐',
    achievements: '成就',
    achievementsDescription: '启用成就系统',
    notifications: '通知',
    notificationsDescription: '启用通知系统',
    bookmarks: '收藏',
    bookmarksDescription: '启用收藏功能',
    problemLists: '题单',
    problemListsDescription: '启用自定义题单',
  },

  // 操作
  actions: {
    title: '操作',
    clearCache: '清除缓存',
    resetToDefaults: '恢复默认',
    resetConfirmTitle: '确定恢复默认设置？',
    resetConfirmDescription: '这将把所有设置恢复为默认值。此操作无法撤销。',
    resetConfirm: '重置',
    saveChanges: '保存更改',
    saving: '保存中...',
  },

  // 安全设置
  security: {
    passwordPolicy: '密码策略',
    twoFactorAuth: '两步验证',
    sessionTimeout: '会话超时',
    maxLoginAttempts: '最大登录尝试',
  },

  // 通知设置
  notifications: {
    emailNotifications: '邮件通知',
    enableEmail: '启用邮件通知',
    smtpSettings: 'SMTP 设置',
  },

  // 外观设置
  appearance: {
    theme: '主题',
    themeDescription: '配置后台管理系统的界面视觉主题样式。',
    light: '浅色',
    dark: '深色',
    system: '跟随系统',
    primaryColor: '主色调',
  },

  // 语言设置
  language: {
    defaultLanguage: '默认语言',
    supportedLanguages: '支持的语言',
  },

  // 高级设置
  advanced: {
    maintenanceMode: '维护模式',
    debugMode: '调试模式',
    cacheSettings: '缓存设置',
    clearCache: '清除缓存',
  },

  // Toast 消息
  toast: {
    saveSuccess: '设置保存成功',
    saveFailed: '保存设置失败',
    loadFailed: '加载设置失败',
    cacheCleared: '缓存已清除',
    clearCacheFailed: '清除缓存失败',
    resetFailed: '重置设置失败',
    resetSuccess: '设置已重置',
  },

  // 按钮
  buttons: {
    save: '保存设置',
    reset: '重置',
    testEmail: '测试邮件',
    clearCache: '清除缓存',
  },
} as const
