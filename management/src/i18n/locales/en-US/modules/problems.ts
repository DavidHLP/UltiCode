export default {
  title: 'Problem Management',
  createTitle: 'Create Problem',
  detailTitle: 'Problem Detail',
  editTitle: 'Edit Problem',
  createProblem: 'Create Problem',
  importProblem: 'Import Problem',
  exportProblem: 'Export Problem',
  searchPlaceholder: 'Search problems...',

  // Column definitions
  columns: {
    id: 'ID',
    title: 'Title',
    difficulty: 'Difficulty',
    status: 'Status',
    tags: 'Tags',
    acceptance: 'Acceptance',
    submissions: 'Submissions',
    createdAt: 'Created At',
    updatedAt: 'Updated At',
    isFlagged: 'Flagged',
  },

  // Difficulty
  difficulty: {
    all: 'All Difficulties',
    EASY: 'Easy',
    MEDIUM: 'Medium',
    HARD: 'Hard',
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
    duplicate: 'Duplicate',
    publish: 'Publish',
    archive: 'Archive',
    restore: 'Restore',
    viewSubmissions: 'View Submissions',
    viewSolutions: 'View Solutions',
    flag: 'Flag',
    unflag: 'Unflag',
    viewFlagInfo: 'View Flag Info',
  },

  // Form fields
  form: {
    title: 'Title',
    titlePlaceholder: 'Enter problem title',
    slug: 'Slug',
    slugPlaceholder: 'two-sum',
    description: 'Description',
    descriptionPlaceholder: 'Enter problem description...',
    difficulty: 'Difficulty',
    tags: 'Tags',
    tagsPlaceholder: 'Select tags...',
    timeLimit: 'Time Limit (ms)',
    memoryLimit: 'Memory Limit (MB)',
    score: 'Score',
    isPremium: 'Premium Problem',
    isPublished: 'Published',
    hints: 'Hints',
    solutionTemplate: 'Solution Template',
    starterCode: 'Starter Code',
    testCases: 'Test Cases',
    examples: 'Examples',
    constraints: 'Constraints',
  },

  // Edit tabs
  tabs: {
    description: 'Description',
    code: 'Code',
    cases: 'Test Cases',
    settings: 'Settings',
    versions: 'Version History',
    audit: 'Audit Log',
  },

  // Toast messages
  toast: {
    createSuccess: 'Problem created successfully',
    createFailed: 'Failed to create problem',
    updateSuccess: 'Problem updated successfully',
    updateFailed: 'Failed to update problem',
    deleteSuccess: 'Problem deleted successfully',
    deleteFailed: 'Failed to delete problem',
    publishSuccess: 'Problem published successfully',
    publishFailed: 'Failed to publish problem',
    archiveSuccess: 'Problem archived successfully',
    archiveFailed: 'Failed to archive problem',
    restoreSuccess: 'Problem restored successfully',
    restoreFailed: 'Failed to restore problem',
    importSuccess: 'Problem imported successfully',
    importFailed: 'Failed to import problem',
    exportSuccess: 'Problem exported successfully',
    exportFailed: 'Failed to export problem',
    flagSuccess: 'Problem flagged successfully',
    flagFailed: 'Failed to flag problem',
    unflagSuccess: 'Problem unflagged successfully',
    unflagFailed: 'Failed to unflag problem',
    loadFailed: 'Failed to load problems',
    versionLoadFailed: 'Failed to load version history',
  },

  // Dialogs
  dialogs: {
    deleteTitle: 'Confirm Delete',
    deleteDescription:
      'Are you sure you want to delete problem "{title}"? This action cannot be undone.',
    publishTitle: 'Confirm Publish',
    publishDescription: 'Are you sure you want to publish problem "{title}"?',
    archiveTitle: 'Confirm Archive',
    archiveDescription: 'Are you sure you want to archive problem "{title}"?',
    flagTitle: 'Flag Problem',
    flagDescription: 'Flag "{title}" for moderation review. Please provide a reason.',
  },

  // Flag info
  flagInfo: {
    title: 'Flag Information',
    flaggedBy: 'Flagged By',
    flaggedAt: 'Flagged At',
    reason: 'Reason',
    status: 'Review Status',
    notes: 'Moderation Notes',
    noFlagInfo: 'This problem has not been flagged',
  },

  // Version history
  versionHistory: {
    title: 'Version History',
    noHistory: 'No version history available',
    version: 'Version',
    author: 'Author',
    changes: 'Changes',
    restore: 'Restore to this version',
    viewDiff: 'View Diff',
  },

  // Empty state
  empty: {
    title: 'No Problems',
    description: 'Click the button above to create your first problem',
  },

  clearSelection: 'Clear Selection',
  bulkDeleteConfirm: 'Are you sure you want to delete {count} selected problems?',
} as const
