export default {
  title: 'Solution Management',
  detailTitle: 'Solution Detail',
  searchPlaceholder: 'Search solutions...',

  // Column definitions
  columns: {
    id: 'ID',
    title: 'Title',
    problem: 'Problem',
    author: 'Author',
    status: 'Status',
    votes: 'Votes',
    views: 'Views',
    createdAt: 'Created At',
    updatedAt: 'Updated At',
  },

  // Status
  status: {
    all: 'All Status',
    DRAFT: 'Draft',
    PUBLISHED: 'Published',
    ARCHIVED: 'Archived',
  },

  // Actions
  actions: {
    view: 'View',
    edit: 'Edit',
    delete: 'Delete',
    approve: 'Approve',
    reject: 'Reject',
  },

  // Toast messages
  toast: {
    loadFailed: 'Failed to load solutions',
    deleteSuccess: 'Solution deleted successfully',
    deleteFailed: 'Failed to delete solution',
  },
} as const
