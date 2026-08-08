export default {
  title: 'Contest Management',
  detailTitle: 'Contest Detail',
  scoringRules: 'Scoring Rules',
  createContest: 'Create Contest',
  searchPlaceholder: 'Search contests...',
  clearSelection: 'Clear Selection',

  // Column definitions
  columns: {
    id: 'ID',
    contest: 'Contest',
    title: 'Title',
    type: 'Type',
    status: 'Status',
    schedule: 'Schedule',
    startTime: 'Start Time',
    endTime: 'End Time',
    participants: 'Participants',
    problems: 'Problems',
    actions: 'Actions',
  },

  // Contest types
  type: {
    all: 'All Types',
    biweekly: 'Biweekly',
    weekly: 'Weekly',
    RATED: 'Rated',
    UNRATED: 'Unrated',
    EDUCATIONAL: 'Educational',
    MONTHLY: 'Monthly',
    WEEKLY: 'Weekly',
    IOI: 'IOI',
    ICPC: 'ICPC',
    CUSTOM: 'Custom',
    PUBLIC: 'Public',
    PRIVATE: 'Private',
    VIRTUAL: 'Virtual',
  },

  // Contest status
  status: {
    all: 'All Status',
    DRAFT: 'Draft',
    UPCOMING: 'Upcoming',
    ONGOING: 'Ongoing',
    RUNNING: 'Running',
    FINISHED: 'Finished',
    CANCELLED: 'Cancelled',
    draft: 'Draft',
    published: 'Published',
    registering: 'Registering',
    upcoming: 'Upcoming',
    ongoing: 'Ongoing',
    running: 'Running',
    freezing: 'Freezing',
    finished: 'Finished',
    cancelled: 'Cancelled',
    archived: 'Archived',
  },

  // Filters
  filters: {
    allStatus: 'All Status',
    allTypes: 'All Types',
    status: {
      draft: 'Draft',
      upcoming: 'Upcoming',
      running: 'Running',
      finished: 'Finished',
      cancelled: 'Cancelled',
    },
    type: {
      ICPC: 'ICPC',
      IOI: 'IOI',
      CUSTOM: 'Custom',
      public: 'Public',
      private: 'Private',
      virtual: 'Virtual',
    },
  },

  // Stats
  stats: {
    contestManagement: 'contest management',
    total: 'total',
    running: 'running',
    upcoming: 'upcoming',
    finished: 'finished',
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
    bulkDelete: 'Bulk Delete',
    viewDetails: 'View Details',
    startContest: 'Start Contest',
    endContest: 'End Contest',
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

  // Basics step
  basics: {
    title: 'Title',
    titlePlaceholder: 'Enter contest title',
    titleDescription: 'A descriptive title for the contest',
    slug: 'Slug',
    slugPlaceholder: 'contest-slug',
    slugDescription: 'URL-friendly identifier (lowercase, hyphens only)',
    type: 'Type',
    typePlaceholder: 'Select contest type',
    typeDescription: 'Public contests are visible to all users',
    description: 'Description',
    descriptionPlaceholder: 'Enter contest description...',
    types: {
      ICPC: 'ICPC',
      IOI: 'IOI',
      CUSTOM: 'Custom',
    },
  },

  // Schedule step
  scheduleStep: {
    startTime: 'Start Time',
    startTimeDescription: 'When the contest will begin',
    duration: 'Duration (minutes)',
    durationDescription: 'How long the contest will last',
    publishImmediately: 'Publish Immediately',
    publishImmediatelyDescription: 'Make the contest visible to users right away',
    minutes: '{minutes} minutes',
    notSet: 'Not set',
  },

  // Problems step
  problemsStep: {
    addProblem: 'Add Problem',
    title: 'Title',
    difficulty: 'Difficulty',
    score: 'Score',
    noProblemsSelected: 'No problems selected. Click "Add Problem" to add problems.',
  },

  // Review step
  reviewStep: {
    basicInfo: 'Basic Info',
    schedule: 'Schedule',
    startTime: 'Start Time',
    duration: 'Duration',
    visibility: 'Visibility',
    defaultScoringRule: 'Using default scoring rule',
    problemsCount: '{count} Problems Selected',
    noProblemsSelected: 'No problems selected',
  },

  // Scoring rule
  scoringRule: {
    selectRule: 'Scoring Rule',
    selectPlaceholder: 'Select a scoring rule',
    createNew: 'Create New Rule',
    selectDescription: 'Choose a scoring rule for this contest or create a new one',
  },

  // Wizard
  wizard: {
    createContest: 'Create Contest',
    description: 'Follow the steps to create a new contest',
    steps: {
      basic: 'Basic Info',
      problems: 'Select Problems',
      schedule: 'Schedule',
      review: 'Review & Submit',
    },
    basics: 'Basics',
    scoring: 'Scoring',
    schedule: 'Schedule',
    problems: 'Problems',
    review: 'Review',
    previous: 'Previous',
    back: 'Previous',
    next: 'Next',
    submit: 'Create Contest',
    update: 'Update Contest',
  },

  // Toast messages
  toast: {
    createSuccess: 'Contest created successfully',
    createFailed: 'Failed to create contest',
    createdSuccessfully: 'Contest created successfully',
    failedToCreate: 'Failed to create contest',
    updateSuccess: 'Contest updated successfully',
    updateFailed: 'Failed to update contest',
    deleteSuccess: 'Contest deleted successfully',
    deleteFailed: 'Failed to delete contest',
    deletedSuccessfully: 'Contest deleted successfully',
    failedToDelete: 'Failed to delete contest',
    cancelSuccess: 'Contest cancelled successfully',
    cancelFailed: 'Failed to cancel contest',
    publishSuccess: 'Announcement published successfully',
    publishFailed: 'Failed to publish announcement',
    startedSuccessfully: 'Contest started successfully',
    failedToStart: 'Failed to start contest',
    endedSuccessfully: 'Contest ended successfully',
    failedToEnd: 'Failed to end contest',
    bulkDeleteSuccess: '{count} contests deleted successfully',
    bulkDeleteFailed: 'Failed to delete contests',
    problemAdded: 'Problem added successfully',
    failedToAddProblem: 'Failed to add problem',
    problemRemoved: 'Problem removed successfully',
    failedToRemoveProblem: 'Failed to remove problem',
    invalidStartTime: 'Invalid start time format',
  },

  // Confirmation messages
  confirmation: {
    bulkDelete: 'Are you sure you want to delete {count} contests?',
    startNow: 'Are you sure you want to start this contest now?',
    endNow: 'Are you sure you want to end this contest now?',
    deleteThis: 'Are you sure you want to delete this contest?',
    removeProblem: 'Are you sure you want to remove this problem from the contest?',
  },

  // Delete dialog
  delete: {
    title: 'Delete Contest',
    description: 'Are you sure you want to delete contest "{title}"? This action cannot be undone.',
    thisContest: 'this contest',
    confirm: 'Delete',
    cancel: 'Cancel',
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

  // Detail view
  detail: {
    overview: 'Overview',
    problems: 'Problems',
    participants: 'Participants',
    rankings: 'Rankings',
    start: 'Start',
    end: 'End',
    description: 'Description',
    noDescription: 'No description provided',
    slug: 'Slug',
    visibility: 'Visibility',
    startTime: 'Start Time',
    duration: 'Duration',
    addProblem: 'Add Problem',
    problem: 'Problem',
    difficulty: 'Difficulty',
    score: 'Score',
    noProblemsAdded: 'No problems added yet',
    user: 'User',
    joinedAt: 'Joined At',
    noParticipantsYet: 'No participants yet',
    rank: 'Rank',
    penalty: 'Penalty',
    noRankingsYet: 'No rankings available yet',
    contestNotFound: 'Contest not found',
    backToList: 'Back to List',
    hidden: 'Hidden',
    statusPublished: 'PUBLISHED',
    statusHidden: 'HIDDEN',
  },

  // Drawer
  drawer: {
    title: 'Contest Details',
    subtitle: 'View contest information',
    loadingDetails: 'Loading contest details...',
    contestNotFound: 'Contest not found',
    fullView: 'Full View',
    published: 'Published',
    problems: 'Problems',
    participants: 'Participants',
    start: 'Start',
    duration: 'Duration',
    pts: 'pts',
  },

  // Problem picker
  problemPicker: {
    title: 'Select Problem',
    description: 'Search and select a problem to add to the contest',
    searchPlaceholder: 'Search problems...',
    noProblemsFound: 'No problems found',
    problems: 'Problems',
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
