export default {
  title: "Keyboard Shortcuts",
  description:
    "Use these shortcuts to work more efficiently in the code editor.",

  categories: {
    UltiCode: "UltiCode",
    general: "General",
    navigation: "Navigation",
    editing: "Editing",
    codeActions: "Code Actions",
    selection: "Selection",
    view: "View",
    search: "Search",
  },

  // UltiCode specific
  submitCode: "Submit code",
  runCode: "Run code",
  runTest: "Run with custom test",
  toggleNotes: "Toggle notes panel",
  showShortcuts: "Show keyboard shortcuts",

  // General
  saveFile: "Save file",
  undo: "Undo",
  redo: "Redo",
  redoAlt: "Redo (alternate)",

  // Navigation
  goToLine: "Go to line",
  quickOpen: "Quick open file",
  goToSymbol: "Go to symbol",
  goBack: "Go back",
  goForward: "Go forward",

  // Editing
  selectNext: "Select next occurrence",
  deleteLine: "Delete line",
  moveLineUp: "Move line up",
  moveLineDown: "Move line down",
  copyLineUp: "Copy line up",
  copyLineDown: "Copy line down",
  toggleComment: "Toggle line comment",
  toggleBlockComment: "Toggle block comment",
  matchBracket: "Match bracket",

  // Code Actions
  triggerSuggestions: "Trigger suggestions",
  parameterHints: "Parameter hints",
  goToDefinition: "Go to definition",
  findReferences: "Find references",
  renameSymbol: "Rename symbol",
  quickFix: "Quick fix",
  formatDocument: "Format document",
  formatSelection: "Format selection",

  // Selection
  selectAll: "Select all",
  selectLine: "Select current line",
  selectAllOccurrences: "Select all occurrences",
  multiCursor: "Multi-cursor",
  addCursorAbove: "Add cursor above",
  addCursorBelow: "Add cursor below",

  // View
  zoomIn: "Zoom in",
  zoomOut: "Zoom out",
  resetZoom: "Reset zoom",
  toggleSidebar: "Toggle sidebar",
  togglePanel: "Toggle panel",

  // Search
  find: "Find",
  findReplace: "Find and replace",
  findInFiles: "Find in files",
  findNext: "Find next",
  findPrevious: "Find previous",
} as const;
