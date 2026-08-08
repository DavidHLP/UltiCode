export default {
  title: 'Scoring Rules',
  createRule: 'Create Rule',
  searchPlaceholder: 'Search rules...',
  showInactive: 'Show Inactive',
  stats: {
    total: 'Total',
    active: 'Active',
    defaults: 'Default',
    inactive: 'Inactive',
    scoringManagement: 'Scoring Management',
  },
  loadError: 'Failed to load scoring rules',
  emptyTitle: 'No scoring rules found',
  emptyDescription: 'Create a new scoring rule to get started.',

  // Columns
  columns: {
    name: 'Name',
    baseScore: 'Base Score',
    timeBonus: 'Time Bonus',
    wrongPenalty: 'Wrong Penalty',
    firstSolveBonus: 'First Solve Bonus',
    actions: 'Actions',
  },

  // Badges
  badges: {
    default: 'Default',
    inactive: 'Inactive',
  },

  // Actions
  actions: {
    setDefault: 'Set as Default',
    edit: 'Edit',
    delete: 'Delete',
    noActionsAvailable: 'No actions available',
  },

  // Form
  form: {
    name: 'Name',
    namePlaceholder: 'Enter rule name',
    nameRequired: 'Name is required',
    nameTooLong: 'Name must be less than 100 characters',
    description: 'Description',
    descriptionPlaceholder: 'Optional description...',
    descriptionTooLong: 'Description must be less than 500 characters',
    baseScorePerProblem: 'Base Score per Problem',
    timeBonusPerMinute: 'Time Bonus per Minute',
    wrongAnswerPenalty: 'Wrong Answer Penalty',
    timeLimitPenalty: 'Time Limit Penalty',
    firstSolveBonus: 'First Solve Bonus',
    fullScoreBonus: 'Full Score Bonus',
    isDefault: 'Set as Default',
    isDefaultDescription: 'This rule will be used for new contests by default',
    mustBeNonNegative: 'Value must be non-negative',
    createTitle: 'Create Scoring Rule',
    createDescription: 'Define a new scoring rule for contests.',
    editTitle: 'Edit Scoring Rule',
    editDescription: 'Modify the scoring rule parameters.',
    createRule: 'Create Rule',
    saveChanges: 'Save Changes',
  },

  // Delete dialog
  delete: {
    title: 'Delete Scoring Rule',
    description:
      'Are you sure you want to delete scoring rule "{name}"? This action cannot be undone.',
    thisRule: 'this scoring rule',
    confirm: 'Delete',
  },

  // Toast messages
  toast: {
    createdSuccessfully: 'Scoring rule created successfully',
    failedToCreate: 'Failed to create scoring rule',
    updatedSuccessfully: 'Scoring rule updated successfully',
    failedToUpdate: 'Failed to update scoring rule',
    deletedSuccessfully: 'Scoring rule deleted successfully',
    failedToDelete: 'Failed to delete scoring rule',
    setDefaultSuccess: 'Default scoring rule updated successfully',
    failedToSetDefault: 'Failed to set default scoring rule',
  },
} as const
