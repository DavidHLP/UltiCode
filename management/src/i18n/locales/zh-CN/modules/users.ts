export default {
  title: '用户管理',
  addUser: '添加用户',
  createUser: '创建用户',
  createDescription: '填写以下信息创建新用户账户',
  searchPlaceholder: '搜索用户名或邮箱...',
  banReasonPrompt: '请输入封禁原因',

  // 列定义
  columns: {
    user: '用户',
    role: '角色',
    status: '状态',
    joined: '注册时间',
    lastLogin: '最后登录',
    bannedAt: '封禁时间',
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

  // 表单
  form: {
    username: '用户名',
    usernamePlaceholder: 'zhangsan',
    email: '邮箱',
    emailPlaceholder: 'user@example.com',
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
    noReasonProvided: '未提供原因',
    unknown: '未知',
  },

  // 统计数据
  stats: {
    solved: '已解决',
    streak: '连续天数',
    never: '从未',
  },

  // 操作
  actions: {
    viewDetails: '查看详情',
    editProfile: '编辑资料',
    resetPassword: '重置密码',
    banUser: '封禁用户',
    unbanUser: '解封用户',
    banUserDescription: '请提供封禁 {username} 的原因。',
    thisUser: '该用户',
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

  clearSelection: '清除选择',
  deleteConfirm: '确定要删除 {count} 个用户吗？',
} as const
