export default {
  // Layout
  layout: {
    systemOnline: '系统在线',
    codingConsole: '管理\n控制台',
    codingConsoleSubtitle: '// 平台管理的精密工具',
  },

  // Login page
  login: {
    terminal: 'login.sh',
    title: '登录到您的账户',
    subtitle: '请输入您的用户名和密码登录',
    username: '用户名',
    usernamePlaceholder: '请输入用户名',
    password: '密码',
    passwordPlaceholder: '请输入密码',
    forgotPassword: '忘记密码？',
    submit: '登录',
    submitting: '登录中...',
    orContinueWith: '或使用以下方式继续',
    loginWithGithub: '使用 GitHub 登录',
    loginWithGoogle: '使用 Google 登录',
    noAccount: '还没有账户？',
    signUp: '注册',
    rememberMe: '记住我',
  },

  // Register page
  register: {
    terminal: 'register.sh',
    title: '创建账户',
    subtitle: '请输入您的信息以创建新账户',
    username: '用户名',
    usernamePlaceholder: '请输入用户名',
    name: '显示名称',
    namePlaceholder: '您希望如何展示您的名称？',
    email: '邮箱',
    emailPlaceholder: '请输入您的邮箱地址',
    password: '密码',
    passwordPlaceholder: '请输入密码',
    confirmPassword: '确认密码',
    confirmPasswordPlaceholder: '再次输入密码',
    submit: '注册',
    submitting: '创建账户中...',
    alreadyHaveAccount: '已经有账户了？',
    signIn: '登录',
    login: '登录',
    termsAgreement: '注册即表示您同意我们的',
    termsOfService: '服务条款',
    and: '和',
    privacyPolicy: '隐私政策',
  },

  // Forgot password page
  forgotPassword: {
    terminal: 'reset-request.sh',
    title: '忘记密码',
    subtitle: '请输入您的邮箱地址，我们会发送重置链接',
    email: '邮箱',
    emailPlaceholder: '请输入您注册的邮箱',
    submit: '发送重置链接',
    submitting: '发送中...',
    backToLogin: '返回登录',
    rememberPassword: '记得密码了？',
    successMessage: '如果邮箱存在，重置链接已发送到您的收件箱',
  },

  // Reset password page
  resetPassword: {
    terminal: 'reset-password.sh',
    title: '重置密码',
    subtitle: '请输入您的新密码',
    newPassword: '新密码',
    newPasswordPlaceholder: '请输入新密码',
    confirmPassword: '确认新密码',
    confirmPasswordPlaceholder: '再次输入新密码',
    submit: '重置密码',
    submitting: '重置中...',
    successMessage: '密码重置成功，请使用新密码登录',
  },

  // Toast messages
  messages: {
    loginSuccess: '登录成功',
    loginFailed: '登录失败',
    registerSuccess: '账户创建成功',
    registerFailed: '注册失败',
    logoutSuccess: '已退出登录',
    passwordResetSuccess: '密码重置成功',
    passwordResetFailed: '密码重置失败',
    emailSent: '邮件已发送',
    requestFailed: '请求处理失败',
    invalidCredentials: '用户名或密码错误',
    accountNotFound: '账户不存在',
    emailAlreadyExists: '邮箱已被使用',
    usernameAlreadyExists: '用户名已被占用',
    sessionExpired: '会话已过期，请重新登录',
    passwordsDoNotMatch: '两次输入的密码不一致',
    contactAdmin: '暂不开放自助注册，请联系管理员开通账号。',
  },

  // Guest user
  guest: {
    name: '访客',
    loginToContinue: '请登录后继续',
    welcome: '欢迎，登录以访问所有功能',
  },

  // Validation messages
  validation: {
    usernameRequired: '请输入用户名',
    usernameMinLength: '用户名至少需要 3 个字符',
    usernameMaxLength: '用户名不能超过 20 个字符',
    usernameInvalid: '用户名只能包含字母、数字和下划线',
    emailRequired: '请输入邮箱',
    emailInvalid: '请输入有效的邮箱地址',
    passwordRequired: '请输入密码',
    passwordMinLength: '密码至少需要 6 个字符',
    passwordMaxLength: '密码不能超过 50 个字符',
    passwordMismatch: '两次输入的密码不一致',
  },
} as const
