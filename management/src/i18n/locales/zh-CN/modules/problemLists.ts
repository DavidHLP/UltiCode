export default {
  title: '题单管理',
  createTitle: '创建题单',
  editTitle: '编辑题单',
  searchPlaceholder: '搜索题单...',

  // 列定义
  columns: {
    id: 'ID',
    title: '标题',
    author: '作者',
    status: '状态',
    problemCount: '题目数',
    isPublic: '公开',
    isFeatured: '精选',
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
    publish: '发布',
    feature: '精选',
  },

  // Toast 消息
  toast: {
    loadFailed: '加载题单失败',
    createSuccess: '题单创建成功',
    createFailed: '创建题单失败',
    updateSuccess: '题单更新成功',
    updateFailed: '更新题单失败',
    deleteSuccess: '题单删除成功',
    deleteFailed: '删除题单失败',
  },
} as const
