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

    // Form
    form: {
      title: 'Title',
      slug: 'Slug',
      difficulty: 'Difficulty',
      timeLimit: 'Time Limit (ms)',
      memoryLimit: 'Memory Limit (MB)',
      isPublic: 'Public',
      isPublished: 'Published',
      description: 'Description',
      inputFormat: 'Input Format',
      outputFormat: 'Output Format',
      constraints: 'Constraints',
      hint: 'Hint',
      tags: 'Tags',
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
  },

  // Contests
  contests: {
    title: 'Contests',
    listTitle: 'Contest Management',
    addContest: 'Add Contest',
    searchPlaceholder: 'Search contests...',

    // Filters
    filters: {
      allStatus: 'All Status',
      upcoming: 'Upcoming',
      running: 'Running',
      finished: 'Finished',
    },

    // Table columns
    columns: {
      id: 'ID',
      name: 'Name',
      startTime: 'Start Time',
      duration: 'Duration',
      participants: 'Participants',
      problems: 'Problems',
      status: 'Status',
    },

    // Status
    status: {
      upcoming: 'Upcoming',
      running: 'Running',
      finished: 'Finished',
    },

    // Wizard steps
    wizard: {
      step1: 'Basic Info',
      step2: 'Problems',
      step3: 'Schedule',
      step4: 'Review',
      next: 'Next',
      previous: 'Previous',
      submit: 'Create Contest',
    },

    // Form
    form: {
      name: 'Contest Name',
      description: 'Description',
      startTime: 'Start Time',
      endTime: 'End Time',
      duration: 'Duration (minutes)',
      isPublic: 'Public',
      rules: 'Rules',
    },

    // Problem picker
    problemPicker: {
      addProblems: 'Add Problems',
      selectedProblems: 'Selected Problems',
      availableProblems: 'Available Problems',
      remove: 'Remove',
      points: 'Points',
    },

    // Review
    review: {
      reviewContest: 'Review Contest',
      contestSummary: 'Contest Summary',
      problemsSummary: 'Problems',
      scheduleSummary: 'Schedule',
    },
  },

  // Solutions
  solutions: {
    title: 'Solutions',
    listTitle: 'Solution Management',
    searchPlaceholder: 'Search solutions...',

    // Table columns
    columns: {
      problem: 'Problem',
      author: 'Author',
      language: 'Language',
      status: 'Status',
      flags: 'Flags',
      createdAt: 'Created',
    },

    // Actions
    actions: {
      view: 'View',
      flag: 'Flag',
      unflag: 'Unflag',
      delete: 'Delete',
    },

    // Status
    status: {
      flagged: 'Flagged',
      approved: 'Approved',
      pending: 'Pending',
    },

    // Tabs
    tabs: {
      code: 'Code',
      description: 'Description',
    },

    // Form
    form: {
      flagReason: 'Flag Reason',
      notes: 'Admin Notes',
    },
  },

  // Forum
  forum: {
    title: 'Forum',
    postsTitle: 'Forum Posts',
    commentsTitle: 'Forum Comments',
    searchPlaceholder: 'Search posts...',

    // Table columns
    columns: {
      title: 'Title',
      author: 'Author',
      category: 'Category',
      replies: 'Replies',
      views: 'Views',
      status: 'Status',
      createdAt: 'Created',
    },

    // Actions
    actions: {
      view: 'View',
      flag: 'Flag',
      unflag: 'Unflag',
      delete: 'Delete',
      lock: 'Lock',
      unlock: 'Unlock',
      pin: 'Pin',
      unpin: 'Unpin',
    },

    // Status
    status: {
      flagged: 'Flagged',
      locked: 'Locked',
      pinned: 'Pinned',
    },

    // Tabs
    tabs: {
      overview: 'Overview',
      comments: 'Comments',
      audit: 'Audit Log',
    },

    // Form
    form: {
      flagReason: 'Flag Reason',
      moderationNotes: 'Moderation Notes',
    },
  },

  // Comments
  comments: {
    title: 'Comments',
    listTitle: 'Comment Management',
    searchPlaceholder: 'Search comments...',

    // Table columns
    columns: {
      content: 'Content',
      author: 'Author',
      type: 'Type',
      target: 'Target',
      status: 'Status',
      createdAt: 'Created',
    },

    // Actions
    actions: {
      view: 'View',
      flag: 'Flag',
      unflag: 'Unflag',
      delete: 'Delete',
    },

    // Types
    type: {
      forumPost: 'Forum Post',
      forumComment: 'Forum Comment',
      solutionComment: 'Solution Comment',
    },

    // Status
    status: {
      flagged: 'Flagged',
      visible: 'Visible',
      hidden: 'Hidden',
    },

    // Form
    form: {
      flagReason: 'Flag Reason',
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
