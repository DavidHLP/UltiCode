export default {
  title: '评论管理',
  searchPlaceholder: '搜索评论内容...',
  clearSelection: '清除选择',
  stats: {
    total: '总计',
    flagged: '被标记',
    forum: '论坛',
    solution: '题解',
    deleted: '已删除',
    commentModeration: '评论管理',
  },

  // 列
  columns: {
    comment: '评论',
    author: '作者',
    created: '创建时间',
    status: '状态',
    type: '类型',
    content: '内容',
  },

  type: {
    forum: '论坛',
    solution: '题解',
    unknown: '未知',
  },

  status: {
    unknown: '未知',
    flagged: '已标记',
    deleted: '已删除',
    active: '正常',
  },

  detail: {
    title: '评论详情',
    parent: '父内容',
    metadata: '元数据',
  },

  error: {
    loadingComment: '加载评论失败',
    commentNotFound: '评论不存在',
    notFoundDescription: '您查找的评论不存在或已被删除。',
    back: '返回',
    retry: '重试',
    backToComments: '返回评论列表',
  },

  filters: {
    type: '评论类型',
    allTypes: '全部类型',
    flagStatus: '标记状态',
    deletedStatus: '删除状态',
    all: '全部',
    flagged: '已标记',
    clean: '正常',
    deleted: '已删除',
    active: '活跃',
  },

  bulkActions: {
    bulkUnflag: '批量取消标记',
    bulkDelete: '批量删除',
  },

  // 操作
  actions: {
    view: '查看',
    delete: '删除',
    flag: '标记',
    unflag: '取消标记',
    viewDetails: '查看详情',
    noPermission: '无权限',
  },

  deleteConfirm: '确定要删除 {count} 条评论吗？',

  delete: {
    title: '删除评论',
    description: '确定要删除这条评论吗？此操作不可撤销。',
    confirm: '确认删除',
    cancel: '取消',
  },

  flag: {
    title: '标记评论',
    description: '请提供标记这条评论的原因。',
    confirm: '确认标记',
    cancel: '取消',
    reasonLabel: '标记原因',
    reasonPlaceholder: '请输入标记原因...',
  },

  toast: {
    deletedSuccessfully: '评论已删除',
    failedToDelete: '删除评论失败',
    flaggedSuccessfully: '评论已标记',
    failedToFlag: '标记评论失败',
    unflaggedSuccessfully: '已取消标记',
    failedToUnflag: '取消标记失败',
    bulkUnflaggedSuccessfully: '已批量取消标记',
    failedToBulkUnflag: '批量取消标记失败',
    bulkDeletedSuccessfully: '已批量删除',
    failedToBulkDelete: '批量删除失败',
    reasonRequired: '请提供标记原因',
  },
} as const
