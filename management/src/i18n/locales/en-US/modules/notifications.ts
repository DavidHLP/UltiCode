export default {
  title: 'System Notifications',
  searchPlaceholder: 'Search notifications...',
  newNotification: 'Send Notification',
  allTypes: 'All Types',
  clearSelection: 'Clear Selection',

  // Column definitions
  columns: {
    title: 'Title',
    type: 'Type',
    status: 'Status',
    createdAt: 'Created At',
    sentAt: 'Sent At',
    sentBy: 'Sent By',
    target: 'Target',
    category: 'Category',
    actions: 'Actions',
  },

  // Type labels
  types: {
    SYSTEM: 'System',
    CONTEST: 'Contest',
    SUBMISSION: 'Submission',
    COMMENT: 'Comment',
    REPLY: 'Reply',
    MENTION: 'Mention',
  },

  // Category labels
  categories: {
    SYSTEM: 'System',
    ANNOUNCEMENT: 'Announcement',
    PROMOTION: 'Promotion',
    UPDATE: 'Update',
    WARNING: 'Warning',
  },

  // Target labels
  targets: {
    ALL: 'All Users',
    USERS: 'Specific Users',
  },

  // Stats
  stats: {
    total: 'Total',
    system: 'System',
    contest: 'Contest',
    submission: 'Submission',
    other: 'Other',
  },

  sentAt: 'Sent At',
  sentBy: 'Sent By',

  // Delete dialog
  delete: {
    title: 'Delete Notification',
    description: 'Are you sure you want to delete this system notification?',
    confirm: 'Delete',
    cancel: 'Cancel',
  },

  deleteSuccess: 'Notification deleted successfully',
  deleteError: 'Failed to delete notification',

  // Dialog
  dialog: {
    createTitle: 'Create Notification',
    createDescription: 'Compose and send a new system notification to users.',
    editTitle: 'Edit Notification',
    editDescription: 'Update the title, content, and classification of this notification.',
    sending: 'Sending...',
    saving: 'Saving...',
    sendNotification: 'Send Notification',
    saveChanges: 'Save Changes',
  },

  // Form
  form: {
    title: 'Title',
    titlePlaceholder: 'Enter notification title',
    content: 'Content',
    contentPlaceholder: 'Enter notification content...',
    type: 'Type',
    targetType: 'Target Type',
    targetAll: 'All Users',
    targetUser: 'Specific Users',
    targetUserPlaceholder: 'Enter user IDs, separated by commas',
    targetTypePlaceholder: 'Select target user type',
    atLeastOneUserId: 'Please enter at least one user ID',

    // New form fields
    messageContent: 'Message Content',
    messageContentDescription: 'Enter the title and content of your notification.',
    notificationTitle: 'Notification Title',
    notificationTitlePlaceholder: 'Enter a concise title...',
    notificationContent: 'Notification Content',
    notificationContentPlaceholder: 'Enter the notification message...',
    classification: 'Classification',
    classificationDescription: 'Select the type and category of this notification.',
    selectType: 'Select type',
    category: 'Category',
    selectCategory: 'Select category',
    targetAudience: 'Target Audience',
    targetAudienceDescription: 'Choose who will receive this notification.',
    allUsers: 'All Users',
    specificUsers: 'Specific Users',
    userIds: 'User IDs',
    userIdsPlaceholder: 'Enter user IDs separated by commas (e.g., user1, user2, user3)',
  },

  // Toast messages
  toast: {
    createSuccess: 'Notification sent successfully',
    createFailed: 'Failed to send notification',
    sentSuccessfully: 'Notification sent successfully',
    failedToSend: 'Failed to send notification',
    updateSuccess: 'Notification updated successfully',
    updateFailed: 'Failed to update notification',
    deleteSuccess: 'Notification deleted successfully',
    deleteFailed: 'Failed to delete notification',
  },

  // Errors
  errors: {
    loadFailed: 'Failed to load notifications',
    notFound: 'Notification not found',
  },

  // Empty states
  empty: {
    title: 'No notifications found',
    description: 'Create a new notification to get started.',
  },
} as const
