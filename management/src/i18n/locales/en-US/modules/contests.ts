export default {
  title: 'Contest Management',
  detailTitle: 'Contest Detail',
  scoringRules: 'Scoring Rules',
  createContest: 'Create Contest',
  searchPlaceholder: 'Search contests...',

  // Column definitions
  columns: {
    id: 'ID',
    title: 'Title',
    type: 'Type',
    status: 'Status',
    startTime: 'Start Time',
    endTime: 'End Time',
    participants: 'Participants',
    problems: 'Problems',
  },

  // Contest types
  type: {
    all: 'All Types',
    RATED: 'Rated',
    UNRATED: 'Unrated',
    EDUCATIONAL: 'Educational',
    MONTHLY: 'Monthly',
    WEEKLY: 'Weekly',
  },

  // Contest status
  status: {
    all: 'All Status',
    UPCOMING: 'Upcoming',
    ONGOING: 'Ongoing',
    FINISHED: 'Finished',
    CANCELLED: 'Cancelled',
  },

  // Actions
  actions: {
    view: 'View',
    edit: 'Edit',
    delete: 'Delete',
    duplicate: 'Duplicate',
    cancel: 'Cancel Contest',
    viewRankings: 'View Rankings',
    viewSubmissions: 'View Submissions',
    manageProblems: 'Manage Problems',
    publish: 'Publish Announcement',
  },

  // Form fields
  form: {
    title: 'Title',
    titlePlaceholder: 'Enter contest title',
    description: 'Description',
    descriptionPlaceholder: 'Enter contest description...',
    type: 'Type',
    startTime: 'Start Time',
    endTime: 'End Time',
    duration: 'Duration',
    isPublic: 'Public Contest',
    isRated: 'Rated Contest',
    maxParticipants: 'Max Participants',
    password: 'Contest Password',
    passwordPlaceholder: 'Leave empty for no password',
    rules: 'Rules',
    scoringRules: 'Scoring Rules',
  },

  // Wizard
  wizard: {
    steps: {
      basic: 'Basic Info',
      problems: 'Select Problems',
      schedule: 'Schedule',
      review: 'Review & Submit',
    },
    back: 'Previous',
    next: 'Next',
    submit: 'Create Contest',
    update: 'Update Contest',
  },

  // Toast messages
  toast: {
    createSuccess: 'Contest created successfully',
    createFailed: 'Failed to create contest',
    updateSuccess: 'Contest updated successfully',
    updateFailed: 'Failed to update contest',
    deleteSuccess: 'Contest deleted successfully',
    deleteFailed: 'Failed to delete contest',
    cancelSuccess: 'Contest cancelled successfully',
    cancelFailed: 'Failed to cancel contest',
    publishSuccess: 'Announcement published successfully',
    publishFailed: 'Failed to publish announcement',
  },

  // Dialogs
  dialogs: {
    deleteTitle: 'Confirm Delete',
    deleteDescription:
      'Are you sure you want to delete contest "{title}"? This action cannot be undone.',
    cancelTitle: 'Confirm Cancel',
    cancelDescription:
      'Are you sure you want to cancel contest "{title}"? Registered users will be notified.',
  },

  // Details
  details: {
    overview: 'Overview',
    problems: 'Problems',
    rankings: 'Rankings',
    submissions: 'Submissions',
    announcements: 'Announcements',
    participants: 'Participants',
    statistics: 'Statistics',
  },

  // Scoring rules
  scoring: {
    title: 'Scoring Rules',
    addRule: 'Add Rule',
    editRule: 'Edit Rule',
    deleteRule: 'Delete Rule',
    type: 'Rule Type',
    value: 'Value',
    description: 'Description',
    types: {
      FIRST_BLOOD: 'First Blood Bonus',
      TIME_BONUS: 'Time Bonus',
      DIFFICULTY_BONUS: 'Difficulty Bonus',
      PENALTY: 'Penalty',
    },
  },
} as const
