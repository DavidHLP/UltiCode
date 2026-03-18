export default {
  title: '系统设置',
  description: '管理系统配置',

  // 标签页
  tabs: {
    general: '常规',
    security: '安全',
    notifications: '通知',
    appearance: '外观',
    language: '语言',
    advanced: '高级',
  },

  // 常规设置
  general: {
    siteName: '站点名称',
    siteDescription: '站点描述',
    siteUrl: '站点 URL',
    contactEmail: '联系邮箱',
    timezone: '时区',
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
    cacheCleared: '缓存已清除',
  },

  // 按钮
  buttons: {
    save: '保存设置',
    reset: '重置',
    testEmail: '测试邮件',
    clearCache: '清除缓存',
  },
} as const
