export default {
  title: '审计日志',
  searchPlaceholder: '搜索审计日志...',
  filterAction: '筛选操作',
  allActions: '全部操作',
  export: '导出',
  noLogs: '暂无日志',
  noLogsDescription: '没有找到符合条件的审计日志记录。',
  systemAction: '系统操作',
  oldValues: '旧值',
  newValues: '新值',
  ipAddress: 'IP 地址',
  userAgent: '用户代理',

  // 列定义
  columns: {
    createdAt: '时间',
    action: '操作',
    entityType: '实体类型',
    performer: '操作者',
    target: '目标',
    ip: 'IP 地址',
    details: '详情',
  },

  // 筛选器
  filters: {
    allActions: '全部操作',
    allEntities: '所有实体',
  },

  // 实体类型
  entityTypes: {
    USER: '用户',
    PROBLEM: '题目',
    CONTEST: '比赛',
    SOLUTION: '题解',
    FORUM_POST: '论坛帖子',
  },

  // 操作类型
  actionTypes: {
    CREATE_USER: '创建用户',
    UPDATE_USER: '更新用户',
    DELETE_USER: '删除用户',
    BAN_USER: '封禁用户',
    UNBAN_USER: '解封用户',
    GRANT_PERMISSION: '授予权限',
    REVOKE_PERMISSION: '撤销权限',
  },

  // 操作
  actions: {
    viewDetails: '查看详情',
    openMenu: '打开菜单',
    create: '创建',
    update: '更新',
    delete: '删除',
    publish: '发布',
    moderate: '审核',
  },

  // 统计标签
  stats: {
    total: '总数',
    create: '创建',
    update: '更新',
    delete: '删除',
    systemAuditTrail: '系统审计追踪',
  },

  // 详情抽屉
  drawer: {
    description: '系统事件的详细记录。',
    notFound: '选择一条日志记录以查看详情',
    system: '系统',
    notAvailable: '无',
    targetEntity: '目标实体',
    userAgent: '用户代理',
    dataChanges: '数据变更',
    noDataChanges: '没有记录数据变更。',
    previousState: '之前状态',
    newState: '新状态',
  },

  // Toast 消息
  toast: {
    loadFailed: '加载审计日志失败',
  },
} as const
