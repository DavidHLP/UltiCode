export default {
  postsTitle: 'Forum Posts',
  detailTitle: 'Post Detail',
  searchPlaceholder: 'Search posts...',

  // Column definitions
  columns: {
    id: 'ID',
    title: 'Title',
    author: 'Author',
    community: 'Community',
    status: 'Status',
    replies: 'Replies',
    views: 'Views',
    createdAt: 'Created At',
    updatedAt: 'Updated At',
  },

  // Status
  status: {
    all: 'All Status',
    ACTIVE: 'Active',
    CLOSED: 'Closed',
    HIDDEN: 'Hidden',
  },

  // Actions
  actions: {
    view: 'View',
    edit: 'Edit',
    delete: 'Delete',
    pin: 'Pin',
    close: 'Close',
    hide: 'Hide',
  },

  // Toast messages
  toast: {
    loadFailed: 'Failed to load posts',
    deleteSuccess: 'Post deleted successfully',
    deleteFailed: 'Failed to delete post',
  },
} as const
