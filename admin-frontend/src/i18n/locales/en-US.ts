export default {
  // Common actions and labels
  common: {
    // Actions
    save: 'Save',
    cancel: 'Cancel',
    delete: 'Delete',
    edit: 'Edit',
    add: 'Add',
    create: 'Create',
    update: 'Update',
    view: 'View',
    remove: 'Remove',
    confirm: 'Confirm',
    submit: 'Submit',
    back: 'Back',
    next: 'Next',
    previous: 'Previous',
    close: 'Close',
    open: 'Open',
    copy: 'Copy',
    download: 'Download',
    upload: 'Upload',

    // States
    loading: 'Loading...',
    noData: 'No data',
    never: 'Never',
    yes: 'Yes',
    no: 'No',
    all: 'All',
    none: 'None',
    any: 'Any',

    // Labels
    actions: 'Actions',
    status: 'Status',
    details: 'Details',
    search: 'Search',
    filter: 'Filter',
    export: 'Export',
    refresh: 'Refresh',
    retry: 'Retry',
    select: 'Select',
    clear: 'Clear',
    name: 'Name',
    title: 'Title',
    description: 'Description',
    type: 'Type',
    created: 'Created',
    updated: 'Updated',
    id: 'ID',

    // Time
    today: 'Today',
    yesterday: 'Yesterday',
    thisWeek: 'This week',
    lastWeek: 'Last week',
  },

  // Navigation
  nav: {
    dashboard: 'Dashboard',
    users: 'Users',
    problems: 'Problems',
    contests: 'Contests',
    forum: 'Forum',
    settings: 'Settings',
    problemLists: 'Problem Lists',
    tags: 'Tags',
    solutions: 'Solutions',
    comments: 'Comments',
    notifications: 'Notifications',
    auditLogs: 'Audit Logs',
    getHelp: 'Get Help',
    search: 'Search',
  },

  // Dashboard
  dashboard: {
    title: 'Dashboard',
    welcome: 'Welcome back',
    loading: 'Loading dashboard...',

    // Statistics
    stats: {
      totalUsers: 'Total Users',
      activeToday: 'active today',
      activeThisWeek: 'active this week',
      totalProblems: 'Total Problems',
      published: 'published',
      unpublished: 'unpublished',
      activeContests: 'Active Contests',
      upcoming: 'upcoming',
      finished: 'finished',
      flaggedContent: 'Flagged Content',
      actionNeeded: 'Action needed',
      allClear: 'All clear',
      pendingModeration: 'Pending moderation',
    },

    // Chart
    chart: {
      userRegistrationTrend: 'User Registration Trend',
      dailyRegistrations: 'Daily user registrations for the past 30 days',
    },

    // Recent Activity
    recentActivity: {
      title: 'Recent Activity',
      description: 'Latest admin actions across the platform',
      noActivity: 'No recent activity',
      target: 'Target',
    },

    // Time ago
    timeAgo: {
      justNow: 'Just now',
      minuteAgo: '{count} minute ago',
      minuteAgo_plural: '{count} minutes ago',
      hourAgo: '{count} hour ago',
      hourAgo_plural: '{count} hours ago',
      dayAgo: '{count} day ago',
      dayAgo_plural: '{count} days ago',
    },
  },

  // Users
  users: {
    title: 'Users',
    listTitle: 'User Management',
    addUser: 'Add User',
    searchPlaceholder: 'Search users...',
    selected: '{count} users selected',
    selected_one: '{count} user selected',
    clearSelection: 'Clear Selection',

    // Filters
    filters: {
      allRoles: 'All Roles',
      allStatus: 'All Status',
      role: {
        USER: 'User',
        MODERATOR: 'Moderator',
        ADMIN: 'Admin',
        SUPER_ADMIN: 'Super Admin',
      },
      status: {
        active: 'Active',
        inactive: 'Inactive',
        banned: 'Banned',
      },
    },

    // Table columns
    columns: {
      user: 'User',
      role: 'Role',
      joined: 'Joined',
      lastLogin: 'Last Login',
    },

    // Bulk actions
    bulkActions: {
      bulkBan: 'Bulk Ban',
      bulkUnban: 'Bulk Unban',
      bulkDelete: 'Bulk Delete',
    },

    // Dialogs
    deleteConfirm: 'Are you sure you want to delete {count} users? This action is IRREVERSIBLE.',
    deleteConfirm_one: 'Are you sure you want to delete {count} user? This action is IRREVERSIBLE.',
    banReasonPrompt: 'Enter reason for bulk ban:',

    // Status badges
    status: {
      banned: 'Banned',
      active: 'Active',
      inactive: 'Inactive',
    },

    // Toast messages
    toast: {
      unbanFailed: 'Failed to unban user',
      bulkBanFailed: 'Failed to bulk ban users',
      bulkUnbanFailed: 'Failed to bulk unban users',
      bulkDeleteFailed: 'Failed to bulk delete users',
    },

    // Form labels
    form: {
      username: 'Username',
      email: 'Email',
      displayName: 'Display Name',
      role: 'Role',
      password: 'Password',
      confirmPassword: 'Confirm Password',
      isActive: 'Active',
      isBanned: 'Banned',
      banReason: 'Ban Reason',
      banExpiresAt: 'Ban Expires At',
      avatar: 'Avatar URL',
    },

    // Actions
    actions: {
      viewDetails: 'View Details',
      editProfile: 'Edit Profile',
      resetPassword: 'Reset Password',
      banUser: 'Ban User',
      unbanUser: 'Unban User',
      deleteUser: 'Delete User',
    },
  },

  // Problems
  problems: {
    title: 'Problems',
    listTitle: 'Problem Management',
    addProblem: 'Add Problem',
    searchPlaceholder: 'Search problems...',

    // Filters
    filters: {
      difficulty: 'Difficulty',
      allLevels: 'All Levels',
      status: 'Status',
      allStatus: 'All Status',
      visibility: 'Visibility',
      published: 'Published',
      unpublished: 'Draft',
    },

    // Difficulty levels
    difficulty: {
      EASY: 'Easy',
      MEDIUM: 'Medium',
      HARD: 'Hard',
    },

    // Status
    status: {
      todo: 'Todo',
      attempted: 'Attempted',
      solved: 'Solved',
    },

    // Published state
    published: {
      published: 'Published',
      draft: 'Draft',
      deleted: 'Deleted',
    },

    // Table columns
    columns: {
      id: 'ID',
      problem: 'Problem',
      difficulty: 'Difficulty',
      status: 'Status',
      published: 'Published',
      submissions: 'Submissions',
      tags: 'Tags',
      created: 'Created',
    },

    // Tabs
    tabs: {
      description: 'Description',
      code: 'Code',
      testCases: 'Test Cases',
      overview: 'Overview',
    },

    // Actions
    actions: {
      view: 'View',
      edit: 'Edit',
      publish: 'Publish',
      unpublish: 'Unpublish',
      delete: 'Delete',
      viewDescription: 'Description',
      viewCode: 'Code',
      viewCases: 'Test Cases',
    },

    // Toast messages
    toast: {
      publishSuccess: 'Problem published successfully',
      publishFailed: 'Failed to publish problem',
      unpublishSuccess: 'Problem unpublished successfully',
      unpublishFailed: 'Failed to unpublish problem',
    },

    // Code template
    code: {
      template: 'Code Template',
      language: 'Language',
      addTemplate: 'Add Template',
    },

    // Test cases
    cases: {
      testCases: 'Test Cases',
      addCase: 'Add Case',
      input: 'Input',
      output: 'Output',
      explanation: 'Explanation',
      sample: 'Sample',
      isSample: 'Is Sample Case',
      isHidden: 'Hidden',
    },

    // Edit views
    edit: {
      description: 'Description',
      code: 'Code',
      testCases: 'Test Cases',
    },

    // View views
    view: {
      errorLoading: 'Error loading problem',
      notFound: 'Problem not found',
      backToProblems: 'Back to Problems',
    },

    // Display components
    display: {
      metadata: 'Metadata',
      id: 'ID',
      created: 'Created',
      updated: 'Updated',
      published: 'Published',
      tags: 'Tags',
      hints: 'Hints',
    },

    // Code form
    codeForm: {
      addLanguages: 'Add Languages',
      quickAdd: 'Quick Add',
      customLanguagePlaceholder: 'Enter custom language...',
      add: 'Add',
      languagesConfigured: '{count} languages configured',
      allLanguages: 'All languages (no filter)',
      selectedLanguages: 'Only problems in selected languages',
      configuration: 'Configuration',
      languages: 'Languages',
      saving: 'Saving...',
      saveChanges: 'Save Changes',
      noLanguages: 'No Languages Added',
      noLanguagesDescription: 'Add programming languages to configure starter code templates',
      starterCodeTemplate: 'Starter Code Template',
      lines: 'lines',
      selectLanguage: 'Select a language to view its starter code',
    },

    // Code display
    codeDisplay: {
      noCode: 'No Code Configured',
      noCodeDescription: 'No starter code has been configured for this problem',
      copy: 'Copy',
      copied: 'Copied!',
      lines: 'lines',
      languagesConfigured: '{count} languages configured',
      selectLanguage: 'Select a language to view its starter code',
      noCodeForLanguage: 'No starter code configured for {language}',
    },

    // Cases form
    casesForm: {
      testCasesSection: 'Test Cases',
      constraintsAndHints: 'Constraints & Hints',
      constraints: 'Constraints',
      hints: 'Hints',
      tags: 'Tags',
      configurationSummary: 'Configuration Summary',
      noConstraints: 'No constraints added',
      noHints: 'No hints added',
      noTags: 'No tags added',
      constraintPlaceholder: 'e.g. 1 <= nums.length <= 10^4',
      add: 'Add',
      addHint: 'Add a hint...',
      addTag: 'Add tag...',
      saving: 'Saving...',
      saveChanges: 'Save Changes',
      summary: {
        testCases: 'Test Cases',
        constraints: 'Constraints',
        hints: 'Hints',
        tags: 'Tags',
      },
      validation: {
        examplesRequired: 'At least one example is required',
        inputRequired: 'Input is required',
        outputRequired: 'Output is required',
      },
    },

    // Cases display
    casesDisplay: {
      noCases: 'No Test Cases Added',
      noCasesDescription:
        'Add test cases with sample inputs and outputs to help users understand the problem',
      examples: 'Examples',
      input: 'Input',
      output: 'Output',
      explanation: 'Explanation',
    },

    // Description form
    descriptionForm: {
      problemDescription: 'Problem Description',
      problemDescriptionSubtitle: 'Basic information and content of the problem',
      titlePlaceholder: 'e.g. Two Sum',
      slugPlaceholder: 'e.g. two-sum',
      summaryPlaceholder: 'Brief summary displayed in lists...',
      contentPlaceholder: 'Write the full problem description in markdown...',
      publishing: 'Publishing',
      premium: 'Premium',
      premiumDescription: 'Only for premium users',
      published: 'Published',
      publishedDescription: 'Visible to all users',
      saving: 'Saving...',
      updateDescription: 'Update Description',
      saveDescription: 'Save Description',
      validation: {
        slugRequired: 'Slug is required',
        slugInvalid: 'Slug must contain only lowercase letters, numbers, and hyphens',
        titleRequired: 'Title is required',
      },
    },

    // Test cases editor
    testCasesEditor: {
      addExample: 'Add Example',
      example: 'Example {number}',
      input: 'Input',
      output: 'Output',
      explanationOptional: 'Explanation (optional)',
      inputPlaceholder: 'Enter the test case input...',
      outputPlaceholder: 'Enter the expected output...',
      explanationPlaceholder: 'Explanation for this example...',
      noCases: 'No test cases. Click "Add Example" to create one.',
    },

    // Markdown editor
    markdownEditor: {
      placeholder: 'Write markdown here...',
      bold: 'Bold (Ctrl+B)',
      italic: 'Italic (Ctrl+I)',
      inlineCode: 'Inline Code',
      codeBlock: 'Code Block',
      insertLink: 'Insert Link',
      insertImage: 'Insert Image',
      toggleFullscreen: 'Toggle Fullscreen (Esc)',
    },

    // Problem form (main create/edit form)
    form: {
      // Card titles
      details: {
        title: 'Problem Details',
        description: 'Basic information about the problem',
      },
      testCases: {
        title: 'Test Cases',
        description: 'Define sample inputs and outputs',
      },
      additionalInfo: {
        title: 'Additional Information',
        description: 'Add constraints, hints, and other metadata',
      },
      // Labels
      title: 'Title',
      titlePlaceholder: 'Enter problem title',
      slug: 'Slug',
      slugPlaceholder: 'problem-slug',
      summary: 'Summary',
      summaryPlaceholder: 'Brief description of the problem',
      fullContent: 'Full Content',
      contentPlaceholder: 'Detailed problem description in markdown',
      difficulty: 'Difficulty',
      status: 'Status',
      constraints: {
        title: 'Constraints',
        placeholder: 'Add a constraint...',
      },
      hints: {
        title: 'Hints',
        placeholder: 'Add a hint...',
      },
      languages: 'Languages',
      all: 'All',
      tags: 'Tags',
      isPremium: 'Premium',
      isPremiumDescription: 'Only available for premium users',
      isPublished: 'Published',
      isPublishedDescription: 'Visible to all users',
      taxonomy: 'Taxonomy',
      // Actions
      add: 'Add',
      createProblem: 'Create Problem',
      updateProblem: 'Update Problem',
      saving: 'Saving...',
      // Validation
      validation: {
        slugRequired: 'Slug is required',
        slugInvalid: 'Slug must contain only lowercase letters, numbers, and hyphens',
        titleRequired: 'Title is required',
        examplesRequired: 'At least one test case is required',
        inputRequired: 'Input is required',
        outputRequired: 'Output is required',
      },
    },

    // Dialog
    dialog: {
      delete: {
        title: 'Delete Problem',
        description: 'Are you sure you want to delete "{title}"? This action is IRREVERSIBLE.',
        thisProblem: 'this problem',
        confirm: 'Delete Problem',
      },
    },
  },

  // Contests
  contests: {
    title: 'Contests',
    listTitle: 'Contest Management',
    addContest: 'Create Contest',
    createContest: 'Create Contest',
    searchPlaceholder: 'Search contests...',
    selected: '{count} contests selected',
    clearSelection: 'Clear Selection',

    // Filters
    filters: {
      allStatus: 'All Status',
      allTypes: 'All Types',
      upcoming: 'Upcoming',
      running: 'Running',
      finished: 'Finished',
      public: 'Public',
      private: 'Private',
      virtual: 'Virtual',
    },

    // Type
    type: {
      PUBLIC: 'Public',
      PRIVATE: 'Private',
      VIRTUAL: 'Virtual',
    },

    // Table columns
    columns: {
      contest: 'Contest',
      type: 'Type',
      status: 'Status',
      schedule: 'Schedule',
      participants: 'Participants',
      actions: 'Actions',
    },

    // Actions
    actions: {
      viewDetails: 'View Details',
      startContest: 'Start Contest',
      endContest: 'End Contest',
      bulkDelete: 'Bulk Delete',
      delete: 'Delete',
    },

    // Status badges
    status: {
      upcoming: 'Upcoming',
      running: 'Running',
      finished: 'Finished',
    },

    // Wizard
    wizard: {
      basics: 'Basics',
      schedule: 'Schedule',
      problems: 'Problems',
      review: 'Review',
      previous: 'Previous',
      next: 'Next',
      submit: 'Create Contest',
      createContest: 'Create Contest',
    },

    // Basic Info step
    basics: {
      title: 'Title',
      titlePlaceholder: 'Weekly Contest 101',
      titleDescription: 'The display name of the contest.',
      slug: 'Slug',
      slugPlaceholder: 'weekly-contest-101',
      slugDescription: 'Unique URL identifier for the contest.',
      type: 'Type',
      typePlaceholder: 'Select type',
      typeDescription: 'Public contests are visible to everyone. Private requires invitation.',
      description: 'Description',
      descriptionPlaceholder: 'Contest details and rules...',
    },

    // Schedule step
    scheduleStep: {
      startTime: 'Start Time',
      startTimeDescription: 'When the contest begins.',
      duration: 'Duration (Minutes)',
      durationDescription: 'Length of the contest in minutes.',
      publishImmediately: 'Publish Immediately',
      publishImmediatelyDescription:
        'If enabled, the contest will be visible in the upcoming list immediately.',
      notSet: 'Not set',
      minutes: '{minutes} minutes',
    },

    // Problems step
    problemsStep: {
      contestProblems: 'Contest Problems',
      addProblem: 'Add Problem',
      index: 'Index',
      title: 'Title',
      difficulty: 'Difficulty',
      score: 'Score',
      noProblemsSelected: 'No problems selected. Add problems to the contest.',
    },

    // Review step
    reviewStep: {
      basicInfo: 'Basic Info',
      schedule: 'Schedule',
      startTime: 'Start Time',
      duration: 'Duration',
      visibility: 'Visibility',
      problemsCount: 'Problems ({count})',
      noProblemsSelected: 'No problems selected.',
      published: 'Published',
      draft: 'Draft',
    },

    // Problem picker
    problemPicker: {
      title: 'Select Problem',
      description: 'Search and select a problem to add to the contest.',
      searchPlaceholder: 'Search problems by title or slug...',
      problems: 'Problems',
      noProblemsFound: 'No problems found.',
      noProblems: 'No problems',
    },

    // Detail view
    detail: {
      overview: 'Overview',
      problems: 'Problems',
      participants: 'Participants',
      rankings: 'Rankings',
      details: 'Details',
      statsAndSchedule: 'Stats & Schedule',
      description: 'Description',
      noDescription: 'No description provided.',
      slug: 'Slug',
      visibility: 'Visibility',
      published: 'Published',
      hidden: 'Hidden',
      startTime: 'Start Time',
      duration: 'Duration',
      contestProblems: 'Contest Problems',
      addProblem: 'Add Problem',
      idx: 'Idx',
      problem: 'Problem',
      difficulty: 'Difficulty',
      score: 'Score',
      noProblemsAdded: 'No problems added yet.',
      user: 'User',
      joinedAt: 'Joined At',
      noParticipantsYet: 'No participants yet.',
      rank: 'Rank',
      penalty: 'Penalty',
      noRankingsYet: 'No rankings available yet.',
      contestNotFound: 'Contest not found.',
      backToList: 'Back to list',
      start: 'Start',
      end: 'End',
    },

    // Detail drawer
    drawer: {
      title: 'Contest Details',
      subtitle: 'View contest information and statistics.',
      fullView: 'Full View',
      loadingDetails: 'Loading contest details...',
      contestNotFound: 'Contest not found',
      statistics: 'Statistics',
      schedule: 'Schedule',
      start: 'Start Time',
      duration: 'Duration (minutes)',
      description: 'Description',
      problemsCount: 'Problems ({count})',
      moreProblems: '+ {count} more problems',
      pts: 'pts',
    },

    // Delete dialog
    delete: {
      title: 'Delete Contest',
      description:
        'Are you sure you want to delete <strong>{title}</strong>? This action cannot be undone.',
      thisContest: 'this contest',
      confirm: 'Delete Contest',
      deleting: 'Deleting...',
      cancel: 'Cancel',
    },

    // Toast messages
    toast: {
      startedSuccessfully: 'Contest started successfully',
      failedToStart: 'Failed to start contest',
      endedSuccessfully: 'Contest ended successfully',
      failedToEnd: 'Failed to end contest',
      deletedSuccessfully: 'Contest deleted successfully',
      failedToDelete: 'Failed to delete contest',
      createdSuccessfully: 'Contest created successfully',
      failedToCreate: 'Failed to create contest',
      problemAdded: 'Problem added to contest',
      failedToAddProblem: 'Failed to add problem',
      problemRemoved: 'Problem removed',
      failedToRemoveProblem: 'Failed to remove problem',
      bulkDeleteSuccess: '{count} contests deleted',
      bulkDeleteFailed: 'Failed to delete some contests',
    },

    // Confirmations
    confirmation: {
      startNow: 'Are you sure you want to start this contest now?',
      endNow: 'Are you sure you want to end this contest?',
      deleteThis: 'Are you sure you want to delete this contest? This action cannot be undone.',
      bulkDelete: 'Are you sure you want to delete {count} contests? This action is IRREVERSIBLE.',
      removeProblem: 'Remove this problem from the contest?',
    },
  },

  // Notifications
  notifications: {
    title: 'Notifications',
    listTitle: 'Notification Management',
    addNotification: 'Add Notification',
    searchPlaceholder: 'Search notifications...',

    // Table columns
    columns: {
      title: 'Title',
      type: 'Type',
      priority: 'Priority',
      recipients: 'Recipients',
      sendAt: 'Send At',
      status: 'Status',
    },

    // Actions
    actions: {
      view: 'View',
      edit: 'Edit',
      delete: 'Delete',
      send: 'Send Now',
    },

    // Form
    form: {
      title: 'Title',
      content: 'Content',
      type: 'Type',
      priority: 'Priority',
      sendAt: 'Send At',
      sendToAll: 'Send to All Users',
      targetUsers: 'Target Users',
    },

    // Types
    type: {
      info: 'Info',
      warning: 'Warning',
      success: 'Success',
      error: 'Error',
    },

    // Priority
    priority: {
      low: 'Low',
      normal: 'Normal',
      high: 'High',
      urgent: 'Urgent',
    },

    // Status
    status: {
      draft: 'Draft',
      scheduled: 'Scheduled',
      sent: 'Sent',
    },
  },

  // Audit Logs
  audit: {
    title: 'Audit Logs',
    listTitle: 'Audit Log History',
    searchPlaceholder: 'Search logs...',

    // Table columns
    columns: {
      action: 'Action',
      performer: 'Performer',
      target: 'Target',
      entityType: 'Entity Type',
      ip: 'IP Address',
      createdAt: 'Time',
      details: 'Details',
    },

    // Filters
    filters: {
      allActions: 'All Actions',
      allUsers: 'All Users',
      dateRange: 'Date Range',
    },

    // Actions
    actions: {
      viewDetails: 'View Details',
      export: 'Export Logs',
    },
  },

  // Settings
  settings: {
    title: 'Settings',

    // General settings
    generalSettings: {
      category: 'General',
      siteName: 'Site Name',
      siteDescription: 'Site Description',
      siteUrl: 'Site URL',
      logo: 'Logo URL',
      favicon: 'Favicon URL',
      language: 'Default Language',
      timezone: 'Timezone',
    },

    // Security settings
    securitySettings: {
      category: 'Security',
      passwordMinLength: 'Minimum Password Length',
      passwordRequireUppercase: 'Require Uppercase',
      passwordRequireLowercase: 'Require Lowercase',
      passwordRequireNumbers: 'Require Numbers',
      passwordRequireSpecialChars: 'Require Special Characters',
      sessionTimeout: 'Session Timeout (minutes)',
      maxLoginAttempts: 'Max Login Attempts',
      lockoutDuration: 'Lockout Duration (minutes)',
    },

    // Email settings
    emailSettings: {
      category: 'Email',
      smtpHost: 'SMTP Host',
      smtpPort: 'SMTP Port',
      smtpSecure: 'Use SSL/TLS',
      smtpUser: 'SMTP Username',
      smtpFrom: 'From Email',
      smtpFromName: 'From Name',
      testEmail: 'Send Test Email',
    },
  },

  // Problem Lists
  problemLists: {
    title: 'Problem Lists',
    addList: 'Add List',
    searchPlaceholder: 'Search lists...',

    // Table columns
    columns: {
      name: 'Name',
      owner: 'Owner',
      problems: 'Problems',
      isPublic: 'Public',
      createdAt: 'Created',
    },

    // Actions
    actions: {
      view: 'View',
      edit: 'Edit',
      delete: 'Delete',
    },

    // Form
    form: {
      name: 'List Name',
      description: 'Description',
      isPublic: 'Public',
    },

    // Problems manager
    problemsManager: {
      addProblems: 'Add Problems',
      removeProblems: 'Remove Problems',
      selectedProblems: 'Selected Problems',
      availableProblems: 'Available Problems',
      reorder: 'Reorder',
    },
  },

  // Tags
  tags: {
    title: 'Tags',
    addTag: 'Add Tag',
    searchPlaceholder: 'Search tags...',
    mergeTags: 'Merge Tags',

    // Table columns
    columns: {
      name: 'Name',
      label: 'Label',
      color: 'Color',
      problems: 'Problems',
      createdAt: 'Created',
    },

    // Actions
    actions: {
      edit: 'Edit',
      delete: 'Delete',
      merge: 'Merge With...',
    },

    // Form
    form: {
      name: 'Tag Name',
      label: 'Display Label',
      color: 'Color',
      description: 'Description',
    },

    // Merge dialog
    merge: {
      sourceTag: 'Source Tag (will be deleted)',
      targetTag: 'Target Tag (will be kept)',
      confirm: 'Merge tags? All problems tagged with "{source}" will be retagged as "{target}".',
    },
  },

  // Solutions
  solutions: {
    title: 'Solutions',
    listTitle: 'Solution Management',
    searchPlaceholder: 'Search solutions...',

    // Filters
    filters: {
      flagStatus: 'Flag Status',
      visibility: 'Visibility',
      all: 'All',
      flagged: 'Flagged',
      clean: 'Clean',
      published: 'Published',
      unpublished: 'Unpublished',
    },

    // Table columns
    columns: {
      id: 'ID',
      solution: 'Solution',
      author: 'Author',
      status: 'Status',
      views: 'Views',
      created: 'Created',
      actions: 'Actions',
    },

    // Status
    status: {
      deleted: 'Deleted',
      flagged: 'Flagged',
      published: 'Published',
      unpublished: 'Unpublished',
    },

    // Actions
    actions: {
      viewDetails: 'View Details',
      unflag: 'Unflag',
      flag: 'Flag',
      delete: 'Delete',
    },

    // Tabs
    tabs: {
      description: 'Description',
      code: 'Code',
    },

    // Detail view
    detail: {
      solutionFor: 'Solution for {problem}',
      noDescriptionContent: 'No description content provided.',
      summary: 'Summary',
      metadata: 'Metadata',
      author: 'Author',
      problemDifficulty: 'Problem Difficulty',
      views: 'Views',
      language: 'Language',
      created: 'Created',
      updated: 'Updated',
      tags: 'Tags',
      flaggedReason: 'Flagged Reason',
      at: 'at',
      sourceCode: 'Source Code',
      lines: 'lines',
      copied: 'Copied',
      copy: 'Copy',
      noCodeContent: 'No code content available.',
    },

    // Delete dialog
    delete: {
      title: 'Delete Solution',
      description:
        'Are you sure you want to delete the solution <span class="font-medium text-foreground">"{title}"</span>? This action cannot be undone.',
      cancel: 'Cancel',
      confirm: 'Delete Solution',
      deleting: 'Deleting...',
    },

    // Flag dialog
    flag: {
      title: 'Flag Solution',
      description:
        'Flagging solution <span class="font-medium text-foreground">"{title}"</span> will mark it for review and may hide it from public view depending on settings.',
      reasonLabel: 'Reason for flagging',
      reasonPlaceholder: 'Please explain why this solution violates community guidelines...',
      cancel: 'Cancel',
      confirm: 'Flag Solution',
      flagging: 'Flagging...',
    },

    // Toast messages
    toast: {
      unflaggedSuccessfully: 'Solution unflagged successfully',
      failedToUnflag: 'Failed to unflag solution',
      deletedSuccessfully: 'Solution deleted successfully',
      failedToDelete: 'Failed to delete solution',
      flaggedSuccessfully: 'Solution flagged successfully',
      failedToFlag: 'Failed to flag solution',
      reasonRequired: 'Please provide a reason for flagging',
    },

    // Error states
    error: {
      loadingSolution: 'Error Loading Solution',
      solutionNotFound: 'Solution Not Found',
      notFoundDescription: "The solution doesn't exist or you don't have permission to view it.",
      backToSolutions: 'Back to Solutions',
      back: 'Back',
      retry: 'Retry',
    },
  },

  // Forum
  forum: {
    title: 'Forum',
    postsTitle: 'Forum Posts',
    searchPlaceholder: 'Search posts...',

    // Filters
    filters: {
      community: 'Community',
      allCommunities: 'All Communities',
      flagStatus: 'Flag Status',
      pinned: 'Pinned',
      locked: 'Locked',
      all: 'All',
      flagged: 'Flagged',
      clean: 'Clean',
      unpinned: 'Unpinned',
      unlocked: 'Unlocked',
    },

    // Table columns
    columns: {
      title: 'Title',
      stats: 'Stats',
      status: 'Status',
      created: 'Created',
      actions: 'Actions',
    },

    // Status
    status: {
      deleted: 'Deleted',
      flagged: 'Flagged',
      active: 'Active',
      pinned: 'Pinned',
      locked: 'Locked',
    },

    // Actions
    actions: {
      viewDetails: 'View Details',
      pin: 'Pin',
      unpin: 'Unpin',
      lock: 'Lock',
      unlock: 'Unlock',
      delete: 'Delete',
    },

    // Tabs
    tabs: {
      overview: 'Overview',
      comments: 'Comments',
      audit: 'Audit',
    },

    // Detail view
    detail: {
      inCommunity: 'in {community}',
      content: 'Content',
      noContentAvailable: 'No content available',
      views: 'Views',
      comments: 'Comments',
      upvotes: 'Upvotes',
      downvotes: 'Downvotes',
      timeline: 'Timeline',
      created: 'Created',
      updated: 'Updated',
      flagInformation: 'Flag Information',
      reason: 'Reason:',
      flaggedOn: 'Flagged on:',
      deletionInformation: 'Deletion Information',
      deletedOn: 'Deleted on:',
      identifiers: 'Identifiers',
      postId: 'Post ID:',
      authorId: 'Author ID:',
      communityId: 'Community ID:',
    },

    // Drawer
    drawer: {
      title: 'Post Details',
      description: 'View forum post information and content.',
      authorCommunity: 'Author & Community',
      unknownCommunity: 'Unknown Community',
      statistics: 'Statistics',
      contentPreview: 'Content Preview',
      postNotFound: 'Post not found',
    },

    // Overview display
    overview: {
      author: 'Author',
      unknown: 'Unknown',
    },

    // Comments tab
    comments: {
      postComments: 'Post Comments',
      noCommentsFound: 'No comments found for this post',
    },

    // Audit tab
    audit: {
      noAuditHistory: 'No audit history available',
      performed: 'performed',
      ip: 'IP:',
      from: 'From:',
      to: 'To:',
    },

    // Action labels for audit
    auditActions: {
      PIN_FORUM_POST: 'Pinned',
      UNPIN_FORUM_POST: 'Unpinned',
      LOCK_FORUM_POST: 'Locked',
      UNLOCK_FORUM_POST: 'Unlocked',
      DELETE_FORUM_POST: 'Deleted',
      FLAG_FORUM_POST: 'Flagged',
      UNFLAG_FORUM_POST: 'Unflagged',
      BULK_DELETE_FORUM: 'Bulk Delete',
      BULK_PIN_FORUM: 'Bulk Pin',
    },

    // Delete dialog
    delete: {
      title: 'Delete Post',
      description: 'Are you sure you want to delete this post? This action cannot be undone.',
      cancel: 'Cancel',
      confirm: 'Delete Post',
      deleting: 'Deleting...',
    },

    // Flag dialog
    flag: {
      title: 'Flag Post',
      description: 'Please provide a reason for flagging this post for review.',
      reasonLabel: 'Reason',
      reasonPlaceholder: 'Enter the reason for flagging this post...',
      cancel: 'Cancel',
      confirm: 'Flag Post',
      flagging: 'Flagging...',
    },

    // Toast messages
    toast: {
      unpinnedSuccessfully: 'Post unpinned',
      pinnedSuccessfully: 'Post pinned',
      failedToUpdatePin: 'Failed to update pin status',
      unlockedSuccessfully: 'Post unlocked',
      lockedSuccessfully: 'Post locked',
      failedToUpdateLock: 'Failed to update lock status',
      unflaggedSuccessfully: 'Post unflagged successfully',
      failedToUnflag: 'Failed to unflag post',
      deletedSuccessfully: 'Post deleted successfully',
      failedToDelete: 'Failed to delete post',
      flaggedSuccessfully: 'Post flagged successfully',
      failedToFlag: 'Failed to flag post',
      reasonRequired: 'Please provide a reason for flagging',
    },

    // Error states
    error: {
      loadingPost: 'Error Loading Post',
      postNotFound: 'Post Not Found',
      notFoundDescription: "The post doesn't exist or you don't have permission to view it.",
      backToForumPosts: 'Back to Forum Posts',
      back: 'Back',
      retry: 'Retry',
    },
  },

  // Comments
  comments: {
    title: 'Comments',
    listTitle: 'Comment Management',
    searchPlaceholder: 'Search comments...',

    // Filters
    filters: {
      type: 'Type',
      allTypes: 'All Types',
      flagStatus: 'Flag Status',
      all: 'All',
      flagged: 'Flagged',
      clean: 'Clean',
    },

    // Types
    type: {
      forum: 'Forum',
      solution: 'Solution',
      unknown: 'Unknown',
    },

    // Table columns
    columns: {
      comment: 'Comment',
      author: 'Author',
      type: 'Type',
      status: 'Status',
      created: 'Created',
      actions: 'Actions',
    },

    // Status
    status: {
      deleted: 'Deleted',
      flagged: 'Flagged',
      active: 'Active',
      unknown: 'Unknown',
    },

    // Actions
    actions: {
      unflag: 'Unflag',
      flag: 'Flag',
      delete: 'Delete',
    },

    // Delete dialog
    delete: {
      title: 'Delete Comment',
      description: 'Are you sure you want to delete this comment? This action cannot be undone.',
      cancel: 'Cancel',
      confirm: 'Delete Comment',
      deleting: 'Deleting...',
    },

    // Flag dialog
    flag: {
      title: 'Flag Comment',
      description:
        'Flagging this comment will mark it for review and may hide it from public view depending on settings.',
      reasonLabel: 'Reason for flagging',
      reasonPlaceholder: 'Please explain why this comment violates community guidelines...',
      cancel: 'Cancel',
      confirm: 'Flag Comment',
      flagging: 'Flagging...',
    },

    // Toast messages
    toast: {
      unflaggedSuccessfully: 'Comment unflagged successfully',
      failedToUnflag: 'Failed to unflag comment',
      deletedSuccessfully: 'Comment deleted successfully',
      failedToDelete: 'Failed to delete comment',
      flaggedSuccessfully: 'Comment flagged successfully',
      failedToFlag: 'Failed to flag comment',
      reasonRequired: 'Please provide a reason for flagging',
    },
  },

  // Auth
  auth: {
    // Login
    login: {
      title: 'Admin Login',
      username: 'Username',
      password: 'Password',
      submit: 'Sign In',
      rememberMe: 'Remember me',
      forgotPassword: 'Forgot password?',
      noAccount: "Don't have an account?",
      signup: 'Sign up',
    },

    // Signup
    signup: {
      title: 'Admin Sign Up',
      username: 'Username',
      email: 'Email',
      password: 'Password',
      confirmPassword: 'Confirm Password',
      submit: 'Create Account',
      hasAccount: 'Already have an account?',
      login: 'Sign in',
      agreeToTerms: 'I agree to the Terms of Service',
    },

    // Logout
    logout: {
      confirm: 'Are you sure you want to log out?',
    },
  },

  // Validation messages
  validation: {
    required: '{field} is required',
    minLength: '{field} must be at least {min} characters',
    maxLength: '{field} must be at most {max} characters',
    email: 'Invalid email address',
    passwordMatch: 'Passwords do not match',
    url: 'Invalid URL',
    number: 'Must be a number',
    positive: 'Must be positive',
    integer: 'Must be an integer',
    range: 'Must be between {min} and {max}',
    unique: 'This value is already taken',
  },

  // Toast messages
  toast: {
    success: 'Success',
    error: 'Error',
    warning: 'Warning',
    info: 'Info',
    loadFailed: 'Failed to load data',
    loadSuccess: 'Data loaded successfully',
    saveSuccess: 'Saved successfully',
    saveFailed: 'Failed to save',
    deleteSuccess: 'Deleted successfully',
    deleteFailed: 'Failed to delete',
    updateSuccess: 'Updated successfully',
    updateFailed: 'Failed to update',
    createSuccess: 'Created successfully',
    createFailed: 'Failed to create',
  },

  // Pagination
  pagination: {
    rowsPerPage: 'Rows per page',
    of: 'of',
    page: 'Page',
    goTo: 'Go to',
    first: 'First',
    last: 'Last',
    showing: 'Showing',
    to: 'to',
    of_total: 'of',
    results: 'results',
  },

  // Data table
  table: {
    emptyState: 'No data available',
    searchPlaceholder: 'Search...',
    filterPlaceholder: 'Filter...',
    clearFilters: 'Clear Filters',
    showColumns: 'Show Columns',
    hideColumns: 'Hide Columns',
    resetColumns: 'Reset Columns',
    selectAll: 'Select All',
    deselectAll: 'Deselect All',
    selected: '{count} selected',
    selected_one: '{count} selected',
  },

  // Dialog labels
  dialog: {
    close: 'Close',
    confirm: 'Confirm',
    cancel: 'Cancel',
    delete: 'Delete',
    save: 'Save',
    submit: 'Submit',
  },

  // Empty states
  empty: {
    title: 'No data found',
    description: 'There are no items to display',
    action: 'Create your first item',
  },
} as const
