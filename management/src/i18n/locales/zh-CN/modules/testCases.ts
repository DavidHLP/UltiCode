export default {
  // 字段标签 (HiddenCasesView / TestCaseForm)
  input: '输入',
  output: '输出',
  explanation: '解释',

  // 表单 / 弹窗 (TestCaseForm)
  editTestCase: '编辑测试用例',
  createTestCase: '创建测试用例',
  inputPlaceholder: '请输入测试输入...',
  outputPlaceholder: '请输入预期输出...',
  explanationPlaceholder: '请输入解释...',

  // 确认弹窗 (useTestCases)
  confirmDelete: '确定删除此测试用例?',

  // Hidden tab list / list-page header (HiddenTestCasesEditor)
  title: '测试用例',
  sample: '样例',
  hidden: '隐藏',
  noTestCases: '暂无测试用例',
  add: '添加',
  addFirst: '添加第一个测试用例',
  import: '导入',
  export: '导出',
  importTestCases: '导入测试用例',
  importData:
    '粘贴 JSON 数组,或使用 Input:/Output:（或 </>）块并以 --- / === 分隔',
  // 占位符与 focused import-normalization 模块支持的语法保持一致。
  // 每个块都会被规范化为 HIDDEN 案例。
  importPlaceholder:
    '< {输入}\n> {输出}\n---\n< {输入 2}\n> {输出 2}\n\n或 JSON:\n[\n  { "inputText": "...", "outputText": "...", "isSample": true, "isHidden": false }\n]',
  importHelp:
    'JSON 支持 camelCase（inputText/outputText），以及旧版 snake_case 与宽松的 {input, output} 格式。行语法始终生成 HIDDEN 案例。',
  replaceExisting: '替换已存在的测试用例',
  importing: '正在导入',

  // TestCaseList dropdown labels
  markAsSample: '设为公开样例',
  markAsHidden: '设为隐藏判题用例',

  // Tabs (EditCasesView / ViewCasesView)
  tabs: {
    samples: '公开样例',
    hidden: '隐藏判题用例',
  },

  // Per-case scope radio (TestCaseForm)
  scope: {
    sample: '公开样例',
    sampleHelp: '显示在题目描述中,提交者可见',
    hidden: '隐藏判题用例',
    hiddenHelp: '仅管理员和判题系统可见,提交者不可见',
  },

  // Hidden tab list badges / counts
  count: {
    sample: '样例',
    hidden: '隐藏',
    total: '总计',
  },

  // Section headers / empty / loading
  section: {
    title: '测试用例',
    subtitle: '管理样例与隐藏判题用例',
    addFirst: '添加第一个测试用例',
  },

  // Form validation
  validation: {
    scopeRequired: '请选择用例类型',
    inputOutputRequired: '输入和输出不能为空',
    importTextRequired: '导入内容不能为空',
    noValidTestCases: '没有找到可导入的有效测试用例',
  },

  // Confirm / dialog
  confirm: {
    delete: '确定删除此测试用例?',
  },

  // Toast
  toast: {
    loadFailed: '加载测试用例失败',
    createSuccess: '测试用例已创建',
    updateSuccess: '测试用例已更新',
    saveFailed: '保存测试用例失败',
    deleteSuccess: '测试用例已删除',
    deleteFailed: '删除测试用例失败',
    exportSuccess: '测试用例已导出',
    exportFailed: '导出测试用例失败',
    importing: '导入中...',
    imported: '已导入 {count} 个测试用例',
    importFailed: '导入测试用例失败',
    importSuccess: '已导入 {count} 个测试用例',
    updateFailed: '更新测试用例失败',
  },

  // View mode (read-only)
  view: {
    noCases: '该题目暂无测试用例',
    hiddenSectionTitle: '隐藏判题用例 ({count})',
    hiddenSectionHelp: '仅管理员可见,提交者无法看到这些用例',
    publicSectionTitle: '公开样例 ({count})',
  },
  // 只读详情卡片标题(TestCaseDetail)
  details: {
    title: '测试用例详情',
  },
}
