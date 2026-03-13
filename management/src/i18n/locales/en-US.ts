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

    // Additional labels
    reportedBy: 'Reported by',
    reportedAt: 'Reported at',
    submissions: 'Submissions',
    solutions: 'Solutions',
    page: 'Page',
    saving: 'Saving...',
    premium: 'Premium',
    unpublished: 'Unpublished',
    deleteConfirm: 'Confirm Delete',
    clearSelection: 'Clear Selection',
  },

  // Navigation
  nav: {
    dashboard: 'Dashboard',
    users: 'Users',
    problems: 'Problems',
    contests: 'Contests',
    submissions: 'Submissions',
    forum: 'Forum',
    settings: 'Settings',
    problemLists: 'Problem Lists',
    tags: 'Tags',
    solutions: 'Solutions',
    comments: 'Comments',
    notifications: 'Notifications',
    auditLogs: 'Audit Logs',
    moderation: 'Moderation',
    analytics: 'Analytics',
    getHelp: 'Get Help',
    search: 'Search',
    account: 'Account',
    billing: 'Billing',
    logout: 'Log out',
  },

  // Moderation
  moderation: {
    title: 'Moderation Queue',
    description: 'Review and manage flagged problems',
    filterStatus: 'Filter by Status',
    allStatuses: 'All Statuses',
    statusPending: 'Pending',
    statusReviewed: 'Reviewed',
    statusResolved: 'Resolved',
    statusDismissed: 'Dismissed',
    noFlagged: 'No Flagged Problems',
    noFlaggedDescription: 'There are currently no flagged problems requiring review.',
    flagReason: 'Flag Reason',
    moderationNotes: 'Moderation Notes',
    moderate: 'Moderate',
    moderateTitle: 'Moderate Problem',
    status: 'Status',
    notes: 'Notes',
    notesPlaceholder: 'Add moderation notes (optional)...',
    success: 'Problem moderated successfully',
    error: 'Failed to moderate problem',
    loadError: 'Failed to load flagged problems',
    // Batch moderation
    selectAll: 'Select All',
    selectedCount: '{count} selected',
    batchResolve: 'Batch Resolve',
    batchDismiss: 'Batch Dismiss',
    batchModerateTitle: 'Batch Moderate Problems',
    batchModerateDescription: 'You are about to update {count} problems.',
    batchSuccess: 'Successfully moderated {count} problems',
    batchPartial: 'Moderated {success} problems, {failed} failed',
    batchError: 'Failed to batch moderate problems',
    batchNotesPlaceholder: 'Add notes for all selected problems (optional)...',
    newStatus: 'New Status',
    apply: 'Apply',
    // Redesign keys
    columns: {
      problem: 'Problem',
    },
    quickResolve: 'Quick Resolve',
    quickDismiss: 'Quick Dismiss',
    unknownReporter: 'Unknown',
    drawerTitle: 'Moderation Details',
    drawerDescription: 'View flag information and moderate',
    problemDetails: 'Problem Details',
    flagInfo: 'Flag Information',
    moderationActions: 'Moderation Actions',
    searchPlaceholder: 'Search problems...',
    allDifficulties: 'All Difficulties',
  },

  // Analytics
  analytics: {
    title: 'Advanced Analytics',
    description: 'Comprehensive reports and insights',
    loadError: 'Failed to load analytics data',
    noData: 'No data available for this report',
    // Navigation labels
    nav: {
      userActivity: 'User Activity',
      problemCompletion: 'Problems',
      contestParticipation: 'Contests',
      revenue: 'Revenue',
      performance: 'Performance',
    },
    // Status labels
    status: {
      good: 'Good',
      excellent: 'Excellent',
      average: 'Average',
      needsWork: 'Needs Work',
      needsAttention: 'Needs Attention',
      high: 'High',
      normal: 'Normal',
    },
    tabs: {
      userActivity: 'User Activity',
      problemCompletion: 'Problem Completion',
      contestParticipation: 'Contest Participation',
      revenue: 'Revenue',
      performance: 'Performance',
    },
    periods: {
      '7days': 'Last 7 Days',
      '30days': 'Last 30 Days',
      '90days': 'Last 90 Days',
      '1year': 'Last Year',
    },
    perContest: 'per contest',
    userActivity: {
      dailyActiveUsers: 'Daily Active Users',
      retention1d: '1-Day Retention',
      retention7d: '7-Day Retention',
      retention30d: '30-Day Retention',
      activeUsersTrend: 'Active Users Trend',
      activeUsersTrendDesc: 'Daily active users over the selected period',
      peakHours: 'Peak Activity Hours',
      peakHoursDesc: 'User activity distribution by hour of day',
      topUsers: 'Most Active Users',
      topUsersDesc: 'Users with highest login activity',
      logins: '{count} logins',
    },
    problemCompletion: {
      totalAttempts: 'Total Attempts',
      successfulAttempts: 'Successful Attempts',
      completionRate: 'Completion Rate',
      trendingProblems: 'Trending Problems',
      byDifficulty: 'Completion by Difficulty',
      byDifficultyDesc: 'Success rate breakdown by problem difficulty',
      hardestProblems: 'Hardest Problems',
      hardestProblemsDesc: 'Problems with lowest completion rates',
      topTags: 'Top Tags by Completion',
      topTagsDesc: 'Most popular tags with their completion rates',
      completed: 'completed',
    },
    contestParticipation: {
      totalContests: 'Total Contests',
      totalParticipants: 'Total Participants',
      avgParticipants: 'Avg Participants',
      virtualParticipation: 'Virtual Participation',
      byType: 'Participation by Type',
      byTypeDesc: 'Average participants by contest type',
      topContests: 'Most Popular Contests',
      topContestsDesc: 'Contests with highest participation',
      contests: 'contests',
    },
    contestParticipants: 'participants',
    revenue: {
      mrr: 'Monthly Recurring Revenue',
      arr: 'Annual Recurring Revenue',
      subscribers: 'Active Subscribers',
      conversionRate: 'Conversion Rate',
      byPlan: 'Revenue by Plan',
      byPlanDesc: 'Monthly revenue breakdown by subscription plan',
      metrics: 'Key Metrics',
      arpu: 'Average Revenue Per User',
      churnRate: 'Churn Rate',
      totalRevenue: 'Total Revenue',
    },
    performance: {
      uptime: 'System Uptime',
      throughput: 'Throughput',
      errorRate: 'Error Rate',
      memoryUsage: 'Memory Usage',
      resourceUsage: 'Resource Usage',
      slowestEndpoints: 'Slowest Endpoints',
      slowestEndpointsDesc: 'API endpoints with highest response times',
      requests: 'requests',
    },
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

    // Time period selector
    timePeriod: {
      last7Days: '7d',
      last30Days: '30d',
      last90Days: '90d',
      allTime: 'All',
    },

    // Timeline
    timeline: {
      title: 'Activity Timeline',
      description: 'Recent administrative actions',
      viewAll: 'View All Activity',
      activityTypes: {
        LOGIN: 'Login',
        CREATE: 'Created',
        UPDATE: 'Updated',
        DELETE: 'Deleted',
        PUBLISH: 'Published',
        UNPUBLISH: 'Unpublished',
        FLAG: 'Flagged',
        UNFLAG: 'Unflagged',
        BAN: 'Banned',
        UNBAN: 'Unbanned',
        MODERATE: 'Moderated',
        PIN: 'Pinned',
        UNPIN: 'Unpinned',
        LOCK: 'Locked',
        UNLOCK: 'Unlocked',
      },
    },

    // Last updated
    lastUpdated: 'Last updated: {time}',
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
      username: 'Username',
      bannedAt: 'Banned At',
    },

    // Dialog titles
    editUser: 'Edit User',
    createUser: 'Create User',
    editDescription: "Make changes to the user profile here. Click save when you're done.",
    createDescription: "Add a new user to the system. Click create when you're done.",

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
      resetPasswordFailed: 'Failed to reset password',
      resetPasswordFailedDescription: 'An error occurred while attempting to update the password.',
      banFailed: 'Failed to ban user',
    },

    // Form labels
    form: {
      username: 'Username',
      email: 'Email',
      displayName: 'Display Name',
      fullName: 'Full Name',
      fullNamePlaceholder: 'John Doe',
      usernamePlaceholder: 'johndoe',
      emailPlaceholder: 'john@example.com',
      role: 'Role',
      status: 'Status',
      password: 'Password',
      confirmPassword: 'Confirm Password',
      newPassword: 'New Password',
      newPasswordPlaceholder: 'Enter new password',
      isActive: 'Active',
      isBanned: 'Banned',
      banReason: 'Ban Reason',
      banReasonPlaceholder: 'Violation of terms...',
      banExpiresAt: 'Ban Expires At',
      avatar: 'Avatar URL',
      targetUser: 'Target User',
      saving: 'Saving...',
      creating: 'Creating...',
      saveChanges: 'Save Changes',
      createUser: 'Create User',
    },

    // Actions
    actions: {
      viewDetails: 'View Details',
      editProfile: 'Edit Profile',
      resetPassword: 'Reset Password',
      resetPasswordAction: 'Reset Password',
      resetting: 'Resetting...',
      resetPasswordDescription: 'Set a new password for {username}.',
      resetPasswordWarning:
        "This will immediately change the user's password. Make sure to communicate the new password securely.",
      banUser: 'Ban User',
      banUserDescription: 'Please provide a reason for banning {username}.',
      confirmBan: 'Confirm Ban',
      banning: 'Banning...',
      unbanUser: 'Unban User',
      deleteUser: 'Delete User',
      cancel: 'Cancel',
      thisUser: 'this user',
    },
  },

  // Problems
  problems: {
    title: 'Problems',
    listTitle: 'Problem Management',
    addProblem: 'Add Problem',
    searchPlaceholder: 'Search problems...',

    // Create
    create: {
      title: 'Create Problem',
      description: 'Create a new problem for the platform',
    },

    // Filters
    filters: {
      difficulty: 'Difficulty',
      allLevels: 'All Levels',
      allDifficulty: 'All Difficulty',
      status: 'Status',
      allStatus: 'All Status',
      allPublished: 'All Published',
      visibility: 'Visibility',
      any: 'Any',
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
      DRAFT: 'Draft',
      PUBLISHED: 'Published',
      ARCHIVED: 'Archived',
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
      flagged: 'Flagged',
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
      audit: 'Audit Logs',
    },

    // Actions
    actions: {
      view: 'View',
      edit: 'Edit',
      publish: 'Publish',
      unpublish: 'Unpublish',
      flag: 'Flag',
      unflag: 'Unflag',
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
      deleteSuccess: 'Problem deleted successfully',
      deleteFailed: 'Failed to delete problem',
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

    // Bulk actions
    bulk: {
      selected: '{count} selected',
      noSelection: 'Please select at least one problem',
      action: 'bulk action',
      publish: 'Publish',
      unpublish: 'Unpublish',
      delete: 'Delete',
      restore: 'Restore',
      publishTitle: 'Publish {count} Problems',
      publishDescription: 'Are you sure you want to publish {count} problem(s)?',
      unpublishTitle: 'Unpublish {count} Problems',
      unpublishDescription: 'Are you sure you want to unpublish {count} problem(s)?',
      deleteTitle: 'Delete {count} Problems',
      deleteDescription:
        'Are you sure you want to delete {count} problem(s)? This action is IRREVERSIBLE.',
      restoreTitle: 'Restore {count} Problems',
      restoreDescription: 'Are you sure you want to restore {count} problem(s)?',
      confirmPublish: 'Publish {count} Problem(s)',
      confirmUnpublish: 'Unpublish {count} Problem(s)',
      confirmDelete: 'Delete {count} Problem(s)',
      confirmRestore: 'Restore {count} Problem(s)',
      warning: 'Warning',
      warningDescription: 'This action will affect the selected problems and cannot be undone.',
      success: 'Successfully {action} {count} problem(s)',
      failed: 'Failed to {action} {count} problem(s)',
      partial: 'Partially completed: {success} succeeded, {failed} failed',
    },

    // Bulk edit
    bulkEdit: {
      title: 'Bulk Edit',
      description: 'Edit {count} problem(s)',
      category: 'Category',
      categoryPlaceholder: 'e.g. Array, String, Tree',
      categoryHint: 'Leave empty to keep current category',
      difficulty: 'Difficulty',
      difficultyPlaceholder: 'Select difficulty',
      tags: 'Tags',
      tagsPlaceholder: 'Add tags (press Enter)',
      addTag: 'Add',
      tagsHint: 'Leave empty to keep current tags',
      premium: 'Premium',
      premiumHint: 'Toggle premium status for all selected problems',
      noChanges: 'No changes to apply',
      edit: 'Bulk Edit',
      editing: 'Editing...',
      success: 'Successfully edited {count} problem(s)',
      failure: 'Failed to edit problems',
      partial: 'Partially completed: {success} succeeded, {failed} failed',
      error: 'Failed to bulk edit problems',
    },

    // Sort
    sort: {
      title: 'Sort By',
      default: 'Default',
      titleAsc: 'Title (A-Z)',
      titleDesc: 'Title (Z-A)',
      difficultyAsc: 'Difficulty (Easy to Hard)',
      difficultyDesc: 'Difficulty (Hard to Easy)',
      createdAsc: 'Created (Oldest First)',
      createdDesc: 'Created (Newest First)',
      updatedAsc: 'Updated (Oldest First)',
      updatedDesc: 'Updated (Newest First)',
      submissionsAsc: 'Submissions (Low to High)',
      submissionsDesc: 'Submissions (High to Low)',
    },

    // Export
    export: {
      title: 'Export',
      json: 'Export as JSON',
      csv: 'Export as CSV',
      success: 'Problems exported successfully',
      error: 'Failed to export problems',
    },

    // Import
    import: {
      title: 'Import',
      description: 'Import problems from JSON or CSV files',
      dropFile: 'Drag and drop your file here, or click to browse',
      browse: 'Browse',
      supportedFormats: 'Supported formats: JSON, CSV',
      conflictStrategy: 'Conflict Strategy',
      conflictStrategyDescription: 'How to handle problems with existing slugs',
      strategies: {
        skip: 'Skip existing',
        update: 'Update existing',
        createNew: 'Create new with suffix',
      },
      strategyDescriptions: {
        skip: 'Skip problems that already exist',
        update: 'Update existing problems with new data',
        create_new: 'Create new problems with a suffix added to the slug',
      },
      import: 'Import',
      cancel: 'Cancel',
      close: 'Close',
      clear: 'Clear',
      success: 'Import completed',
      error: 'Import failed',
      partialSuccess: 'Partially completed: {success} of {total} imported',
      someErrors: 'Some problems could not be imported. See details below.',
      results: 'Import Results',
      total: 'Total',
      created: 'Created',
      updated: 'Updated',
      skipped: 'Skipped',
      failed: 'Failed',
      imported: 'Imported',
      errors: 'Errors',
      noFile: 'No file selected',
      invalidFile: 'Invalid file format',
      importing: 'Importing...',
      importProgress: 'Importing... {progress}%',
    },

    // Version history
    versionHistory: {
      title: 'Version History',
      description: 'View and manage version history of this problem',
      noVersions: 'No version history available',
      viewVersion: 'View version',
      viewDetails: 'View details',
      restoreVersion: 'Restore this version',
      restoreTitle: 'Restore Version',
      restoreDescription:
        'Are you sure you want to restore this version? The current version will be saved as a new version.',
      restoreSuccess: 'Version restored successfully',
      restoreError: 'Failed to restore version',
      changes: 'Changes',
      noChanges: 'No changes',
      added: 'Added',
      removed: 'Removed',
      changed: 'Changed',
      compareWith: 'Compare with',
      compare: 'Compare with current version',
      compareVersions: 'Compare Versions',
      oldValue: 'Old Value',
      newValue: 'New Value',
      currentVersion: 'Current Version',
      version: 'Version',
      versionDetails: 'Version Details',
      performedBy: 'Performed by',
      by: 'by',
      at: 'at',
      loadError: 'Failed to load version history',
      loadDetailError: 'Failed to load version details',
      compareError: 'Failed to compare versions',
      rollback: 'Rollback to this version',
      rollbackTitle: 'Rollback to Version',
      rollbackConfirm:
        'Are you sure you want to rollback to version {version}? This will create a new version with the content from that version.',
      rollbackReason: 'Reason (optional)',
      rollbackReasonPlaceholder: 'Enter a reason for this rollback...',
      rollbackButton: 'Rollback',
      rollbackSuccess: 'Successfully rolled back to version {version}',
      rollbackError: 'Failed to rollback to version',
      action: {
        CREATE: 'Created',
        UPDATE: 'Updated',
        DELETE: 'Deleted',
        RESTORE: 'Restored',
      },
    },

    // Moderation
    moderation: {
      title: 'Moderation Queue',
      description: 'Review and manage flagged problems',
      filterStatus: 'Filter by Status',
      allStatuses: 'All Statuses',
      statusPending: 'Pending',
      statusReviewed: 'Reviewed',
      statusResolved: 'Resolved',
      statusDismissed: 'Dismissed',
      noFlagged: 'No Flagged Problems',
      noFlaggedDescription: 'There are no flagged problems to review at this time.',
      flagReason: 'Flag Reason',
      moderationNotes: 'Moderation Notes',
      moderate: 'Moderate',
      moderateTitle: 'Moderate Problem',
      status: 'Status',
      notes: 'Notes',
      notesPlaceholder: 'Add moderation notes (optional)...',
      success: 'Problem moderated successfully',
      error: 'Failed to moderate problem',
      loadError: 'Failed to load flagged problems',
      reasonPrompt: 'Please enter a reason for flagging this problem:',
      flagSuccess: 'Problem flagged successfully',
      unflagSuccess: 'Problem unflagged successfully',
      flag: 'Flag',
      unflag: 'Unflag',
    },
  },

  // Test Cases (Hidden Test Cases for judging)
  testCases: {
    title: 'Hidden Test Cases',
    sample: 'Sample',
    hidden: 'Hidden',
    add: 'Add Case',
    import: 'Import',
    export: 'Export',
    input: 'Input',
    output: 'Output',
    explanation: 'Explanation',
    noTestCases: 'No hidden test cases configured',
    addFirst: 'Add First Test Case',
    isSample: 'Is Sample',
    isHidden: 'Is Hidden',
    editTestCase: 'Edit Test Case',
    createTestCase: 'Create Test Case',
    inputPlaceholder: 'Enter test case input...',
    outputPlaceholder: 'Enter expected output...',
    explanationPlaceholder: 'Optional explanation...',
    markAsSample: 'Mark as Sample',
    markAsHidden: 'Mark as Hidden',
    makeVisible: 'Make Visible',
    makeHidden: 'Make Hidden',
    confirmDelete: 'Are you sure you want to delete this test case?',
    importTestCases: 'Import Test Cases',
    importData: 'Import Data',
    importPlaceholder:
      'Paste JSON array of test cases or use the format:\nInput\n---\nOutput\n---\n...',
    importHelp:
      'JSON format: [{ "input_text": "...", "output_text": "..." }] or custom format with --- separators',
    replaceExisting: 'Replace existing test cases',
    importing: 'Importing...',

    // Validation
    validation: {
      inputOutputRequired: 'Input and output are required',
      importTextRequired: 'Import data is required',
      noValidTestCases: 'No valid test cases found in import data',
    },

    // Toast messages
    toast: {
      loadFailed: 'Failed to load test cases',
      createSuccess: 'Test case created successfully',
      updateSuccess: 'Test case updated successfully',
      saveFailed: 'Failed to save test case',
      deleteSuccess: 'Test case deleted successfully',
      deleteFailed: 'Failed to delete test case',
      exportSuccess: 'Test cases exported successfully',
      exportFailed: 'Failed to export test cases',
      importSuccess: '{count} test cases imported successfully',
      importFailed: 'Failed to import test cases',
      updateFailed: 'Failed to update test case',
    },
  },

  // System Monitoring
  monitoring: {
    title: 'System Monitoring',
    description: 'Monitor system health, resources, and performance',
    healthStatus: 'System Health',
    lastChecked: 'Last checked',
    systemInfo: 'System Information',
    nodeVersion: 'Node Version',
    platform: 'Platform',
    uptime: 'Uptime',
    environment: 'Environment',
    memoryUsage: 'Memory Usage',
    heapUsed: 'Heap Used',
    heapTotal: 'Heap Total',
    rss: 'RSS',
    database: 'Database',
    activeConnections: 'Active Connections',
    maxConnections: 'Max Connections',
    queryCount: 'Query Count',
    slowQueries: 'Slow Queries',
    connected: 'Connected',
    disconnected: 'Disconnected',
    version: 'Version',
    usedMemory: 'Used Memory',
    queues: 'Job Queues',
    noQueues: 'No queues configured',
    paused: 'Paused',
    waiting: 'Waiting',
    active: 'Active',
    completed: 'Completed',
    failed: 'Failed',
    delayed: 'Delayed',
    status: {
      status: 'Status',
      healthy: 'Healthy',
      unhealthy: 'Unhealthy',
      degraded: 'Degraded',
      unknown: 'Unknown',
    },
  },

  // Backup & Recovery
  backup: {
    title: 'Backup & Recovery',
    description: 'Manage database backups and restore points',
    createBackup: 'Create Backup',
    backupList: 'Backup List',
    totalBackups: 'Total Backups',
    completedBackups: 'Completed',
    pendingBackups: 'In Progress',
    noBackups: 'No backups available',
    type: 'Type',
    size: 'Size',
    createdAt: 'Created',
    restoreBackup: 'Restore Backup',
    restoreWarning:
      'This will replace the current database with the backup. This action cannot be undone.',
    restoreConfirm: 'Are you sure you want to restore from {filename}?',
    restore: 'Restore',
    deleteBackup: 'Delete Backup',
    deleteConfirm: 'Are you sure you want to delete {filename}?',
    status: {
      PENDING: 'Pending',
      IN_PROGRESS: 'In Progress',
      COMPLETED: 'Completed',
      FAILED: 'Failed',
    },
    toast: {
      loadFailed: 'Failed to load backups',
      createSuccess: 'Backup started successfully',
      createFailed: 'Failed to create backup',
      downloadSuccess: 'Backup downloaded successfully',
      downloadFailed: 'Failed to download backup',
      restoreSuccess: 'Database restored successfully',
      restoreFailed: 'Failed to restore database',
      deleteSuccess: 'Backup deleted successfully',
      deleteFailed: 'Failed to delete backup',
    },
  },

  // Email Management
  email: {
    title: 'Email Management',
    description: 'Manage email templates and view email logs',
    sendEmail: 'Send Email',
    send: 'Send',
    to: 'To',
    createdAt: 'Created',
    tabs: {
      logs: 'Email Logs',
      templates: 'Templates',
    },
    logs: {
      title: 'Email Logs',
      noLogs: 'No email logs available',
    },
    templates: {
      title: 'Email Templates',
      noTemplates: 'No templates available',
      create: 'Create Template',
      edit: 'Edit Template',
    },
    stats: {
      total: 'Total Emails',
      sent: 'Sent',
      pending: 'Pending',
      failed: 'Failed',
    },
    status: {
      PENDING: 'Pending',
      SENT: 'Sent',
      FAILED: 'Failed',
    },
    form: {
      to: 'Recipient',
      subject: 'Subject',
      subjectPlaceholder: 'Email subject...',
      body: 'Body (HTML)',
      bodyPlaceholder: '<p>Email content...</p>',
      name: 'Template Name',
      namePlaceholder: 'Welcome Email',
      variables: 'Variables',
      variablesPlaceholder: 'name, email, company',
      variablesHelp: 'Comma-separated list of variables. Use {{variable}} in subject/body.',
    },
    deleteConfirm: 'Are you sure you want to delete template "{name}"?',
    validation: {
      required: 'Recipient and subject are required',
    },
    toast: {
      loadFailed: 'Failed to load email data',
      sendSuccess: 'Email sent successfully',
      sendFailed: 'Failed to send email',
      createSuccess: 'Template created successfully',
      updateSuccess: 'Template updated successfully',
      saveFailed: 'Failed to save template',
      deleteSuccess: 'Template deleted successfully',
      deleteFailed: 'Failed to delete template',
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
      status: {
        notStarted: 'Not Started',
        ongoing: 'Ongoing',
        finished: 'Finished',
      },
      type: {
        ioi: 'IOI',
        icpc: 'ICPC',
        custom: 'Custom',
      },
    },

    // Type
    type: {
      PUBLIC: 'Public',
      PRIVATE: 'Private',
      VIRTUAL: 'Virtual',
      weekly: 'Weekly',
      biweekly: 'Biweekly',
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
      description: 'Are you sure you want to delete {title}? This action cannot be undone.',
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
    newNotification: 'New Notification',
    allTypes: 'All Types',
    sentAt: 'Sent At',
    sentBy: 'Sent By',
    refresh: 'Refresh',

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
      messageContent: 'Message Content',
      messageContentDescription: 'The notification message to send to users.',
      notificationTitle: 'Title',
      notificationTitlePlaceholder: 'Notification title',
      notificationContent: 'Content',
      notificationContentPlaceholder: 'Notification content...',
      classification: 'Classification',
      classificationDescription: 'Type and category for the notification.',
      selectType: 'Select type',
      category: 'Category',
      selectCategory: 'Select category',
      targetAudience: 'Target Audience',
      targetAudienceDescription: 'Who should receive this notification.',
      allUsers: 'All Users (Broadcast)',
      specificUsers: 'Specific Users',
      userIds: 'User IDs',
      userIdsPlaceholder: 'Comma separated User IDs (e.g. user1, user2)',
      select: 'Select',
      atLeastOneUserId: 'At least one User ID is required',
    },

    // Types
    type: {
      info: 'Info',
      warning: 'Warning',
      success: 'Success',
      error: 'Error',
      SYSTEM: 'SYSTEM',
      SECURITY: 'SECURITY',
      CONTEST: 'CONTEST',
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

    // Dialogs
    dialog: {
      createTitle: 'New Notification',
      createDescription: 'Create and send a notification to users.',
      deleteTitle: 'Delete Notification',
      deleteDescription:
        'Are you sure you want to delete "{title}"? This action cannot be undone and the notification will be removed for all users.',
      deleteFallback: 'this notification',
      sending: 'Sending...',
      sendNotification: 'Send Notification',
      deleting: 'Deleting...',
    },

    // Toast messages
    toast: {
      sentSuccessfully: 'Notification sent successfully',
      failedToSend: 'Failed to send notification',
      deletedSuccessfully: 'Notification deleted',
      failedToDelete: 'Failed to delete notification',
    },

    // Delete dialog
    delete: {
      title: 'Delete Notification',
      description:
        'Are you sure you want to delete this notification? This action cannot be undone.',
      confirm: 'Delete Notification',
      cancel: 'Cancel',
    },

    // Delete result messages
    deleteSuccess: 'Notification deleted successfully',
    deleteError: 'Failed to delete notification',

    // Category
    category: {
      SYSTEM: 'SYSTEM',
      CONTEST: 'CONTEST',
      ACCOUNT: 'ACCOUNT',
      GENERAL: 'GENERAL',
    },

    // Target
    target: {
      ALL: 'ALL',
      USERS: 'USERS',
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
      exportLogs: 'Export Logs',
      create: 'Create',
      update: 'Update',
      delete: 'Delete',
      publish: 'Publish',
      moderate: 'Moderate',
    },

    // Audit log viewer
    filterAction: 'Filter by Action',
    allActions: 'All Actions',
    noLogs: 'No Audit Logs',
    noLogsDescription: 'There are no audit logs to display.',
    systemAction: 'System Action',
    oldValues: 'Old Values',
    newValues: 'New Values',
    ipAddress: 'IP Address',
    userAgent: 'User Agent',
  },

  // Audit Logs (for AuditLogViewer component)
  auditLogs: {
    searchPlaceholder: 'Search logs...',
    filterAction: 'Filter by Action',
    allActions: 'All Actions',
    noLogs: 'No Audit Logs',
    noLogsDescription: 'There are no audit logs to display.',
    export: 'Export',
    systemAction: 'System Action',
    oldValues: 'Old Values',
    newValues: 'New Values',
    ipAddress: 'IP Address',
    userAgent: 'User Agent',
    actions: {
      create: 'Create',
      update: 'Update',
      delete: 'Delete',
      publish: 'Publish',
      moderate: 'Moderate',
    },
  },

  // Audit Report
  auditReport: {
    title: 'Audit Report',
    description: 'View audit statistics and generate reports',
    filters: 'Filters',
    startDate: 'Start Date',
    endDate: 'End Date',
    performer: 'Performer',
    performerPlaceholder: 'Filter by performer ID...',
    applyFilters: 'Apply Filters',
    export: 'Export Report',
    totalActions: 'Total Actions',
    allTime: 'All time',
    uniqueEntities: 'Unique Entities',
    entityTypes: 'Entity types',
    activePerformers: 'Active Performers',
    users: 'Users',
    topPerformers: 'Top Performers',
    actionsByEntity: 'Actions by Entity',
    actions: 'actions',
  },

  // Settings
  settings: {
    title: 'System Settings',
    description: 'Manage global system configuration and preferences.',

    // Tabs
    tabs: {
      general: 'General',
      email: 'Email',
      rateLimits: 'Rate Limits',
      uploads: 'Uploads',
      features: 'Features',
    },

    // Toast messages
    toast: {
      loadFailed: 'Failed to load settings',
      saveFailed: 'Failed to save settings',
      clearCacheFailed: 'Failed to clear cache',
      resetFailed: 'Failed to reset settings',
    },

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
    email: {
      title: 'Email Configuration',
      description: 'Configure SMTP settings for sending emails.',
      smtpHost: 'SMTP Host',
      smtpPort: 'SMTP Port',
      smtpUser: 'SMTP Username',
      smtpPassword: 'SMTP Password',
      smtpFrom: 'From Email Address',
      smtpFromName: 'From Name',
      smtpSecure: 'Use TLS',
      smtpSecureDescription: 'Enable TLS encryption for SMTP connection',
    },

    // Rate limits
    rateLimits: {
      title: 'Rate Limit Settings',
      description: 'Configure rate limits for various operations.',
      api: 'API Rate Limit',
      apiDescription: 'Maximum API requests per minute per user',
      submission: 'Submission Rate Limit',
      submissionDescription: 'Maximum code submissions per minute',
      auth: 'Authentication Rate Limit',
      authDescription: 'Maximum login/register attempts per minute',
      upload: 'Upload Rate Limit',
      uploadDescription: 'Maximum file uploads per minute',
    },

    // Upload settings
    uploads: {
      title: 'Upload Settings',
      description: 'Configure file upload limits and restrictions.',
      maxSize: 'Maximum File Size',
      maxSizeDescription: 'Enter size with unit (e.g., 10 MB, 1 GB)',
      allowedTypes: 'Allowed File Types',
      allowedTypesDescription: 'Comma-separated list of allowed file extensions',
      maxFiles: 'Maximum Files per Upload',
      maxFilesDescription: 'Maximum number of files in a single upload',
    },

    // Feature toggles
    features: {
      title: 'Feature Toggles',
      description: 'Enable or disable platform features.',
      contest: 'Contests',
      contestDescription: 'Enable contest and competition features',
      forum: 'Forum',
      forumDescription: 'Enable community forum and discussions',
      solutions: 'Solutions',
      solutionsDescription: 'Enable solution sharing and viewing',
      subscriptions: 'Subscriptions',
      subscriptionsDescription: 'Enable premium subscription features',
      achievements: 'Achievements',
      achievementsDescription: 'Enable achievements and badges',
      notifications: 'Notifications',
      notificationsDescription: 'Enable push and email notifications',
      bookmarks: 'Bookmarks',
      bookmarksDescription: 'Enable problem bookmarking feature',
      problemLists: 'Problem Lists',
      problemListsDescription: 'Enable curated problem lists',
    },

    // System settings
    general: {
      title: 'General Settings',
      description: 'Basic site information.',
    },
    siteName: 'Site Name',
    siteDescription: 'Site Description',

    // User registration
    userRegistration: {
      title: 'User Registration',
      description: 'Control how users sign up.',
      enableRegistrations: 'Enable Registrations',
      enableRegistrationsDescription: 'Allow new users to create accounts.',
      requireEmailVerification: 'Require Email Verification',
      requireEmailVerificationDescription: 'Users must verify email before logging in.',
    },

    // System status
    systemStatus: {
      title: 'System Status',
      description: 'Control site availability.',
      maintenanceMode: 'Maintenance Mode',
      maintenanceModeDescription: 'Only admins can access the site when enabled.',
      maintenanceMessage: 'Maintenance Message',
    },

    // Actions
    actions: {
      title: 'Actions',
      clearCache: 'Clear System Cache',
      saveChanges: 'Save Changes',
      saving: 'Saving...',
      resetToDefaults: 'Reset to Defaults',
      resetConfirmTitle: 'Reset Settings to Defaults?',
      resetConfirmDescription:
        'This will restore all settings to their default values. This action cannot be undone.',
      resetConfirm: 'Reset',
    },
  },

  // Problem Lists
  problemLists: {
    title: 'Problem Lists',
    addList: 'Add List',
    searchPlaceholder: 'Search lists...',
    createList: 'Create Problem List',
    editList: 'Edit List',
    backToLists: 'Back to Lists',
    errorLoading: 'Error Loading List',
    generalInfo: 'General Info',
    problems: 'Problems',

    // Table columns
    columns: {
      name: 'Name',
      featured: 'Featured',
      visibility: 'Visibility',
      problems: 'Problems',
      order: 'Order',
      createdAt: 'Created',
    },

    // Filters
    filters: {
      type: 'Type',
      allTypes: 'All Types',
      featured: 'Featured',
      standard: 'Standard',
      visibility: 'Visibility',
      allVisibility: 'All Visibility',
      public: 'Public',
      private: 'Private',
    },

    // Actions
    actions: {
      view: 'View',
      edit: 'Edit',
      delete: 'Delete',
    },

    // Form
    form: {
      name: 'Name',
      namePlaceholder: 'e.g. Top 100 Dynamic Programming',
      description: 'Description',
      descriptionPlaceholder: 'Describe what this list is about...',
      isPublic: 'Public',
      isPublicDescription: 'Make this list visible to all users',
      isFeatured: 'Featured',
      isFeaturedDescription: 'Show this list on the home page',
      bannerTag: 'Banner Tag',
      bannerTagPlaceholder: 'e.g. POPULAR',
      bannerTagDescription: 'Small tag shown on the banner card',
      bannerTheme: 'Banner Theme',
      bannerThemePlaceholder: 'Select a theme',
      sortOrder: 'Sort Order',
      sortOrderDescription: 'Order in featured lists section (lower first)',
      saving: 'Saving...',
      saveChanges: 'Save Changes',
      validation: {
        nameRequired: 'Name is required',
      },
    },

    // Themes
    themes: {
      blue: 'Blue',
      green: 'Green',
      purple: 'Purple',
      orange: 'Orange',
      red: 'Red',
    },

    // Problems manager
    problemsManager: {
      title: 'Problems',
      addProblem: 'Add Problem',
      saveChanges: 'Save Changes',
      saving: 'Saving...',
      order: 'Order',
      problem: 'Problem',
      difficulty: 'Difficulty',
      noProblems: 'No problems in this list.',
      addProblems: 'Add Problems',
      removeProblems: 'Remove Problems',
      selectedProblems: 'Selected Problems',
      availableProblems: 'Available Problems',
      reorder: 'Reorder',
    },

    // Delete dialog
    delete: {
      title: 'Delete Problem List',
      description: 'Are you sure you want to delete {name}? This action cannot be undone.',
      thisList: 'this list',
      confirm: 'Delete List',
      deleting: 'Deleting...',
      cancel: 'Cancel',
    },

    // Toast messages
    toast: {
      createdSuccess: 'List created successfully',
      updatedSuccess: 'List updated successfully',
      deletedSuccess: 'Problem list deleted successfully',
      createFailed: 'Failed to save list',
      deleteFailed: 'Failed to delete problem list',
      problemsUpdated: 'Problems updated successfully',
      problemsUpdateFailed: 'Failed to update problems',
    },
  },

  // Tags
  tags: {
    title: 'Tags',
    addTag: 'Add Tag',
    searchPlaceholder: 'Search tags...',
    mergeTags: 'Merge Tags',
    selected: '{count} tags selected',
    selected_one: '{count} tag selected',
    clearSelection: 'Clear Selection',
    bulkDelete: 'Bulk Delete',
    createTag: 'Create Tag',
    tagType: 'Tag Type',
    problemTags: 'Problem Tags',
    forumTags: 'Forum Tags',
    retry: 'Retry',

    // Table columns
    columns: {
      name: 'Name',
      label: 'Label',
      color: 'Color',
      problems: 'Problems',
      createdAt: 'Created',
      tag: 'Tag',
      usage: 'Usage',
      description: 'Description',
      actions: 'Actions',
    },

    // Actions
    actions: {
      edit: 'Edit',
      delete: 'Delete',
      merge: 'Merge With...',
      mergeInto: 'Merge into...',
      noActionsAvailable: 'No actions available',
    },

    // Form
    form: {
      name: 'Tag Name',
      label: 'Display Label',
      color: 'Color',
      description: 'Description',
      slug: 'Slug (Optional)',
      slugPlaceholder: 'dynamic-programming',
      colorHex: 'Color (Hex)',
      colorPlaceholder: '#3b82f6',
      namePlaceholder: 'Dynamic Programming',
      descriptionPlaceholder: 'Tag description...',
      editTitle: 'Edit Tag',
      createTitle: 'Create Tag',
      editDescription: 'Make changes to the tag here.',
      createDescription: 'Add a new tag to the system.',
      saveChanges: 'Save Changes',
      createTag: 'Create Tag',
      nameRequired: 'Name is required',
      nameTooLong: 'Name is too long',
    },

    // Delete dialog
    delete: {
      title: 'Delete Tag',
      description:
        'Are you sure you want to delete the tag "{name}"? This action cannot be undone.',
      confirm: 'Delete Tag',
    },

    // Merge dialog
    merge: {
      title: 'Merge Tags',
      description:
        'Merge "{source}" into another tag. All relations will be moved to the target tag, and the source tag will be deleted.',
      targetTag: 'Target Tag',
      targetTagPlaceholder: 'Select a tag to merge into',
      confirm: 'Merge Tags',
      sourceTag: 'Source Tag (will be deleted)',
      targetTagLabel: 'Target Tag (will be kept)',
      mergeConfirm:
        'Merge tags? All problems tagged with "{source}" will be retagged as "{target}".',
    },

    // Toast messages
    toast: {
      createdSuccessfully: 'Tag created successfully',
      updatedSuccessfully: 'Tag updated successfully',
      deletedSuccessfully: 'Tag deleted successfully',
      mergedSuccessfully: 'Tags merged successfully',
      failedToCreate: 'Failed to create tag',
      failedToUpdate: 'Failed to update tag',
      failedToDelete: 'Failed to delete tag',
      failedToMerge: 'Failed to merge tags',
      bulkDeleteSuccess: '{count} tags deleted',
      bulkDeleteFailed: 'Failed to delete some tags',
      bulkDeleteConfirm:
        'Are you sure you want to delete {count} tags? This action is IRREVERSIBLE.',
      bulkDeleteConfirm_one:
        'Are you sure you want to delete {count} tag? This action is IRREVERSIBLE.',
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
        'Are you sure you want to delete the solution "{title}"? This action cannot be undone.',
      cancel: 'Cancel',
      confirm: 'Delete Solution',
      deleting: 'Deleting...',
    },

    // Flag dialog
    flag: {
      title: 'Flag Solution',
      description:
        'Flagging solution "{title}" will mark it for review and may hide it from public view depending on settings.',
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
      pinnedOnly: 'Pinned Only',
      unpinnedOnly: 'Unpinned Only',
      lockedOnly: 'Locked Only',
      unlockedOnly: 'Unlocked Only',
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
      bulkPinnedSuccessfully: 'Posts pinned successfully',
      bulkLockedSuccessfully: 'Posts locked successfully',
      bulkUnflaggedSuccessfully: 'Posts unflagged successfully',
      bulkDeletedSuccessfully: 'Posts deleted successfully',
    },

    // Bulk actions
    bulkActions: {
      bulkPin: 'Bulk Pin',
      bulkLock: 'Bulk Lock',
      bulkUnflag: 'Bulk Unflag',
      bulkDelete: 'Bulk Delete',
    },

    clearSelection: 'Clear Selection',

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
      bulkUnflaggedSuccessfully: 'Comments unflagged successfully',
      bulkDeletedSuccessfully: 'Comments deleted successfully',
      failedToBulkUnflag: 'Failed to unflag comments',
      failedToBulkDelete: 'Failed to delete comments',
    },

    // Bulk actions
    bulkActions: {
      bulkUnflag: 'Bulk Unflag',
      bulkDelete: 'Bulk Delete',
    },

    clearSelection: 'Clear Selection',
    deleteConfirm:
      'Are you sure you want to delete {count} comments? This action cannot be undone.',
  },

  // Auth
  auth: {
    // Login
    login: {
      title: 'Sign in',
      subtitle: 'Enter your credentials to access the admin panel',
      username: 'Username',
      usernamePlaceholder: 'admin',
      password: 'Password',
      passwordPlaceholder: '••••••••',
      submit: 'Sign in',
      submitting: 'Signing in...',
      invalidCredentials: 'Invalid username or password',
      loginFailed: 'Login failed. Please try again.',
      continueWithGithub: 'Continue with GitHub',
      demoAccounts: 'Demo Accounts',
      demoAccountsTitle: 'Use these credentials:',
      demoAdmin: '• admin / admin123 (Super Admin)',
      demoModerator: '• moderator / mod123 (Moderator)',
      rememberMe: 'Remember me',
      forgotPassword: 'Forgot password?',
      noAccount: "Don't have an account?",
      signup: 'Sign up',
    },

    // Signup
    signup: {
      title: 'Create your account',
      subtitle: 'Fill in the form below to create your account',
      fullName: 'Full Name',
      fullNamePlaceholder: 'John Doe',
      email: 'Email',
      emailPlaceholder: 'm@example.com',
      emailDescription:
        "We'll use this to contact you. We will not share your email with anyone else.",
      password: 'Password',
      passwordDescription: 'Must be at least 8 characters long.',
      confirmPassword: 'Confirm Password',
      confirmPasswordDescription: 'Please confirm your password.',
      submit: 'Create Account',
      orContinueWith: 'Or continue with',
      github: 'Sign up with GitHub',
      alreadyHaveAccount: 'Already have an account?',
      signIn: 'Sign in',
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

  // Error messages
  errors: {
    validation: {
      title: 'Validation Error',
      default: 'The submitted data is invalid',
      suggestion: 'Please check your input and try again',
    },
    unauthorized: {
      title: 'Unauthorized',
      message: 'You need to log in to perform this action',
      suggestion: 'Please log in and try again',
    },
    forbidden: {
      title: 'Access Denied',
      message: 'You do not have permission to perform this action',
      suggestion: 'Contact your administrator if you believe this is an error',
    },
    notFound: {
      title: 'Not Found',
      message: 'The resource was not found',
      suggestion: 'The resource may have been deleted or moved',
    },
    serverError: {
      title: 'Server Error',
      message: 'The server encountered an error',
      suggestion: 'Please try again later or contact support',
    },
    network: {
      title: 'Network Error',
      message: 'Unable to connect to the server',
      suggestion: 'Please check your internet connection',
    },
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
    emptyTitle: 'No results found',
    emptyDescription:
      "We couldn't find what you're looking for. Try adjusting your filters or search query.",
    searchPlaceholder: 'Search...',
    filterPlaceholder: 'Filter...',
    clearFilters: 'Clear Filters',
    showColumns: 'Show Columns',
    hideColumns: 'Hide Columns',
    resetColumns: 'Reset Columns',
    customizeColumns: 'Customize Columns',
    columns: 'Columns',
    selectAll: 'Select All',
    deselectAll: 'Deselect All',
    selected: '{count} selected',
    selected_one: '{count} selected',
    rowsPerPage: 'Rows per page',
    of: 'of',
    page: 'Page',
    rowsSelected: 'row(s) selected',
    goToFirstPage: 'Go to first page',
    goToPreviousPage: 'Go to previous page',
    goToNextPage: 'Go to next page',
    goToLastPage: 'Go to last page',
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

  // Account
  account: {
    title: 'My Account',
    subtitle: 'Manage your profile and preferences',
    sections: {
      basic: 'Basic Information',
      about: 'About',
      social: 'Social Links',
      preferences: 'Preferences',
      security: 'Security',
      accountInfo: 'Account Information',
    },
    fields: {
      name: 'Display Name',
      email: 'Email',
      avatar: 'Avatar URL',
      bio: 'Bio',
      company: 'Company',
      location: 'Location',
      github: 'GitHub',
      twitter: 'Twitter',
      website: 'Website',
      preferredLanguage: 'Preferred Language',
      role: 'Role',
      joinedAt: 'Joined At',
      lastLogin: 'Last Login',
      currentPassword: 'Current Password',
      newPassword: 'New Password',
      confirmPassword: 'Confirm New Password',
    },
    actions: {
      save: 'Save Changes',
      cancel: 'Cancel',
      changePassword: 'Change Password',
    },
    toast: {
      saveSuccess: 'Profile updated successfully',
      saveFailed: 'Failed to update profile',
      passwordSuccess: 'Password changed successfully',
      passwordFailed: 'Failed to change password',
      passwordsDoNotMatch: 'Passwords do not match',
    },
  },

  // Billing
  billing: {
    title: 'Billing & Subscription',
    subtitle: 'Manage your subscription and billing information',
    currentPlan: 'Current Plan',
    planDetails: 'Plan Details',
    statusLabel: 'Status',
    startedAt: 'Started At',
    expiresAt: 'Expires At',
    cancelledAt: 'Cancelled At',
    plans: {
      FREE: 'Free',
      PREMIUM_MONTHLY: 'Premium (Monthly)',
      PREMIUM_YEARLY: 'Premium (Yearly)',
    },
    status: {
      ACTIVE: 'Active',
      CANCELLED: 'Cancelled',
      EXPIRED: 'Expired',
      PENDING: 'Pending',
    },
    features: {
      free: {
        title: 'Free Plan',
        description: 'Basic access to platform features',
      },
      premium: {
        title: 'Premium Plan',
        description: 'Full access to all premium features',
      },
    },
    noSubscription: 'No active subscription',
  },

  // Submissions
  submissions: {
    title: 'Submissions',
    description: 'Manage and review code submissions',
    loadError: 'Failed to load submissions',
    loadDetailError: 'Failed to load submission details',

    // Statistics
    totalSubmissions: 'Total Submissions',
    last24h: '{count} in last 24h',
    pending: 'Pending',
    inQueue: 'In judge queue',
    topLanguage: 'Top Language',
    submissionsCount: '{count} submissions',
    acceptedRate: 'Accepted Rate',
    acceptedRateDesc: 'Percentage of accepted submissions',

    // Filters
    search: 'Search',
    searchPlaceholder: 'Search by username or problem...',
    status: 'Status',
    allStatuses: 'All Statuses',
    language: 'Language',
    allLanguages: 'All Languages',

    // Table
    id: 'ID',
    problem: 'Problem',
    user: 'User',
    runtime: 'Runtime',
    memory: 'Memory',
    submittedAt: 'Submitted',
    noSubmissions: 'No submissions found',

    // Detail
    detail: 'Submission Details',
    code: 'Code',
    notes: 'Notes',

    // Actions
    rejudge: 'Rejudge',
    rejudgeTitle: 'Rejudge Submission',
    rejudgeDescription: 'This will re-run the judge process for this submission.',
    rejudgeSuccess: 'Submission queued for rejudge',
    rejudgeError: 'Failed to rejudge: {error}',

    // Batch
    selectedCount: '{count} submissions selected',
    batchRejudge: 'Batch Rejudge',
    batchRejudgeTitle: 'Batch Rejudge Submissions',
    batchRejudgeDescription: 'You are about to rejudge {count} submissions.',
    batchRejudgeSuccess: 'Successfully queued {count} submissions for rejudge',
    batchRejudgePartial: '{success} queued, {failed} failed',
    batchRejudgeError: 'Failed to batch rejudge submissions',
  },
} as const
