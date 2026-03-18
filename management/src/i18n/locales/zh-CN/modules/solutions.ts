export default {
  title: '题解管理',
  detailTitle: '题解详情',
  searchPlaceholder: '搜索题解...',

  // 列定义
  columns: {
    id: 'ID',
    title: '标题',
    problem: '题目',
    author: '作者',
    status: '状态',
    votes: '点赞数',
    views: '浏览数',
    createdAt: '创建时间',
    updatedAt: '更新时间',
  },

  // 状态
  status: {
    all: '全部状态',
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档',
  },

  // 操作
  actions: {
    view: '查看',
    edit: '编辑',
    delete: '删除',
    approve: '批准',
    reject: '拒绝',
  },

  // Toast 消息
  toast: {
    loadFailed: '加载题解失败',
    deleteSuccess: '题解删除成功',
    deleteFailed: '删除题解失败',
  },
} as const
