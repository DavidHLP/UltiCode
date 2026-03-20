export default {
  title: '审计报告',
  description: '系统活动分析与统计',

  // 筛选区域
  filters: '筛选器',
  startDate: '开始日期',
  endDate: '结束日期',
  performer: '操作者 ID',
  performerPlaceholder: '输入操作者 ID...',
  applyFilters: '应用筛选',
  export: '导出 CSV',

  // 统计概览
  totalActions: '总操作数',
  allTime: '全部时间',
  uniqueEntities: '唯一实体',
  entityTypes: '实体类型',
  activePerformers: '活跃操作者',
  users: '用户',

  // 热门操作者区域
  topPerformers: '热门操作者',
  actions: '次操作',

  // 按实体统计区域
  actionsByEntity: '按实体统计操作',
} as const
