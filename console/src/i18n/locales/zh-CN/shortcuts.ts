export default {
  title: "键盘快捷键",
  description: "使用这些快捷键可以更高效地使用代码编辑器。",

  categories: {
    UltiCode: "UltiCode",
    general: "常规",
    navigation: "导航",
    editing: "编辑",
    codeActions: "代码操作",
    selection: "选择",
    view: "视图",
    search: "搜索",
  },

  // UltiCode specific
  submitCode: "提交代码",
  runCode: "运行代码",
  runTest: "使用自定义测试运行",
  toggleNotes: "切换笔记面板",
  showShortcuts: "显示键盘快捷键",

  // General
  saveFile: "保存文件",
  undo: "撤销",
  redo: "重做",
  redoAlt: "重做 (备用)",

  // Navigation
  goToLine: "跳转到行",
  quickOpen: "快速打开文件",
  goToSymbol: "跳转到符号",
  goBack: "后退",
  goForward: "前进",

  // Editing
  selectNext: "选择下一个匹配项",
  deleteLine: "删除行",
  moveLineUp: "上移行",
  moveLineDown: "下移行",
  copyLineUp: "向上复制行",
  copyLineDown: "向下复制行",
  toggleComment: "切换行注释",
  toggleBlockComment: "切换块注释",
  matchBracket: "匹配括号",

  // Code Actions
  triggerSuggestions: "触发建议",
  parameterHints: "参数提示",
  goToDefinition: "转到定义",
  findReferences: "查找引用",
  renameSymbol: "重命名符号",
  quickFix: "快速修复",
  formatDocument: "格式化文档",
  formatSelection: "格式化选区",

  // Selection
  selectAll: "全选",
  selectLine: "选择当前行",
  selectAllOccurrences: "选择所有匹配项",
  multiCursor: "多光标",
  addCursorAbove: "在上方添加光标",
  addCursorBelow: "在下方添加光标",

  // View
  zoomIn: "放大",
  zoomOut: "缩小",
  resetZoom: "重置缩放",
  toggleSidebar: "切换侧边栏",
  togglePanel: "切换面板",

  // Search
  find: "查找",
  findReplace: "查找和替换",
  findInFiles: "在文件中查找",
  findNext: "查找下一个",
  findPrevious: "查找上一个",
} as const;
