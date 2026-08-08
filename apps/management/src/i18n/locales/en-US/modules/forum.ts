export default {
  title: 'Forum Posts',
  postsTitle: 'Forum Posts',
  detailTitle: 'Post Detail',
  searchPlaceholder: 'Search posts...',
  clearSelection: 'Clear Selection',
  stats: {
    total: 'Total',
    pinned: 'Pinned',
    locked: 'Locked',
    flagged: 'Flagged',
    postManagement: 'Post Management',
  },

  // Column definitions
  columns: {
    id: 'ID',
    title: 'Title',
    author: 'Author',
    community: 'Community',
    status: 'Status',
    replies: 'Replies',
    views: 'Views',
    createdAt: 'Created At',
    updatedAt: 'Updated At',
    stats: 'Stats',
    created: 'Created',
    actions: 'Actions',
  },

  // Status
  status: {
    all: 'All Status',
    ACTIVE: 'Active',
    active: 'Active',
    CLOSED: 'Closed',
    HIDDEN: 'Hidden',
    pinned: 'Pinned',
    locked: 'Locked',
    flagged: 'Flagged',
    deleted: 'Deleted',
  },

  // Actions
  actions: {
    view: 'View',
    viewDetails: 'View Details',
    edit: 'Edit',
    delete: 'Delete',
    pin: 'Pin',
    unpin: 'Unpin',
    close: 'Close',
    lock: 'Lock',
    unlock: 'Unlock',
    hide: 'Hide',
    flag: 'Flag',
    unflag: 'Unflag',
  },

  // Bulk actions
  bulkActions: {
    bulkPin: 'Bulk Pin',
    bulkLock: 'Bulk Lock',
    bulkUnflag: 'Bulk Unflag',
    bulkDelete: 'Bulk Delete',
  },

  // Filters
  filters: {
    all: 'All',
    community: 'Community',
    allCommunities: 'All Communities',
    flagStatus: 'Flag Status',
    flagged: 'Flagged',
    clean: 'Clean',
    pinned: 'Pinned',
    pinnedOnly: 'Pinned Only',
    unpinnedOnly: 'Unpinned Only',
    locked: 'Locked',
    lockedOnly: 'Locked Only',
    unlockedOnly: 'Unlocked Only',
    deleted: 'Deleted',
    deletedOnly: 'Deleted Only',
    activeOnly: 'Active Only',
  },

  // Tabs
  tabs: {
    overview: 'Overview',
    comments: 'Comments',
    audit: 'Audit History',
  },

  // Overview
  overview: {
    unknown: 'Unknown',
  },

  // Detail view
  detail: {
    content: 'Content',
    views: 'Views',
    comments: 'Comments',
    upvotes: 'Upvotes',
    downvotes: 'Downvotes',
    timeline: 'Timeline',
    created: 'Created',
    updated: 'Updated',
    flagInformation: 'Flag Information',
    reason: 'Reason',
    flaggedOn: 'Flagged on',
    deletionInformation: 'Deletion Information',
    deletedOn: 'Deleted on',
    noContentAvailable: 'No content available',
    identifiers: 'Identifiers',
    postId: 'Post ID',
    authorId: 'Author ID',
    communityId: 'Community ID',
  },

  // Drawer
  drawer: {
    title: 'Post Details',
    description: 'View post information',
    authorCommunity: 'Author & Community',
    unknownCommunity: 'Unknown Community',
    contentPreview: 'Content Preview',
    postNotFound: 'Post not found',
  },

  // Comments tab
  comments: {
    postComments: 'Post Comments',
    noCommentsFound: 'No comments found for this post',
  },

  // Audit
  audit: {
    description: 'Moderation actions and changes to this post',
    noAuditHistory: 'No audit history available',
    from: 'From',
    to: 'To',
    performed: 'performed',
  },

  // Audit actions
  auditActions: {
    PIN_FORUM_POST: 'Pin Post',
    UNPIN_FORUM_POST: 'Unpin Post',
    LOCK_FORUM_POST: 'Lock Post',
    UNLOCK_FORUM_POST: 'Unlock Post',
    DELETE_FORUM_POST: 'Delete Post',
    FLAG_FORUM_POST: 'Flag Post',
    UNFLAG_FORUM_POST: 'Unflag Post',
    BULK_DELETE_FORUM: 'Bulk Delete',
    BULK_PIN_FORUM: 'Bulk Pin',
  },

  // Delete dialog
  delete: {
    title: 'Delete Post',
    description: 'Are you sure you want to delete this post? This action cannot be undone.',
    confirm: 'Delete',
    cancel: 'Cancel',
  },

  // Flag dialog
  flag: {
    title: 'Flag Post',
    description: 'Please provide a reason for flagging this post.',
    confirm: 'Flag',
    cancel: 'Cancel',
    reasonLabel: 'Reason',
    reasonPlaceholder: 'Enter the reason for flagging...',
  },

  // Error messages
  error: {
    loadingPost: 'Failed to load post',
    postNotFound: 'Post not found',
    notFoundDescription: 'The post you are looking for does not exist or has been deleted.',
    back: 'Back',
    retry: 'Retry',
    backToForumPosts: 'Back to Forum Posts',
  },

  // Confirmation messages
  deleteConfirm: 'Are you sure you want to delete {count} posts?',

  // Toast messages
  toast: {
    loadFailed: 'Failed to load posts',
    deleteSuccess: 'Post deleted successfully',
    deleteFailed: 'Failed to delete post',
    deletedSuccessfully: 'Post deleted successfully',
    failedToDelete: 'Failed to delete post',
    pinnedSuccessfully: 'Post pinned successfully',
    unpinnedSuccessfully: 'Post unpinned successfully',
    failedToUpdatePin: 'Failed to update pin status',
    lockedSuccessfully: 'Post locked successfully',
    unlockedSuccessfully: 'Post unlocked successfully',
    failedToUpdateLock: 'Failed to update lock status',
    flaggedSuccessfully: 'Post flagged successfully',
    failedToFlag: 'Failed to flag post',
    unflaggedSuccessfully: 'Post unflagged successfully',
    failedToUnflag: 'Failed to unflag post',
    bulkPinnedSuccessfully: 'Posts pinned successfully',
    bulkLockedSuccessfully: 'Posts locked successfully',
    bulkUnflaggedSuccessfully: 'Posts unflagged successfully',
    bulkDeletedSuccessfully: 'Posts deleted successfully',
    reasonRequired: 'Reason is required',
  },
} as const
