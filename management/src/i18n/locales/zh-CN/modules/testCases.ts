export default {
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
    addFirst: '添加第一个用例',
  },

  // Form validation
  validation: {
    scopeRequired: '请选择用例类型',
    inputOutputRequired: '输入和输出不能为空',
    importTextRequired: '导入内容不能为空',
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
  },

  // View mode (read-only)
  view: {
    noCases: '该题目暂无测试用例',
    hiddenSectionTitle: '隐藏判题用例 ({count})',
    hiddenSectionHelp: '仅管理员可见,提交者无法看到这些用例',
    publicSectionTitle: '公开样例 ({count})',
  },
}
