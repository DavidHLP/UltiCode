export default {
  // Layout
  layout: {
    systemOnline: "系统在线",
    codingConsole: "在线编程\n控制台",
    codingConsoleSubtitle: "练习算法，提升编程能力",
  },

  // Login page
  login: {
    terminal: "login.sh",
    title: "登录您的账户",
    subtitle: "输入您的用户名和密码登录",
    username: "用户名",
    usernamePlaceholder: "请输入用户名",
    password: "密码",
    passwordPlaceholder: "请输入密码",
    forgotPassword: "忘记密码？",
    submit: "登录",
    submitting: "登录中...",
    orContinueWith: "或使用以下方式继续",
    loginWithGithub: "使用 GitHub 登录",
    loginWithGoogle: "使用 Google 登录",
    noAccount: "还没有账户？",
    signUp: "注册",
    rememberMe: "记住我",
  },

  // Register page
  register: {
    terminal: "register.sh",
    title: "创建账户",
    subtitle: "输入您的信息创建新账户",
    username: "用户名",
    usernamePlaceholder: "请输入用户名",
    name: "显示名称",
    namePlaceholder: "您希望如何展示您的名称？",
    email: "邮箱",
    emailPlaceholder: "请输入邮箱地址",
    password: "密码",
    passwordPlaceholder: "请输入密码",
    confirmPassword: "确认密码",
    confirmPasswordPlaceholder: "请再次输入密码",
    submit: "注册",
    submitting: "创建账户中...",
    alreadyHaveAccount: "已有账户？",
    signIn: "登录",
    login: "登录",
    termsAgreement: "注册即表示您同意我们的",
    termsOfService: "服务条款",
    and: "和",
    privacyPolicy: "隐私政策",
  },

  // Forgot password page
  forgotPassword: {
    terminal: "reset-request.sh",
    title: "忘记密码",
    subtitle: "输入您的邮箱地址，我们将发送重置链接",
    email: "邮箱",
    emailPlaceholder: "请输入注册时使用的邮箱",
    submit: "发送重置链接",
    submitting: "发送中...",
    backToLogin: "返回登录",
    rememberPassword: "记得您的密码？",
    successMessage: "如果该邮箱存在，重置链接已发送到您的邮箱",
  },

  // Reset password page
  resetPassword: {
    terminal: "reset-password.sh",
    title: "重置密码",
    subtitle: "请输入您的新密码",
    newPassword: "新密码",
    newPasswordPlaceholder: "请输入新密码",
    confirmPassword: "确认新密码",
    confirmPasswordPlaceholder: "请再次输入新密码",
    submit: "重置密码",
    submitting: "重置中...",
    successMessage: "密码重置成功，请使用新密码登录",
  },

  // Toast messages
  messages: {
    loginSuccess: "登录成功",
    loginFailed: "登录失败",
    registerSuccess: "账户创建成功",
    registerFailed: "注册失败",
    logoutSuccess: "已退出登录",
    passwordResetSuccess: "密码重置成功",
    passwordResetFailed: "密码重置失败",
    emailSent: "邮件已发送",
    requestFailed: "处理请求失败",
    invalidCredentials: "用户名或密码错误",
    accountNotFound: "账户不存在",
    emailAlreadyExists: "邮箱已被使用",
    usernameAlreadyExists: "用户名已被使用",
    sessionExpired: "登录已过期，请重新登录",
    passwordsDoNotMatch: "两次输入的密码不一致",
  },

  // Guest user
  guest: {
    name: "游客",
    loginToContinue: "登录以继续",
    welcome: "欢迎登录，探索全部功能",
  },

  // Validation messages
  validation: {
    usernameRequired: "请输入用户名",
    usernameMinLength: "用户名至少需要 3 个字符",
    usernameMaxLength: "用户名最多 20 个字符",
    usernameInvalid: "用户名只能包含字母、数字和下划线",
    emailRequired: "请输入邮箱地址",
    emailInvalid: "请输入有效的邮箱地址",
    passwordRequired: "请输入密码",
    passwordMinLength: "密码至少需要 6 个字符",
    passwordMaxLength: "密码最多 50 个字符",
    passwordMismatch: "两次输入的密码不一致",
  },
} as const;
