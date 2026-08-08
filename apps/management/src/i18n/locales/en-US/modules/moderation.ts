export default {
  title: 'Moderation Queue',
  description: 'Review and manage flagged content',
  searchPlaceholder: 'Search...',

  // ========== Report Categories (9 types) ==========
  categories: {
    title: 'Category',
    all: 'All Categories',
    SPAM: 'Spam',
    HARASSMENT: 'Harassment',
    HATE_SPEECH: 'Hate Speech',
    VIOLENCE: 'Violence',
    SEXUAL_CONTENT: 'Sexual Content',
    MISINFORMATION: 'Misinformation',
    WRONG_ANSWER: 'Wrong Answer',
    COPYRIGHT: 'Copyright',
    OTHER: 'Other',
  },

  // Category descriptions
  categoryDescriptions: {
    SPAM: 'Unsolicited promotional content or repeated posting',
    HARASSMENT: 'Targeted harassment or bullying of individuals',
    HATE_SPEECH: 'Content that promotes hate or discrimination',
    VIOLENCE: 'Content that depicts or promotes violence',
    SEXUAL_CONTENT: 'Sexually explicit or inappropriate content',
    MISINFORMATION: 'False or misleading information',
    WRONG_ANSWER: 'Solution contains incorrect code or logic',
    COPYRIGHT: 'Copyright infringement or plagiarism',
    OTHER: 'Other violations not covered by specific categories',
  },

  // ========== Moderation Status (5 types) ==========
  status: {
    title: 'Status',
    all: 'All Statuses',
    PENDING: 'Pending',
    UNDER_REVIEW: 'Under Review',
    RESOLVED: 'Resolved',
    DISMISSED: 'Dismissed',
    APPEAL_PENDING: 'Appeal Pending',
  },

  // Status descriptions
  statusDescriptions: {
    PENDING: 'Awaiting moderator review',
    UNDER_REVIEW: 'Currently being reviewed by a moderator',
    RESOLVED: 'Issue has been addressed',
    DISMISSED: 'Report was found to be invalid',
    APPEAL_PENDING: 'User has filed an appeal',
  },

  // ========== Moderation Actions (11 types) ==========
  actions: {
    title: 'Actions',
    DELETED: 'Delete Content',
    HIDDEN: 'Hide Content',
    RESTORED: 'Restore Content',
    WARNED: 'Issue Warning',
    TEMP_BANNED: 'Temporary Ban',
    PERM_BANNED: 'Permanent Ban',
    DISMISSED: 'Dismiss Report',
    RESOLVED: 'Mark Resolved',
    APPEAL_PENDING: 'Appeal Pending',
    APPEAL_APPROVED: 'Approve Appeal',
    APPEAL_REJECTED: 'Reject Appeal',
  },

  // Action descriptions
  actionDescriptions: {
    DELETED: 'Permanently delete the reported content',
    HIDDEN: 'Hide the content from public view',
    RESTORED: 'Restore previously hidden content',
    WARNED: 'Send a warning to the user',
    TEMP_BANNED: 'Temporarily ban the user',
    PERM_BANNED: 'Permanently ban the user',
    DISMISSED: 'Dismiss the report as invalid',
    RESOLVED: 'Mark the issue as resolved',
    APPEAL_PENDING: 'User has submitted an appeal',
    APPEAL_APPROVED: "Approve the user's appeal",
    APPEAL_REJECTED: "Reject the user's appeal",
  },

  // ========== Entity Types (5 types) ==========
  entityTypes: {
    title: 'Entity Type',
    all: 'All Types',
    forum_post: 'Forum Post',
    forum_comment: 'Forum Comment',
    solution: 'Solution',
    solution_comment: 'Solution Comment',
    problem: 'Problem',
  },

  // ========== Appeal Status (4 types) ==========
  appealStatus: {
    title: 'Appeal Status',
    all: 'All Appeals',
    PENDING: 'Pending',
    UNDER_REVIEW: 'Under Review',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
  },

  // ========== Report Status (4 types) ==========
  reportStatus: {
    title: 'Report Status',
    all: 'All Reports',
    PENDING: 'Pending',
    REVIEWED: 'Reviewed',
    RESOLVED: 'Resolved',
    DISMISSED: 'Dismissed',
  },

  // ========== Queue View ==========
  queue: {
    pageTitle: 'Content Moderation',
    title: 'Moderation Queue',
    description: 'Review and manage reported content',
    emptyTitle: 'Queue is Empty',
    emptyDescription: 'No items currently require moderation.',
    claimItem: 'Claim Item',
    assignTo: 'Assign To',
    unassign: 'Unassign',
    performAction: 'Perform Action',
    batchActions: 'Batch Actions',
    selectedCount: '{count} items selected',
    priority: 'Priority',
    reportCount: 'Reports',
    assignedTo: 'Assigned To',
    unassigned: 'Unassigned',
    claimedBy: 'Claimed by {name}',
    viewDetails: 'View Details',
    viewEntity: 'View Content',
    viewReports: 'View Reports ({count})',
  },

  // ========== Statistics Dashboard ==========
  stats: {
    title: 'Statistics',
    overview: 'Overview',
    totalPending: 'Pending',
    totalUnderReview: 'Under Review',
    totalResolved: 'Resolved',
    totalDismissed: 'Dismissed',
    totalAppealPending: 'Appeals Pending',
    avgResolutionTime: 'Avg. Resolution Time',
    hours: 'hours',
    byCategory: 'By Category',
    byEntityType: 'By Entity Type',
    recentActivity: 'Recent Activity',
    noData: 'No data available',
  },

  // ========== Detail View ==========
  detail: {
    title: 'Moderation Details',
    entityInfo: 'Entity Information',
    entityPreview: 'Content Preview',
    reportsTitle: 'Reports ({count})',
    actionsTitle: 'Action History ({count})',
    appealTitle: 'Appeal Details',
    noReports: 'No reports found',
    noActions: 'No actions taken',
    reporter: 'Reporter',
    reportedAt: 'Reported At',
    reason: 'Reason',
    evidence: 'Evidence',
    performedBy: 'Performed By',
    performedAt: 'Performed At',
    note: 'Note',
    duration: 'Duration',
    days: '{count} days',
    moreReports: '{count} more reports',
  },

  // ========== Action Panel ==========
  actionPanel: {
    title: 'Take Action',
    selectAction: 'Select Action',
    addNote: 'Add Note (Optional)',
    notePlaceholder: 'Enter a note explaining your decision...',
    durationLabel: 'Ban Duration (Days)',
    durationPlaceholder: 'Enter number of days...',
    confirmAction: 'Confirm Action',
    confirming: 'Processing...',
    warning: 'This action will be logged and cannot be undone.',
    days: 'days',
  },

  // ========== Appeals View ==========
  appeals: {
    pageTitle: 'Appeals',
    title: 'Appeals',
    description: 'Review user appeals against moderation decisions',
    emptyTitle: 'No Appeals',
    emptyDescription: 'No appeals are currently pending.',
    appellant: 'Appellant',
    reason: 'Appeal Reason',
    evidence: 'Evidence',
    submittedAt: 'Submitted',
    reviewedBy: 'Reviewed By',
    reviewedAt: 'Reviewed At',
    response: 'Response',
    approveAppeal: 'Approve Appeal',
    rejectAppeal: 'Reject Appeal',
    reviewAppeal: 'Review Appeal',
    responsePlaceholder: 'Enter your response to this appeal...',
    decision: 'Decision',
    reviewDescription: 'Review the appeal and provide your decision.',
  },

  // ========== Reports View ==========
  reports: {
    pageTitle: 'Reports',
    title: 'Reports',
    description: 'View all content reports',
    emptyTitle: 'No Reports',
    emptyDescription: 'No reports have been submitted.',
    reporter: 'Reporter',
    entity: 'Entity',
    entityType: 'Type',
    category: 'Category',
    reason: 'Reason',
    evidence: 'Evidence',
    submittedAt: 'Submitted',
    status: 'Status',
    viewEntity: 'View Entity',
    viewQueue: 'View in Queue',
    noQueueItem: 'No queue item associated with this report',
  },

  // ========== Filters ==========
  filters: {
    title: 'Filters',
    clearAll: 'Clear All',
    status: 'Status',
    category: 'Category',
    entityType: 'Entity Type',
    assignedTo: 'Assigned To',
    minPriority: 'Min Priority',
    dateRange: 'Date Range',
    from: 'From',
    to: 'To',
    apply: 'Apply Filters',
    activeFilters: 'Active Filters',
  },

  // ========== Dialogs ==========
  dialogs: {
    confirmTitle: 'Confirm Action',
    confirmMessage: 'Are you sure you want to {action}?',
    confirmBatchTitle: 'Confirm Batch Action',
    confirmBatchMessage: 'Are you sure you want to {action} on {count} items?',
    cancel: 'Cancel',
    confirm: 'Confirm',
    close: 'Close',
  },

  // ========== Toast Messages ==========
  toast: {
    success: 'Action completed successfully',
    error: 'An error occurred',
    claimed: 'Item claimed successfully',
    assigned: 'Item assigned successfully',
    unassigned: 'Item unassigned successfully',
    actionCompleted: 'Action completed successfully',
    batchCompleted: 'Batch action completed',
    appealApproved: 'Appeal approved',
    appealRejected: 'Appeal rejected',
    loadError: 'Failed to load data',
    networkError: 'Network error. Please try again.',
  },

  // ========== Empty States ==========
  empty: {
    title: 'No Results',
    description: 'No items match your current filters.',
    clearFilters: 'Clear Filters',
  },

  // ========== Column Definitions ==========
  columns: {
    entity: 'Entity',
    entityType: 'Type',
    title: 'Title',
    category: 'Category',
    status: 'Status',
    priority: 'Priority',
    reports: 'Reports',
    assignedTo: 'Assigned To',
    createdAt: 'Created',
    updatedAt: 'Updated',
    actions: 'Actions',
    reporter: 'Reporter',
    reason: 'Reason',
    resolution: 'Action',
    queueId: 'Queue ID',
    id: 'ID',
  },

  // ========== Terminal Style ==========
  terminal: {
    loading: 'Loading...',
    selected: 'Selected',
    total: 'Total',
    pending: 'Pending',
    reviewed: 'Reviewed',
    resolved: 'Resolved',
    dismissed: 'Dismissed',
    underReview: 'Under Review',
  },

  // ========== Time Related ==========
  time: {
    justNow: 'just now',
    minutesAgo: '{count}m ago',
    hoursAgo: '{count}h ago',
    daysAgo: '{count}d ago',
  },

  // ========== Priority ==========
  priority: {
    critical: 'Critical',
    high: 'High',
    medium: 'Medium',
    low: 'Low',
  },

  // ========== Legacy (for backward compatibility) ==========
  filterStatus: 'Filter by Status',
  allStatuses: 'All Statuses',
  statusPending: 'Pending',
  statusReviewed: 'Reviewed',
  statusResolved: 'Resolved',
  statusDismissed: 'Dismissed',
  noFlagged: 'No Flagged Content',
  noFlaggedDescription: 'There is currently no content requiring review.',
  flagDescription: 'Flag "{title}" for review. Please provide the reason for flagging.',
  flagReason: 'Flag Reason',
  moderationNotes: 'Moderation Notes',
  unknownReporter: 'Unknown',
  moderate: 'Moderate',
  flag: 'Flag',
  unflag: 'Unflag',
  quickResolve: 'Quick Resolve',
  quickDismiss: 'Quick Dismiss',
  flagProblem: 'Flag Problem',
  drawerTitle: 'Moderation Details',
  moderationActions: 'Moderation Actions',
  success: 'Moderated successfully',
  error: 'Failed to moderate',
  loadError: 'Failed to load',
  selectAll: 'Select All',
  selectedCount: '{count} selected',
  batchResolve: 'Batch Resolve',
  batchDismiss: 'Batch Dismiss',
  notFound: 'Moderation item not found',
  flagSuccess: 'Flagged successfully',
  flagError: 'Failed to flag',
  unflagSuccess: 'Unflagged successfully',
  unflagError: 'Failed to unflag',
} as const
