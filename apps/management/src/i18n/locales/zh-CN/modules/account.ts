export default {
  title: '账户设置',
  subtitle: '管理您的个人资料和偏好设置',

  sections: {
    basic: '基本信息',
    about: '个人简介',
    social: '社交链接',
    preferences: '偏好设置',
    security: '安全设置',
    accountInfo: '账户信息',
  },

  fields: {
    name: '姓名',
    email: '邮箱',
    avatar: '头像URL',
    company: '公司',
    location: '位置',
    bio: '个人简介',
    github: 'GitHub',
    twitter: 'Twitter',
    website: '个人网站',
    preferredLanguage: '偏好语言',
    currentPassword: '当前密码',
    newPassword: '新密码',
    confirmPassword: '确认密码',
    role: '角色',
    joinedAt: '注册时间',
    lastLogin: '最后登录',
    username: '用户名',
  },

  actions: {
    save: '保存更改',
    cancel: '取消',
    changePassword: '修改密码',
  },

  toast: {
    saveSuccess: '个人资料已更新',
    saveFailed: '保存失败',
    passwordSuccess: '密码已更新',
    passwordFailed: '密码修改失败',
    passwordsDoNotMatch: '两次输入的密码不一致',
  },
} as const
