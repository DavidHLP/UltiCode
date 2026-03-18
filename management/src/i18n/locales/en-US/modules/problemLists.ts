export default {
  title: 'Problem List Management',
  createTitle: 'Create Problem List',
  editTitle: 'Edit Problem List',
  searchPlaceholder: 'Search problem lists...',

  // Column definitions
  columns: {
    id: 'ID',
    title: 'Title',
    author: 'Author',
    status: 'Status',
    problemCount: 'Problems',
    isPublic: 'Public',
    isFeatured: 'Featured',
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
    publish: 'Publish',
    feature: 'Feature',
  },

  // Toast messages
  toast: {
    loadFailed: 'Failed to load problem lists',
    createSuccess: 'Problem list created successfully',
    createFailed: 'Failed to create problem list',
    updateSuccess: 'Problem list updated successfully',
    updateFailed: 'Failed to update problem list',
    deleteSuccess: 'Problem list deleted successfully',
    deleteFailed: 'Failed to delete problem list',
  },
} as const
