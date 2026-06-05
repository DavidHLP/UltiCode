export default {
  title: 'Solution Management',
  detailTitle: 'Solution Detail',
  searchPlaceholder: 'Search solutions...',

  // ========== Column Definitions ==========
  columns: {
    id: 'ID',
    title: 'Title',
    solution: 'Solution',
    problem: 'Problem',
    author: 'Author',
    status: 'Status',
    votes: 'Votes',
    views: 'Views',
    created: 'Created',
    createdAt: 'Created At',
    updatedAt: 'Updated At',
    actions: 'Actions',
  },

  // ========== Status ==========
  status: {
    all: 'All Status',
    DRAFT: 'Draft',
    PUBLISHED: 'Published',
    ARCHIVED: 'Archived',
    deleted: 'Deleted',
    flagged: 'Flagged',
    published: 'Published',
    unpublished: 'Unpublished',
  },

  // ========== Actions ==========
  actions: {
    view: 'View',
    viewDetails: 'View Details',
    edit: 'Edit',
    delete: 'Delete',
    approve: 'Approve',
    reject: 'Reject',
    flag: 'Flag',
    unflag: 'Unflag',
  },

  // ========== Filters ==========
  filters: {
    flagStatus: 'Flag Status',
    all: 'All',
    flagged: 'Flagged',
    clean: 'Clean',
    visibility: 'Visibility',
    published: 'Published',
    unpublished: 'Unpublished',
  },

  // ========== Tabs ==========
  tabs: {
    description: 'Description',
    code: 'Code',
  },

  // ========== Detail ==========
  detail: {
    noCodeContent: 'No code content',
    solutionFor: 'Solution for {problem}',
    noDescriptionContent: 'No description content',
    author: 'Author',
    problemDifficulty: 'Problem Difficulty',
    views: 'Views',
    language: 'Language',
    created: 'Created',
    updated: 'Updated',
    lines: 'lines',
    copy: 'Copy',
    copied: 'Copied',
    sourceCode: 'Source Code',
    deletedAt: 'Deleted At',
    deletedBy: 'Deleted By',
  },

  // ========== Error States ==========
  error: {
    loadingSolution: 'Failed to load solution',
    back: 'Back',
    retry: 'Retry',
    solutionNotFound: 'Solution Not Found',
    notFoundDescription: 'The specified solution could not be found',
    backToSolutions: 'Back to Solutions',
  },

  // ========== Delete Dialog ==========
  delete: {
    title: 'Delete Solution',
    description: 'Are you sure you want to delete this solution? This action cannot be undone.',
    confirm: 'Confirm Delete',
    cancel: 'Cancel',
  },

  // ========== Approval Status ==========
  approval: {
    approved: 'Approved',
    rejected: 'Rejected',
    pending: 'Pending',
  },

  // ========== Flag Dialog ==========
  flag: {
    title: 'Flag Solution',
    description: 'Please provide a reason for flagging.',
    confirm: 'Confirm Flag',
    cancel: 'Cancel',
    reasonLabel: 'Flag Reason',
    reasonPlaceholder: 'Enter reason for flagging...',
  },

  // ========== Toast Messages ==========
  toast: {
    loadFailed: 'Failed to load solutions',
    deleteSuccess: 'Solution deleted successfully',
    deleteFailed: 'Failed to delete solution',
    deletedSuccessfully: 'Solution deleted successfully',
    failedToDelete: 'Failed to delete solution',
    flaggedSuccessfully: 'Solution flagged successfully',
    failedToFlag: 'Failed to flag solution',
    unflaggedSuccessfully: 'Solution unflagged successfully',
    failedToUnflag: 'Failed to unflag solution',
    reasonRequired: 'Please provide a reason for flagging',
  },

  // ========== Terminal Stats ==========
  terminal: {
    total: 'Total',
    flagged: 'Flagged',
    published: 'Published',
    loading: 'Loading...',
    solutionManagement: 'Solution Management',
  },

  // ========== Empty State ==========
  empty: {
    title: 'No Solutions',
    description: 'Click the button above to create your first solution',
  },
} as const
