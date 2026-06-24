export default {
  // 操作
  save: '保存',
  cancel: '取消',
  delete: '删除',
  edit: '编辑',
  add: '添加',
  create: '创建',
  update: '更新',
  view: '查看',
  remove: '移除',
  confirm: '确认',
  submit: '提交',
  back: '返回',
  next: '下一步',
  previous: '上一步',
  close: '关闭',
  open: '打开',
  copy: '复制',
  download: '下载',
  upload: '上传',

  // 状态
  loading: '加载中...',
  noData: '暂无数据',
  never: '从未',
  yes: '是',
  no: '否',
  all: '全部',
  none: '无',
  any: '任意',

  // 标签
  actions: {
    label: '操作',
    toggleLanguage: '切换语言',
  },
  status: '状态',
  details: '详情',
  search: '搜索',
  filter: '筛选',
  sort: '排序',
  export: '导出',
  refresh: '刷新',
  retry: '重试',
  select: '选择',
  clear: '清除',
  name: '名称',
  title: '标题',
  description: '描述',
  type: '类型',
  created: '创建时间',
  updated: '更新时间',
  id: 'ID',

  // 时间
  today: '今天',
  yesterday: '昨天',
  thisWeek: '本周',
  lastWeek: '上周',
  minutes: '分钟',

  // 其他标签
  reportedBy: '举报人',
  reportedAt: '举报时间',
  submissions: '提交数',
  solutions: '题解数',
  page: '页',
  saving: '保存中...',
  premium: '高级',
  unpublished: '未发布',
  deleteConfirm: '确认删除',
  clearSelection: '清除选择',
  reasonLabel: '标记原因',
  reasonPlaceholder: '请提供标记原因...',
  noDataAvailable: '暂无数据',

  // 表单字段标注 (TestCaseForm)
  optional: '可选',

  // 标记操作 (EntityActionDialog)
  flag: '标记',
  flagConfirm: '标记',
  flagDescription: '将此内容标记以供管理员审核。',
  flagSuccess: '内容已成功标记',
  flagError: '标记内容失败',

  // 删除操作 (EntityActionDialog)
  deleteDescription: '确定要删除此项吗?此操作无法撤销。',
  deleteDescriptionWithName: '确定要删除"{name}"吗?此操作无法撤销。',
  deleteSuccess: '删除成功',
  deleteError: '删除失败',

  // 原因校验 (EntityActionDialog)
  reasonRequired: '请填写原因',

  // 主题模式标签(AuthThemeToggle 与设置页共用)
  appearance: {
    light: '浅色',
    dark: '深色',
    system: '跟随系统',
  },
} as const
