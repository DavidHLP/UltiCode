export default {
  title: '系统通知',
  searchPlaceholder: '搜索通知...',
  newNotification: '发送通知',
  allTypes: '全部类型',
  clearSelection: '清除选择',

  // 列定义
  columns: {
    title: '标题',
    type: '类型',
    status: '状态',
    createdAt: '创建时间',
    sentAt: '发送时间',
    sentBy: '发送人',
    target: '目标',
    category: '分类',
    actions: '操作',
  },

  // 类型标签
  types: {
    SYSTEM: '系统',
    CONTEST: '比赛',
    SUBMISSION: '提交',
    COMMENT: '评论',
    REPLY: '回复',
    MENTION: '提及',
  },

  // 分类标签
  categories: {
    SYSTEM: '系统',
    ANNOUNCEMENT: '公告',
    PROMOTION: '推广',
    UPDATE: '更新',
    WARNING: '警告',
  },

  // 目标标签
  targets: {
    ALL: '所有用户',
    USERS: '指定用户',
  },

  // 统计
  stats: {
    total: '总计',
    system: '系统',
    contest: '比赛',
    submission: '提交',
    other: '其他',
  },

  sentAt: '发送时间',
  sentBy: '发送人',

  // 删除对话框
  delete: {
    title: '删除通知',
    description: '确定要删除这条系统通知吗？',
    confirm: '删除',
    cancel: '取消',
  },

  deleteSuccess: '通知已删除',
  deleteError: '删除通知失败',

  // 对话框
  dialog: {
    createTitle: '创建通知',
    createDescription: '编写并发送新的系统通知给用户。',
    sending: '发送中...',
    sendNotification: '发送通知',
  },

  // 表单
  form: {
    title: '标题',
    titlePlaceholder: '输入通知标题',
    content: '内容',
    contentPlaceholder: '输入通知内容...',
    type: '类型',
    targetType: '目标类型',
    targetAll: '所有用户',
    targetUser: '指定用户',
    targetUserPlaceholder: '输入用户ID，多个用逗号分隔',
    targetTypePlaceholder: '选择目标用户类型',
    atLeastOneUserId: '请输入至少一个用户ID',

    // 新表单字段
    messageContent: '消息内容',
    messageContentDescription: '输入通知的标题和内容。',
    notificationTitle: '通知标题',
    notificationTitlePlaceholder: '输入简洁的标题...',
    notificationContent: '通知内容',
    notificationContentPlaceholder: '输入通知消息...',
    classification: '分类信息',
    classificationDescription: '选择此通知的类型和分类。',
    selectType: '选择类型',
    category: '分类',
    selectCategory: '选择分类',
    targetAudience: '目标受众',
    targetAudienceDescription: '选择谁将收到此通知。',
    allUsers: '所有用户',
    specificUsers: '指定用户',
    userIds: '用户ID',
    userIdsPlaceholder: '输入用户ID，用逗号分隔（如：user1, user2, user3）',
  },

  // Toast 消息
  toast: {
    createSuccess: '通知发送成功',
    createFailed: '发送通知失败',
    sentSuccessfully: '通知发送成功',
    failedToSend: '发送通知失败',
    updateSuccess: '通知更新成功',
    updateFailed: '更新通知失败',
    deleteSuccess: '通知删除成功',
    deleteFailed: '删除通知失败',
  },

  // 错误
  errors: {
    loadFailed: '加载通知失败',
    notFound: '未找到通知',
  },

  // 空状态
  empty: {
    title: '未找到通知',
    description: '创建新通知以开始使用。',
  },
} as const
