export default {
  title: '用户管理',
  addUser: '添加用户',
  createUser: '创建用户',
  createDescription: '填写以下信息创建新用户账户',
  searchPlaceholder: '搜索用户名或邮箱...',
  banReasonPrompt: '请输入封禁原因',
  editUser: '编辑用户',
  editDescription: '编辑用户信息并更新权限设置',

  // 列定义
  columns: {
    user: '用户',
    role: '角色',
    status: '状态',
    joined: '注册时间',
    lastLogin: '最后登录',
    bannedUntil: '封禁截止',
    username: '用户名',
  },

  // 筛选器
  filters: {
    allRoles: '全部角色',
    allStatus: '全部状态',
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

  // 状态 Badge
  status: {
    active: '活跃',
    inactive: '未激活',
    banned: '已封禁',
  },

  // 表单
  form: {
    sections: {
      general: '基本信息',
      accessControl: '访问控制',
      securityAccess: '安全与访问',
    },
    username: '用户名',
    usernamePlaceholder: 'zhangsan',
    email: '邮箱',
    emailPlaceholder: "user{'@'}example.com",
    password: '密码',
    passwordPlaceholder: '••••••••',
    confirmPassword: '确认密码',
    role: '角色',
    isActive: '激活状态',
    banReason: '封禁原因',
    banReasonPlaceholder: '请输入封禁原因...',
    name: '姓名',
    namePlaceholder: '张三',
    fullName: '姓名',
    fullNamePlaceholder: '张三',
    bio: '个人简介',
    bioPlaceholder: '一句话介绍自己...',
    status: '状态',
    creating: '创建中...',
    createUser: '创建用户',
    saveChanges: '保存更改',
    saving: '保存中...',
    noReasonProvided: '未提供原因',
    unknown: '未知',
    newPassword: '新密码',
    newPasswordPlaceholder: '请输入新密码',
    targetUser: '目标用户',
  },

  // 统计数据
  stats: {
    solved: '已解决',
    streak: '连续天数',
    never: '从未',
    solutions: '题解数',
    submissions: '提交数',
    accepted: '通过数',
    acceptanceRate: '通过率',
    userManagement: '用户管理',
    total: '总计',
    active: '活跃',
    banned: '已封禁',
  },

  // 操作
  actions: {
    viewDetails: '查看详情',
    editProfile: '编辑资料',
    resetPassword: '重置密码',
    banUser: '封禁用户',
    bulkBanUser: '批量封禁用户',
    unbanUser: '解封用户',
    unbanUserDescription: '确定要解封 {username} 吗?',
    banUserDescription: '请提供封禁 {username} 的原因。',
    thisUser: '该用户',
    resetPasswordDescription: '为 {username} 设置新的登录密码。',
    resetPasswordWarning: '密码重置会立即生效，请通过安全渠道通知用户。',
    cancel: '取消',
    resetting: '重置中...',
    resetPasswordAction: '确认重置密码',
    confirmBan: '确认封禁',
    confirmUnban: '确认解封',
    deleteUsers: '批量删除用户',
  },

  // 批量操作
  bulkActions: {
    bulkBan: '批量封禁',
    bulkUnban: '批量解封',
    bulkDelete: '批量删除',
  },

  // Toast 消息
  toast: {
    createSuccess: '用户创建成功',
    createFailed: '创建用户失败',
    updateSuccess: '用户更新成功',
    updateFailed: '更新用户失败',
    deleteSuccess: '用户删除成功',
    deleteFailed: '删除用户失败',
    banSuccess: '用户已封禁',
    banFailed: '封禁用户失败',
    unbanSuccess: '用户已解封',
    unbanFailed: '解封用户失败',
    resetPasswordSuccess: '密码重置成功',
    resetPasswordFailed: '密码重置失败',
    resetPasswordFailedDescription: '尝试更新密码时发生错误。',
    resetPasswordValidationFailed: '密码太短',
    resetPasswordValidationFailedDescription: '密码必须至少包含 8 个字符。',
    bulkBanFailed: '批量封禁失败',
    bulkUnbanFailed: '批量解封失败',
    bulkDeleteFailed: '批量删除失败',
  },

  // 对话框
  dialogs: {
    createTitle: '创建新用户',
    editTitle: '编辑用户',
    deleteTitle: '确认删除',
    deleteDescription: '确定要删除用户 "{username}" 吗？此操作不可撤销。',
    resetPasswordTitle: '重置密码',
    resetPasswordDescription: '为 {username} 设置新密码。',
  },

  // 详情
  details: {
    profile: '个人资料',
    activity: '活动记录',
    submissions: '提交记录',
    statistics: '统计数据',
    title: '用户详情',
    description: '查看用户的完整信息',
    notFound: '未找到用户',
  },

  drawer: {
    sections: {
      profile: '用户资料',
      performance: '性能统计',
      account: '账户信息',
    },
  },

  clearSelection: '清除选择',
  deleteConfirm: '确定要删除 {count} 个用户吗？',
  typeToConfirm: '请输入 {text} 以确认',
  typeConfirmLabel: '为确认操作,请输入下方文字:',
} as const
