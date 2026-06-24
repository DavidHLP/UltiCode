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
    easy: 'Easy',
    medium: 'Medium',
    hard: 'Hard',
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

  // Stats
  stats: {
    problemManagement: 'problem management',
    total: 'total',
    published: 'published',
    draft: 'draft',
    flagged: 'flagged',
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
    description: 'Import problems from JSON or CSV file',
    dropFile: 'Drop file here, or click to browse',
    supportedFormats: 'Supported formats: JSON, CSV',
    browse: 'Browse',
    clear: 'Clear',
    invalidFile: 'Invalid file format. Please upload JSON or CSV.',
    conflictStrategy: 'Conflict Resolution Strategy',
    strategies: {
      skip: 'Skip',
      update: 'Update Existing',
      createNew: 'Create New',
    },
    strategyDescriptions: {
      skip: 'Skip existing problems without making changes',
      update: 'Update information of existing problems',
      create_new: 'Create conflicting problems as new entries',
    },
    importing: 'Importing...',
    import: 'Import',
    created: 'Created',
    updated: 'Updated',
    skipped: 'Skipped',
    success: 'Problems imported successfully',
    failed: 'Failed to import problems',
    partialSuccess: 'Successfully imported {success} / {total} problems',
    someErrors: 'Some problems failed to import. See error details below.',
    error: 'Import failed. Please try again.',
    errors: 'Error Details',
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
    publishTitle: 'Confirm Bulk Publish',
    publishDescription: 'Are you sure you want to publish {count} selected problems?',
    confirmPublish: 'Confirm Publish',
    unpublishTitle: 'Confirm Bulk Unpublish',
    unpublishDescription: 'Are you sure you want to unpublish {count} selected problems?',
    confirmUnpublish: 'Confirm Unpublish',
    deleteTitle: 'Confirm Bulk Delete',
    deleteDescription:
      'Are you sure you want to delete {count} selected problems? This action cannot be undone.',
    confirmDelete: 'Confirm Delete',
    restoreTitle: 'Confirm Bulk Restore',
    restoreDescription: 'Are you sure you want to restore {count} selected problems?',
    confirmRestore: 'Confirm Restore',
    warning: 'Warning',
    warningDescription: 'This action will affect all selected problems',
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

  // Preview
  preview: {
    untitled: 'Untitled',
  },

  // Description form
  descriptionForm: {
    basicInfo: 'Basic Information',
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
    examples: 'Examples',
    examplesSection: {
      title: 'Examples',
      add: 'Add Example',
      empty: 'No examples yet',
      input: 'Input',
      output: 'Output',
      explanation: 'Explanation (optional)',
    },
    constraints: 'Constraints',
    constraintsSection: {
      title: 'Constraints',
      add: 'Add Constraint',
      empty: 'No constraints yet',
      emptyDescription:
        'No constraints added yet. Constraints describe the limits and rules for the problem (e.g., array length, value ranges).',
      addNew: 'Add new constraint',
      placeholder: 'e.g., 1 <= nums.length <= 10^5',
    },
    hints: 'Hints',
    hintsSection: {
      title: 'Hints',
      add: 'Add Hint',
      empty: 'No hints yet',
    },
    tags: 'Tags',
    tagsSection: {
      title: 'Tags',
    },
    languages: 'Languages',
    languagesDescription: 'Select the programming languages this problem supports.',
    noLanguagesSelected: 'No languages selected',
    preview: {
      title: 'Live Preview',
    },
    section: {
      basicInfo: 'Basic Information',
      problemDescription: 'Problem Description',
      examples: 'Examples',
      constraints: 'Constraints',
      hints: 'Hints',
      tags: 'Tags',
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
    action: 'Action',
    mode: 'Mode',
    newProblem: 'New Problem',
    problemCreation: 'Problem Creation',
    section: 'Section',
    description: 'Description',
    testCases: 'Test Cases',
    code: 'Code',
    problemEditor: 'Problem Editor',
    testCaseEditor: 'Test Case Editor',
    languageConfig: 'Language Config',
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
  dialog: {
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
    noReason: 'No reason provided',
    reportedBy: 'Reported By',
    reportedAt: 'Reported At',
    reviewedBy: 'Reviewed By',
    reviewedAt: 'Reviewed At',
  },

  // Version history
  versionHistory: {
    title: 'Version History',
    noHistory: 'No version history yet',
    version: 'Version',
    author: 'Author',
    changes: 'Changes',
    restore: 'Restore',
    restoreSuccess: 'Restored to this version',
    viewDiff: 'View Diff',
    description: 'View and manage problem version history',
    compareWith: 'Compare with version {version}',
    noVersions: 'No versions available',
    createInitial: 'Create Initial Version',
    by: 'by',
    versionDetails: 'Version Details',
    compareVersions: 'Compare Versions',
    noChanges: 'No changes',
    oldValue: 'Old Value',
    newValue: 'New Value',
    rollbackTitle: 'Rollback to Version {version}',
    rollbackConfirm:
      'Are you sure you want to rollback to version {version}? This will create a new version record.',
    rollbackReasonPlaceholder: 'Enter rollback reason (optional)',
    rollbackButton: 'Confirm Rollback',
    loadError: 'Failed to load version history',
    loadDetailError: 'Failed to load version details',
    compareError: 'Version comparison failed',
    rollbackError: 'Rollback failed',
    rollbackSuccess: 'Successfully rolled back to version {version}',
    createInitialSuccess: 'Initial version created successfully',
    alreadyHasVersions: 'This problem already has version records',
    createInitialError: 'Failed to create initial version',
    action: {
      CREATE: 'Created',
      UPDATE: 'Updated',
      ROLLBACK: 'Rolled Back',
    },
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

  // Tags Selector
  tagsSelector: {
    selected: 'Selected',
    selectedCount: '{count} selected',
    removeTag: 'Remove tag {tag}',
    noTagsSelected: 'No tags selected',
    searchPlaceholder: 'Search tags...',
    loading: 'Loading...',
    available: 'Available',
    totalCount: '{count} total',
    noResults: 'No matching tags',
    noTagsAvailable: 'No tags available',
  },

  clearSelection: 'Clear Selection',
  bulkDeleteConfirm: 'Are you sure you want to delete {count} selected problems?',

  // Markdown Editor
  markdownEditor: {
    bold: 'Bold',
    italic: 'Italic',
    inlineCode: 'Inline Code',
    codeBlock: 'Code Block',
    insertLink: 'Insert Link',
    insertImage: 'Insert Image',
    toggleFullscreen: 'Toggle Fullscreen',
    placeholder: 'Enter Markdown content...',
  },

  // Test Cases Editor
  testCasesEditor: {
    example: 'Example {number}',
    addExample: 'Add Example',
    input: 'Input',
    inputPlaceholder: 'Enter test input...',
    output: 'Output',
    outputPlaceholder: 'Enter expected output...',
    explanationOptional: 'Explanation (optional)',
    explanationPlaceholder: 'Enter explanation...',
    noCases: 'No test cases',
  },

  // Bulk edit dialog (BulkEditDialog)
  bulkEdit: {
    title: 'Bulk Edit Problems',
    description: 'Apply changes to {count} selected problem(s).',
    difficulty: 'Difficulty',
    difficultyPlaceholder: 'Select difficulty',
    premium: 'Premium',
    premiumHint: 'Mark selected problems as premium content',
    editing: 'Saving...',
    edit: 'Apply Changes',
    noChanges: 'Please select at least one field to edit',
    success: 'Successfully updated {count} problem(s)',
    failure: 'Failed to update the selected problem(s)',
    partial: 'Updated {success} problem(s), {failed} failed',
    error: 'An error occurred while updating the problems',
  },
} as const
