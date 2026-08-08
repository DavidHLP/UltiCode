export default {
  title: '提交记录',
  searchPlaceholder: '搜索提交记录...',
  allStatuses: '全部状态',
  allLanguages: '全部语言',
  clearSelection: '清除选择',

  // 列标题
  id: 'ID',
  problem: '题目',
  user: '用户',
  language: '语言',
  status: '状态',
  runtime: '运行时间',
  memory: '内存占用',
  codeLength: '代码长度',
  submittedAt: '提交时间',
  score: '分数',
  result: '结果',

  // 详情视图
  detail: '提交详情',
  detailTitle: '提交详情',
  code: '代码',
  notes: '备注',
  testCases: '测试用例',
  compileOutput: '编译输出',
  judgeResult: '评测结果',
  noCode: '无代码',
  noNotes: '无备注',

  // 状态标签
  statusLabels: {
    ACCEPTED: '通过',
    WRONG_ANSWER: '答案错误',
    TIME_LIMIT_EXCEEDED: '超时',
    MEMORY_LIMIT_EXCEEDED: '内存超限',
    RUNTIME_ERROR: '运行时错误',
    COMPILE_ERROR: '编译错误',
    PENDING: '等待中',
    JUDGING: '评测中',
    SYSTEM_ERROR: '系统错误',
    SANDBOX_ERROR: '沙箱错误',
    OUTPUT_LIMIT_EXCEEDED: '输出超限',
    PRESENTATION_ERROR: '格式错误',
  },

  // 统计
  stats: {
    total: '总计',
    pending: '等待中',
    accepted: '已通过',
    acceptedRate: '通过率',
    topLanguage: '热门语言',
    submissionManagement: '提交记录',
  },

  // 操作
  actions: {
    view: '查看详情',
    rejudge: '重判',
    batchRejudge: '批量重判',
    viewCode: '查看代码',
    copyCode: '复制代码',
    downloadCode: '下载代码',
  },

  // 重判
  batchRejudge: '批量重判',
  rejudge: '重判',
  rejudgeTitle: '确认重判',
  rejudgeDescription: '确定要重新评判这条提交吗？',
  batchRejudgeTitle: '批量重判',
  batchRejudgeDescription: '确定要重新评判选中的 {count} 条提交吗？',
  rejudgeSuccess: '重判成功',
  rejudgeError: '重判失败: {error}',

  // 错误
  loadDetailError: '加载提交详情失败',
  loadError: '加载提交记录失败',
  notFound: '未找到提交记录',

  // 空状态
  emptyTitle: '未找到提交记录',
  emptyDescription: '没有符合条件的提交记录。',
  noSubmissionsSelected: '未选择提交记录',

  // Toast 消息
  toast: {
    rejudgeSuccess: '重判成功',
    rejudgeError: '重判失败: {error}',
    batchRejudgeSuccess: '已成功重判 {count} 条提交',
    batchRejudgePartial: '重判完成: {success} 条成功, {failed} 条失败',
    batchRejudgeError: '批量重判失败',
    copiedToClipboard: '代码已复制到剪贴板',
    copyFailed: '复制代码失败',
  },

  // 对话框
  dialogs: {
    detailTitle: '提交详情',
    rejudgeTitle: '确认重判',
    rejudgeDescription: '确定要重新评判这条提交吗？',
    batchRejudgeTitle: '批量重判',
    batchRejudgeDescription: '确定要重新评判选中的 {count} 条提交吗？',
    confirm: '确认',
    cancel: '取消',
  },

  // 筛选器
  filters: {
    allStatuses: '全部状态',
    allLanguages: '全部语言',
    allUsers: '全部用户',
    allProblems: '全部题目',
    dateRange: '日期范围',
    from: '从',
    to: '到',
  },

  // 表格
  table: {
    selectAll: '全选',
    selected: '已选择 {count} 条',
    noData: '暂无数据',
    loading: '正在加载提交记录...',
  },
} as const
