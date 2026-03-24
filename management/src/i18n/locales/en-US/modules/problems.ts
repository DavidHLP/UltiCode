export default {
  title: 'Problem Management',
  createTitle: 'Create Problem',
  detailTitle: 'Problem Detail',
  editTitle: 'Edit Problem',
  createProblem: 'Create Problem',
  importProblem: 'Import Problem',
  exportProblem: 'Export Problem',
  searchPlaceholder: 'Search problems...',
  addProblem: 'Add Problem',

  // Column definitions
  columns: {
    id: 'ID',
    title: 'Title',
    problem: 'Problem',
    difficulty: 'Difficulty',
    status: 'Status',
    tags: 'Tags',
    acceptance: 'Acceptance',
    submissions: 'Submissions',
    createdAt: 'Created At',
    updatedAt: 'Updated At',
    isFlagged: 'Flagged',
    published: 'Published',
    flagged: 'Flagged',
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
    TODO: 'Todo',
    ATTEMPTED: 'Attempted',
    SOLVED: 'Solved',
    todo: 'Todo',
    attempted: 'Attempted',
    solved: 'Solved',
  },

  // Published status
  published: {
    published: 'Published',
    draft: 'Draft',
    deleted: 'Deleted',
  },

  // Badges
  badges: {
    premium: 'Premium',
  },

  // Actions
  actions: {
    view: 'View',
    edit: 'Edit',
    delete: 'Delete',
    duplicate: 'Duplicate',
    publish: 'Publish',
    unpublish: 'Unpublish',
    archive: 'Archive',
    restore: 'Restore',
    viewSubmissions: 'View Submissions',
    viewSolutions: 'View Solutions',
    flag: 'Flag',
    unflag: 'Unflag',
    viewFlagInfo: 'View Flag Info',
    export: 'Export',
  },

  // Filters
  filters: {
    allDifficulty: 'All Difficulties',
    allStatus: 'All Status',
    allPublished: 'All Published',
    published: 'Published',
    unpublished: 'Unpublished',
  },

  // Sort
  sort: {
    title: 'Sort By',
    default: 'Default',
    titleAsc: 'Title (A-Z)',
    difficultyAsc: 'Difficulty (Low to High)',
    createdDesc: 'Created (Newest)',
    updatedDesc: 'Updated (Newest)',
    submissionsDesc: 'Submissions (High to Low)',
  },

  // Export
  export: {
    title: 'Export',
    json: 'Export as JSON',
    csv: 'Export as CSV',
    success: 'Problems exported successfully',
    failed: 'Failed to export problems',
  },

  // Import
  import: {
    title: 'Import',
    success: 'Problems imported successfully',
    failed: 'Failed to import problems',
  },

  // Bulk operations
  bulk: {
    noSelection: 'Please select problems to perform action',
    success: 'Successfully {action} {count} problems',
    failed: 'Failed to {action} {count} problems',
    partial: '{success} succeeded, {failed} failed',
    publish: 'publish',
    unpublish: 'unpublish',
    delete: 'delete',
    restore: 'restore',
    action: 'Bulk action',
  },

  // Form fields
  form: {
    title: 'Title',
    titlePlaceholder: 'Enter problem title',
    slug: 'Slug',
    slugPlaceholder: 'two-sum',
    description: 'Description',
    descriptionPlaceholder: 'Enter problem description...',
    summary: 'Summary',
    summaryPlaceholder: 'Enter problem summary...',
    contentPlaceholder: 'Enter problem content...',
    fullContent: 'Full Content',
    difficulty: 'Difficulty',
    tags: 'Tags',
    tagsPlaceholder: 'Select tags...',
    addTagPlaceholder: 'Add tag...',
    timeLimit: 'Time Limit (ms)',
    memoryLimit: 'Memory Limit (MB)',
    score: 'Score',
    isPremium: 'Premium Problem',
    isPublished: 'Published',
    hints: 'Hints',
    addHintPlaceholder: 'Add a hint...',
    addHint: 'Add Hint',
    noHints: 'No hints added.',
    solutionTemplate: 'Solution Template',
    starterCode: 'Starter Code',
    testCases: 'Test Cases',
    examples: 'Examples',
    constraints: 'Constraints',
    publishing: 'Publishing',
    status: 'Status',
    premium: 'Premium',
    premiumDescription: 'Only for premium users',
    published: 'Published',
    publishedDescription: 'Visible to all users',
    saving: 'Saving...',
    updateProblem: 'Update Problem',
    createProblem: 'Create Problem',
    taxonomy: 'Taxonomy',
    languages: 'Languages',
    all: 'All',
    addLanguagePlaceholder: 'Add language...',
    add: 'Add',
    details: {
      title: 'Basic Information',
      description: 'Fill in the basic problem information',
    },
    testCasesSection: {
      title: 'Test Cases',
      description: 'Add example test cases to help users understand the problem',
    },
    additionalInfo: {
      title: 'Additional Information',
    },
    constraintsSection: {
      title: 'Constraints',
      placeholder: 'e.g., 1 <= nums.length <= 10^4',
    },
    validation: {
      slugRequired: 'Slug is required',
      slugInvalid: 'Slug can only contain lowercase letters, numbers and hyphens',
      titleRequired: 'Title is required',
      examplesRequired: 'At least one test case is required',
      inputRequired: 'Input is required',
      outputRequired: 'Output is required',
    },
  },

  // Description form
  descriptionForm: {
    problemDescription: 'Problem Description',
    problemDescriptionSubtitle: 'Fill in the basic information and description',
    titlePlaceholder: 'Enter problem title',
    slugPlaceholder: 'e.g., two-sum',
    summaryPlaceholder: 'Enter a brief summary...',
    contentPlaceholder: 'Enter the full problem description...',
    publishing: 'Publishing',
    premium: 'Premium',
    premiumDescription: 'Only accessible to premium users',
    published: 'Published',
    publishedDescription: 'Visible to all users',
    saving: 'Saving...',
    updateDescription: 'Update Description',
    saveDescription: 'Save Description',
    validation: {
      slugRequired: 'Slug is required',
      slugInvalid: 'Slug can only contain lowercase letters, numbers and hyphens',
      titleRequired: 'Title is required',
    },
  },

  // Code form
  codeForm: {
    addLanguages: 'Add Languages',
    quickAdd: 'Quick Add',
    customLanguagePlaceholder: 'Enter custom language...',
    add: 'Add',
    lines: 'lines',
    starterCodeTemplate: 'Starter Code Template',
    noLanguages: 'No languages added yet',
    noLanguagesDescription:
      'Click the language buttons above to quickly add, or enter a custom language',
    configuration: 'Configuration',
    languages: 'Languages',
    allLanguages: 'All languages supported',
    selectedLanguages: 'Specific languages selected',
    saving: 'Saving...',
    saveChanges: 'Save Changes',
  },

  // Cases form
  casesForm: {
    testCasesSection: 'Test Cases',
    constraintsAndHints: 'Constraints & Hints',
    constraints: 'Constraints',
    constraintPlaceholder: 'e.g., 1 <= n <= 10^5',
    add: 'Add',
    noConstraints: 'No constraints added',
    hints: 'Hints',
    addHint: 'Add Hint',
    noHints: 'No hints added',
    tags: 'Tags',
    addTag: 'Add Tag',
    noTags: 'No tags added',
    configurationSummary: 'Configuration Summary',
    summary: {
      testCases: 'Test Cases',
      constraints: 'Constraints',
      hints: 'Hints',
      tags: 'Tags',
    },
    saving: 'Saving...',
    saveChanges: 'Save Changes',
    validation: {
      examplesRequired: 'At least one test case is required',
      inputRequired: 'Input is required',
      outputRequired: 'Output is required',
    },
  },

  // Edit tabs
  tabs: {
    description: 'Description',
    code: 'Code',
    cases: 'Test Cases',
    testCases: 'Test Cases',
    settings: 'Settings',
    versions: 'Version History',
    audit: 'Audit Log',
  },

  // Create page
  create: {
    title: 'Create Problem',
  },

  // Edit page
  edit: {
    loading: 'Loading...',
    descriptionSubtitle: 'Edit problem description',
    codeSubtitle: 'Configure supported languages',
    testCasesSubtitle: 'Manage test cases and constraints',
  },

  // View page
  view: {
    loading: 'Loading...',
    notFound: 'Problem Not Found',
    notFoundDescription: 'The specified problem could not be found',
    backToProblems: 'Back to Problems',
  },

  // Display components
  display: {
    id: 'ID',
    created: 'Created',
    updated: 'Updated',
    published: 'Published',
    metadata: 'Metadata',
    tags: 'Tags',
    hints: 'Hints',
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
    unpublishSuccess: 'Problem unpublished successfully',
    unpublishFailed: 'Failed to unpublish problem',
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
    delete: {
      title: 'Confirm Delete',
      description:
        'Are you sure you want to delete problem "{title}"? This action cannot be undone.',
      confirm: 'Confirm Delete',
      thisProblem: 'this problem',
    },
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
    restoreSuccess: 'Version restored successfully',
    viewDiff: 'View Diff',
  },

  // Empty state
  empty: {
    title: 'No Problems',
    description: 'Click the button above to create your first problem',
  },

  // Code Display component
  codeDisplay: {
    noCode: 'No Code Templates',
    noCodeDescription:
      'No language templates have been configured for this problem yet. Edit the problem to add starter code for different programming languages.',
    noCodeForLanguage: 'No starter code configured for {language}',
    lines: 'lines',
    copy: 'Copy',
    copied: 'Copied',
    languagesConfigured: '{count} language(s) configured',
    selectLanguage: 'Select a language to view code',
  },

  // Description Display component
  descriptionDisplay: {
    example: 'Example',
    input: 'Input',
    expectedOutput: 'Expected Output',
    explanation: 'Explanation',
    constraints: 'Constraints',
    hints: 'Hints',
    codeCopied: 'Code copied',
  },

  // Cases Display component
  casesDisplay: {
    examples: 'Test Cases',
    input: 'Input',
    output: 'Output',
    explanation: 'Explanation',
    noCases: 'No Test Cases',
    noCasesDescription:
      'No test cases have been configured for this problem yet. Edit the problem to add example test cases.',
  },

  clearSelection: 'Clear Selection',
  bulkDeleteConfirm: 'Are you sure you want to delete {count} selected problems?',
} as const
