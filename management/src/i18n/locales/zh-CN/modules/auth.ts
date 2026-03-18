export default {
  // 登录
  login: {
    title: '登录',
    subtitle: '管理后台',
    username: '用户名',
    usernamePlaceholder: 'admin',
    password: '密码',
    passwordPlaceholder: '••••••••',
    rememberMe: '记住我',
    forgotPassword: '忘记密码？',
    submit: '登录',
    loggingIn: '登录中...',
    invalidCredentials: '用户名或密码错误',
    accountDisabled: '账户已被禁用',
    success: '登录成功',
    error: '登录失败',
  },

  // 登出
  logout: {
    title: '退出登录',
    confirm: '确定要退出登录吗？',
    success: '已退出登录',
    error: '退出登录失败',
  },

  // 密码重置
  resetPassword: {
    title: '重置密码',
    email: '邮箱',
    emailPlaceholder: 'admin@example.com',
    submit: '发送重置链接',
    sending: '发送中...',
    success: '重置链接已发送到您的邮箱',
    error: '发送重置链接失败',
    backToLogin: '返回登录',
    newPassword: '新密码',
    confirmPassword: '确认密码',
    passwordMismatch: '两次密码输入不一致',
    passwordTooShort: '密码至少需要 8 个字符',
    passwordResetSuccess: '密码重置成功',
    passwordResetError: '密码重置失败',
  },

  // 会话管理
  session: {
    expired: '会话已过期，请重新登录',
    invalid: '无效的会话，请重新登录',
  },

  // 权限
  permissions: {
    denied: '权限不足',
    noAccess: '您没有权限访问此页面',
    noAction: '您没有权限执行此操作',
  },
} as const
