export default {
  title: 'Comment Management',
  searchPlaceholder: 'Search comments...',
  clearSelection: 'Clear Selection',
  stats: {
    total: 'Total',
    flagged: 'Flagged',
    forum: 'Forum',
    solution: 'Solution',
    deleted: 'Deleted',
    commentModeration: 'Comment Moderation',
  },

  // Columns
  columns: {
    comment: 'Comment',
    author: 'Author',
    created: 'Created',
    status: 'Status',
    type: 'Type',
    content: 'Content',
  },

  type: {
    forum: 'Forum',
    solution: 'Solution',
    unknown: 'Unknown',
  },

  status: {
    unknown: 'Unknown',
    flagged: 'Flagged',
    deleted: 'Deleted',
    active: 'Active',
  },

  detail: {
    title: 'Comment Detail',
    parent: 'Parent Content',
    metadata: 'Metadata',
  },

  error: {
    loadingComment: 'Failed to load comment',
    commentNotFound: 'Comment not found',
    notFoundDescription: 'The comment you are looking for does not exist or has been removed.',
    back: 'Back',
    retry: 'Retry',
    backToComments: 'Back to Comments',
  },

  filters: {
    type: 'Comment Type',
    allTypes: 'All Types',
    flagStatus: 'Flag Status',
    deletedStatus: 'Delete Status',
    all: 'All',
    flagged: 'Flagged',
    clean: 'Clean',
    deleted: 'Deleted',
    active: 'Active',
  },

  bulkActions: {
    bulkUnflag: 'Bulk Unflag',
    bulkDelete: 'Bulk Delete',
  },

  // Actions
  actions: {
    view: 'View',
    delete: 'Delete',
    flag: 'Flag',
    unflag: 'Unflag',
    viewDetails: 'View Details',
    noPermission: 'No Permission',
  },

  deleteConfirm: 'Are you sure you want to delete {count} comments?',

  delete: {
    title: 'Delete Comment',
    description: 'Are you sure you want to delete this comment? This action cannot be undone.',
    confirm: 'Confirm Delete',
    cancel: 'Cancel',
  },

  flag: {
    title: 'Flag Comment',
    description: 'Please provide a reason for flagging this comment.',
    confirm: 'Confirm Flag',
    cancel: 'Cancel',
    reasonLabel: 'Flag Reason',
    reasonPlaceholder: 'Enter flag reason...',
  },

  toast: {
    deletedSuccessfully: 'Comment deleted successfully',
    failedToDelete: 'Failed to delete comment',
    flaggedSuccessfully: 'Comment flagged successfully',
    failedToFlag: 'Failed to flag comment',
    unflaggedSuccessfully: 'Comment unflagged successfully',
    failedToUnflag: 'Failed to unflag comment',
    bulkUnflaggedSuccessfully: 'Comments unflagged successfully',
    failedToBulkUnflag: 'Failed to bulk unflag comments',
    bulkDeletedSuccessfully: 'Comments deleted successfully',
    failedToBulkDelete: 'Failed to bulk delete comments',
    reasonRequired: 'Please provide a reason',
  },
} as const
