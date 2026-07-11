const tableTranslations = {
  // Table toolbar
  selectAll: 'Select All',
  customizeColumns: 'Customize Columns',
  columns: 'Columns',
  rowsPerPage: 'Rows per page',
  page: 'Page',
  of: 'of',
  rowsSelected: 'rows selected',
  goToFirstPage: 'Go to first page',
  goToPreviousPage: 'Previous page',
  goToNextPage: 'Next page',
  goToLastPage: 'Go to last page',

  // Selection
  selected: '{count} selected',

  // Empty state
  emptyTitle: 'No data',
  emptyDescription: 'There is no data to display',

  // Column name mappings - C8 normalises snake_case column ids to camelCase
  // at the DataTable seam (resolveColumnName/toCamelCase), so only camelCase
  // keys live here. New column ids must match /^[a-z][a-zA-Z0-9]*$/.
  columnNames: {
    username: 'Username',
    role: 'Role',
    status: 'Status',
    actions: 'Actions',
    id: 'ID',
    name: 'Name',
    title: 'Title',
    description: 'Description',
    type: 'Type',
    created: 'Created',
    updated: 'Updated',
    email: 'Email',
    submissions: 'Submissions',
    solutions: 'Solutions',
    difficulty: 'Difficulty',
    views: 'Views',
    tags: 'Tags',
    category: 'Category',
    visibility: 'Visibility',
    author: 'Author',
    creator: 'Creator',
    duration: 'Duration',
    participants: 'Participants',

    // Moderation columns
    action: 'Action',
    performer: 'Performer',
    user: 'Target User',
    entity: 'Entity',
    priority: 'Priority',
    reporter: 'Reporter',
    reason: 'Reason',
    appellant: 'Appellant',
    response: 'Response',

    // Forum/Comments columns
    isFlagged: 'Flagged',
    content: 'Content',
    stats: 'Statistics',
    createdAt: 'Created',

    // Problem list columns
    isFeatured: 'Featured',
    isPublic: 'Public',
    problemCount: 'Problems',
    bannerOrder: 'Banner Order',
    updatedAt: 'Updated',

    // Missing camelCase keys
    lastLoginAt: 'Last Login',
    joinedAt: 'Joined',
    submissionCount: 'Submissions',
    isPublished: 'Published',
    assignedTo: 'Assigned To',
    entityType: 'Entity Type',
    primaryCategory: 'Category',
    resolution: 'Resolution',
    reportCount: 'Reports',
    authorName: 'Author Name',
    addedAt: 'Added At',
    startTime: 'Start Time',
    participantCount: 'Participants',
    contestType: 'Contest Type',
    sortOrder: 'Sort Order',
    runtime: 'Runtime',
    codeLength: 'Code Length',
    language: 'Language',
    problemTitle: 'Problem Title',
    memory: 'Memory',
    baseScorePerProblem: 'Base Score',
    timeBonusPerMinute: 'Time Bonus',
    wrongAnswerPenalty: 'WA Penalty',
    firstSolveBonus: 'First Solve Bonus',
    entityId: 'Entity ID',
    ipAddress: 'IP Address',
    queueId: 'Queue ID',
    reviewer: 'Reviewer',
  },
} as const

export type TableColumnName = keyof typeof tableTranslations.columnNames
export default tableTranslations