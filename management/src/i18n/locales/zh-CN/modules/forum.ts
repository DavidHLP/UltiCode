export default {
  postsTitle: '论坛帖子',
  detailTitle: '帖子详情',
  searchPlaceholder: '搜索帖子...',

  // 列定义
  columns: {
    id: 'ID',
    title: '标题',
    author: '作者',
    community: '社区',
    status: '状态',
    replies: '回复数',
    views: '浏览数',
    createdAt: '创建时间',
    updatedAt: '更新时间',
  },

  // 状态
  status: {
    all: '全部状态',
    ACTIVE: '活跃',
    CLOSED: '已关闭',
    HIDDEN: '已隐藏',
  },

  // 操作
  actions: {
    view: '查看',
    edit: '编辑',
    delete: '删除',
    pin: '置顶',
    close: '关闭',
    hide: '隐藏',
  },

  // Toast 消息
  toast: {
    loadFailed: '加载帖子失败',
    deleteSuccess: '帖子删除成功',
    deleteFailed: '删除帖子失败',
  },
} as const
