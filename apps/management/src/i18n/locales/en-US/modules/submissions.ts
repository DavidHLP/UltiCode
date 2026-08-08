export default {
  title: 'Submissions',
  searchPlaceholder: 'Search submissions...',
  allStatuses: 'All Statuses',
  allLanguages: 'All Languages',
  clearSelection: 'Clear Selection',

  // Column headers
  id: 'ID',
  problem: 'Problem',
  user: 'User',
  language: 'Language',
  status: 'Status',
  runtime: 'Runtime',
  memory: 'Memory',
  codeLength: 'Code Length',
  submittedAt: 'Submitted At',
  score: 'Score',
  result: 'Result',

  // Detail view
  detail: 'Submission Detail',
  detailTitle: 'Submission Details',
  code: 'Code',
  notes: 'Notes',
  testCases: 'Test Cases',
  compileOutput: 'Compile Output',
  judgeResult: 'Judge Result',
  noCode: 'No code available',
  noNotes: 'No notes',

  // Status labels
  statusLabels: {
    ACCEPTED: 'Accepted',
    WRONG_ANSWER: 'Wrong Answer',
    TIME_LIMIT_EXCEEDED: 'Time Limit Exceeded',
    MEMORY_LIMIT_EXCEEDED: 'Memory Limit Exceeded',
    RUNTIME_ERROR: 'Runtime Error',
    COMPILE_ERROR: 'Compile Error',
    PENDING: 'Pending',
    JUDGING: 'Judging',
    SYSTEM_ERROR: 'System Error',
    SANDBOX_ERROR: 'Sandbox Error',
    OUTPUT_LIMIT_EXCEEDED: 'Output Limit Exceeded',
    PRESENTATION_ERROR: 'Presentation Error',
  },

  // Stats
  stats: {
    total: 'Total',
    pending: 'Pending',
    accepted: 'Accepted',
    acceptedRate: 'AC Rate',
    topLanguage: 'Top Language',
    submissionManagement: 'submission management',
  },

  // Actions
  actions: {
    view: 'View Details',
    rejudge: 'Rejudge',
    batchRejudge: 'Batch Rejudge',
    viewCode: 'View Code',
    copyCode: 'Copy Code',
    downloadCode: 'Download Code',
  },

  // Rejudge
  batchRejudge: 'Batch Rejudge',
  rejudge: 'Rejudge',
  rejudgeTitle: 'Confirm Rejudge',
  rejudgeDescription: 'Are you sure you want to rejudge this submission?',
  batchRejudgeTitle: 'Batch Rejudge',
  batchRejudgeDescription: 'Are you sure you want to rejudge {count} selected submissions?',
  rejudgeSuccess: 'Rejudge successful',
  rejudgeError: 'Rejudge failed: {error}',

  // Errors
  loadDetailError: 'Failed to load submission details',
  loadError: 'Failed to load submissions',
  notFound: 'Submission not found',

  // Empty states
  emptyTitle: 'No submissions found',
  emptyDescription: 'There are no submissions matching your criteria.',
  noSubmissionsSelected: 'No submissions selected',

  // Toast messages
  toast: {
    rejudgeSuccess: 'Rejudge successful',
    rejudgeError: 'Rejudge failed: {error}',
    batchRejudgeSuccess: 'Successfully rejudged {count} submissions',
    batchRejudgePartial: 'Rejudge completed: {success} successful, {failed} failed',
    batchRejudgeError: 'Batch rejudge failed',
    copiedToClipboard: 'Code copied to clipboard',
    copyFailed: 'Failed to copy code',
  },

  // Dialogs
  dialogs: {
    detailTitle: 'Submission Details',
    rejudgeTitle: 'Confirm Rejudge',
    rejudgeDescription: 'Are you sure you want to rejudge this submission?',
    batchRejudgeTitle: 'Batch Rejudge',
    batchRejudgeDescription: 'Are you sure you want to rejudge {count} selected submissions?',
    confirm: 'Confirm',
    cancel: 'Cancel',
  },

  // Filters
  filters: {
    allStatuses: 'All Statuses',
    allLanguages: 'All Languages',
    allUsers: 'All Users',
    allProblems: 'All Problems',
    dateRange: 'Date Range',
    from: 'From',
    to: 'To',
  },

  // Table
  table: {
    selectAll: 'Select all',
    selected: '{count} selected',
    noData: 'No data available',
    loading: 'Loading submissions...',
  },
} as const
