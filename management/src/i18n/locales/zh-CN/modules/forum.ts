export default {
  title: '论坛帖子',
  postsTitle: '论坛帖子',
  detailTitle: '帖子详情',
  searchPlaceholder: '搜索帖子...',
  clearSelection: '清除选择',
  stats: {
    total: '总计',
    pinned: '已置顶',
    locked: '已锁定',
    flagged: '已标记',
    postManagement: '帖子管理',
  },

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
    stats: '统计',
    created: '创建时间',
    actions: '操作',
  },

  // 状态
  status: {
    all: '全部状态',
    ACTIVE: '活跃',
    active: '活跃',
    CLOSED: '已关闭',
    HIDDEN: '已隐藏',
    pinned: '已置顶',
    locked: '已锁定',
    flagged: '已标记',
    deleted: '已删除',
  },

  // 操作
  actions: {
    view: '查看',
    viewDetails: '查看详情',
    edit: '编辑',
    delete: '删除',
    pin: '置顶',
    unpin: '取消置顶',
    close: '关闭',
    lock: '锁定',
    unlock: '解锁',
    hide: '隐藏',
    flag: '标记',
    unflag: '取消标记',
  },

  // 批量操作
  bulkActions: {
    bulkPin: '批量置顶',
    bulkLock: '批量锁定',
    bulkUnflag: '批量取消标记',
    bulkDelete: '批量删除',
  },

  // 筛选器
  filters: {
    all: '全部',
    community: '社区',
    allCommunities: '全部社区',
    flagStatus: '标记状态',
    flagged: '已标记',
    clean: '正常',
    pinned: '置顶',
    pinnedOnly: '仅置顶',
    unpinnedOnly: '未置顶',
    locked: '锁定',
    lockedOnly: '仅锁定',
    unlockedOnly: '未锁定',
    deleted: '已删除',
    deletedOnly: '仅已删除',
    activeOnly: '仅活跃',
  },

  // 标签页
  tabs: {
    overview: '概览',
    comments: '评论',
    audit: '操作记录',
  },

  // 概览
  overview: {
    unknown: '未知',
  },

  // 详情视图
  detail: {
    content: '内容',
    views: '浏览量',
    comments: '评论数',
    upvotes: '点赞数',
    downvotes: '踩数',
    timeline: '时间线',
    created: '创建时间',
    updated: '更新时间',
    flagInformation: '标记信息',
    reason: '原因',
    flaggedOn: '标记时间',
    deletionInformation: '删除信息',
    deletedOn: '删除时间',
    noContentAvailable: '暂无内容',
    identifiers: '标识符',
    postId: '帖子ID',
    authorId: '作者ID',
    communityId: '社区ID',
  },

  // 抽屉
  drawer: {
    title: '帖子详情',
    description: '查看帖子信息',
    authorCommunity: '作者与社区',
    unknownCommunity: '未知社区',
    contentPreview: '内容预览',
    postNotFound: '未找到帖子',
  },

  // 评论标签
  comments: {
    postComments: '帖子评论',
    noCommentsFound: '该帖子暂无评论',
  },

  // 审计
  audit: {
    description: '对此帖子的管理操作和变更记录',
    noAuditHistory: '暂无操作记录',
    from: '原值',
    to: '新值',
    performed: '执行了',
  },

  // 审计操作
  auditActions: {
    PIN_FORUM_POST: '置顶帖子',
    UNPIN_FORUM_POST: '取消置顶',
    LOCK_FORUM_POST: '锁定帖子',
    UNLOCK_FORUM_POST: '解锁帖子',
    DELETE_FORUM_POST: '删除帖子',
    FLAG_FORUM_POST: '标记帖子',
    UNFLAG_FORUM_POST: '取消标记',
    BULK_DELETE_FORUM: '批量删除',
    BULK_PIN_FORUM: '批量置顶',
  },

  // 删除对话框
  delete: {
    title: '删除帖子',
    description: '确定要删除此帖子吗？此操作不可撤销。',
    confirm: '删除',
    cancel: '取消',
  },

  // 标记对话框
  flag: {
    title: '标记帖子',
    description: '请提供标记此帖子的原因。',
    confirm: '标记',
    cancel: '取消',
    reasonLabel: '原因',
    reasonPlaceholder: '请输入标记原因...',
  },

  // 错误消息
  error: {
    loadingPost: '加载帖子失败',
    postNotFound: '未找到帖子',
    notFoundDescription: '您访问的帖子不存在或已被删除。',
    back: '返回',
    retry: '重试',
    backToForumPosts: '返回论坛',
  },

  // 确认消息
  deleteConfirm: '确定要删除 {count} 篇帖子吗？',

  // Toast 消息
  toast: {
    loadFailed: '加载帖子失败',
    deleteSuccess: '帖子删除成功',
    deleteFailed: '删除帖子失败',
    deletedSuccessfully: '帖子删除成功',
    failedToDelete: '删除帖子失败',
    pinnedSuccessfully: '帖子置顶成功',
    unpinnedSuccessfully: '已取消置顶',
    failedToUpdatePin: '更新置顶状态失败',
    lockedSuccessfully: '帖子锁定成功',
    unlockedSuccessfully: '已解锁帖子',
    failedToUpdateLock: '更新锁定状态失败',
    flaggedSuccessfully: '帖子标记成功',
    failedToFlag: '标记帖子失败',
    unflaggedSuccessfully: '已取消标记',
    failedToUnflag: '取消标记失败',
    bulkPinnedSuccessfully: '批量置顶成功',
    bulkLockedSuccessfully: '批量锁定成功',
    bulkUnflaggedSuccessfully: '批量取消标记成功',
    bulkDeletedSuccessfully: '批量删除成功',
    reasonRequired: '请输入原因',
  },
} as const
